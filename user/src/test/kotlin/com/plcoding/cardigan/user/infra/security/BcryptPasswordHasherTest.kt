package com.plcoding.cardigan.user.infra.security

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import assertk.assertions.matches
import assertk.assertions.prop
import com.plcoding.cardigan.common.domain.text.normalizePassword
import com.plcoding.cardigan.user.domain.exception.PasswordHashingBusyException
import com.plcoding.cardigan.user.infra.config.PasswordHashingProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BcryptPasswordHasherTest {

    private val hasher = BcryptPasswordHasher(PasswordHashingProperties(bcryptCost = 4, maxConcurrentHashes = 2))
    private val longWait = Duration.ofSeconds(30)

    @Test
    fun `hash is BCrypt with the configured cost and without the password`() {
        val hash = BcryptPasswordHasher(PasswordHashingProperties(bcryptCost = 5, maxConcurrentHashes = 1))
            .hash("Correct horse 7")

        assertThat(hash).matches(Regex("""^\$2[aby]\$05\$[./A-Za-z0-9]{53}$"""))
        assertThat(hash).doesNotContain("Correct horse 7")
    }

    @Test
    fun `hash matches its password`() {
        assertThat(hasher.matches("Correct horse 7", hasher.hash("Correct horse 7"))).isTrue()
    }

    @Test
    fun `two hashes of one password differ and both match`() {
        val first = hasher.hash("Correct horse 7")
        val second = hasher.hash("Correct horse 7")

        assertThat(first).isNotEqualTo(second)
        assertThat(hasher.matches("Correct horse 7", first)).isTrue()
        assertThat(hasher.matches("Correct horse 7", second)).isTrue()
    }

    @Test
    fun `128-character passwords that differ in the last character don't match`() {
        val password = "Aa1" + "x".repeat(124) + "y"
        val other = "Aa1" + "x".repeat(124) + "z"

        assertThat(hasher.matches(other, hasher.hash(password))).isFalse()
    }

    @Test
    fun `passwords that differ only after byte 72 don't match`() {
        val password = "a".repeat(72) + "b"
        val other = "a".repeat(72) + "c"

        assertThat(hasher.matches(other, hasher.hash(password))).isFalse()
    }

    @Test
    fun `400-byte password of 4-byte characters hashes and matches`() {
        val password = "😀".repeat(100)
        assertThat(password.toByteArray(Charsets.UTF_8).size).isEqualTo(400)

        val hash = hasher.hash(password)

        assertThat(hasher.matches(password, hash)).isTrue()
        assertThat(hasher.matches("😀".repeat(99) + "😁", hash)).isFalse()
    }

    @Test
    fun `surrounding whitespace is part of the password`() {
        val hash = hasher.hash("Correct horse 7")

        assertThat(hasher.matches(" Correct horse 7", hash)).isFalse()
        assertThat(hasher.matches("Correct horse 7 ", hash)).isFalse()
    }

    @Test
    fun `letter case is part of the password`() {
        assertThat(hasher.matches("correct horse 7", hasher.hash("Correct horse 7"))).isFalse()
    }

    @Test
    fun `composed and decomposed forms match once both are normalized`() {
        val hash = hasher.hash(normalizePassword("Café password 1"))

        assertThat(hasher.matches(normalizePassword("Café password 1"), hash)).isTrue()
        assertThat(hasher.matches("Café password 1", hash)).isFalse()
    }

    @Test
    fun `empty password hashes and matches only itself`() {
        val hash = hasher.hash("")

        assertThat(hasher.matches("", hash)).isTrue()
        assertThat(hasher.matches(" ", hash)).isFalse()
    }

    @Test
    fun `null hash never matches`() {
        assertThat(hasher.matches("Correct horse 7", null)).isFalse()
        assertThat(hasher.matches("", null)).isFalse()
    }

    @Test
    fun `null hash does one verification against a real hash`() {
        val encoder = RecordingEncoder()
        val recordingHasher = BcryptPasswordHasher(encoder, 1, longWait)
        val verificationsBefore = encoder.verifiedHashes.size

        recordingHasher.matches("Correct horse 7", null)

        assertThat(encoder.verifiedHashes.size - verificationsBefore).isEqualTo(1)
        assertThat(encoder.verifiedHashes.last()).isEqualTo(encoder.encodedHashes.single())
    }

    @Test
    fun `a match against the dummy hash still returns false`() {
        val recordingHasher = BcryptPasswordHasher(RecordingEncoder(alwaysMatches = true), 1, longWait)

        assertThat(recordingHasher.matches("anything", null)).isFalse()
    }

    @Test
    fun `an encoder that returns no hash fails instead of storing null`() {
        val nullEncoder = object : PasswordEncoder {
            override fun encode(rawPassword: CharSequence?): String? = null

            override fun matches(rawPassword: CharSequence?, encodedPassword: String?): Boolean = false
        }

        assertFailure { BcryptPasswordHasher(nullEncoder, 1, longWait) }.isInstanceOf(IllegalStateException::class)
    }

    @Test
    fun `stored value that is no BCrypt hash doesn't match`() {
        assertThat(hasher.matches("Correct horse 7", "not-a-real-hash")).isFalse()
    }

    @Test
    fun `no more than the configured number of hashes run at once`() {
        val maxConcurrent = 2
        val encoder = BlockingEncoder()
        val blockingHasher = BcryptPasswordHasher(encoder, maxConcurrent, longWait)
        encoder.block()
        val executor = Executors.newFixedThreadPool(8)
        try {
            val futures = (1..8).map { i ->
                executor.submit<Any> { if (i % 2 == 0) blockingHasher.hash("p$i") else blockingHasher.matches("p$i", "h") }
            }
            encoder.awaitRunning(maxConcurrent)
            Thread.sleep(200)
            assertThat(encoder.running.get()).isEqualTo(maxConcurrent)

            encoder.release()
            futures.forEach { it.get(10, TimeUnit.SECONDS) }

            assertThat(encoder.maxRunning.get()).isEqualTo(maxConcurrent)
            assertThat(encoder.calls.get()).isGreaterThan(8)
        } finally {
            encoder.release()
            executor.shutdownNow()
        }
    }

    @Test
    fun `a request that gets no permit in time fails as rate limited and never hashes`() {
        val encoder = BlockingEncoder()
        val busyHasher = BcryptPasswordHasher(encoder, 1, Duration.ofMillis(100))
        encoder.block()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val running = executor.submit<Any> { busyHasher.hash("first") }
            encoder.awaitRunning(1)
            val callsBefore = encoder.calls.get()

            assertFailure { busyHasher.hash("second") }.isInstanceOf(PasswordHashingBusyException::class)
                .prop(PasswordHashingBusyException::retryAfterSeconds).isEqualTo(1)
            assertFailure { busyHasher.matches("second", null) }.isInstanceOf(PasswordHashingBusyException::class)

            assertThat(encoder.calls.get()).isEqualTo(callsBefore)
            encoder.release()
            running.get(10, TimeUnit.SECONDS)
        } finally {
            encoder.release()
            executor.shutdownNow()
        }
    }

    @Test
    fun `a timed out request doesn't use up a permit`() {
        val encoder = BlockingEncoder()
        val busyHasher = BcryptPasswordHasher(encoder, 1, Duration.ofMillis(100))
        encoder.block()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val running = executor.submit<Any> { busyHasher.hash("first") }
            encoder.awaitRunning(1)
            assertFailure { busyHasher.hash("second") }.isInstanceOf(PasswordHashingBusyException::class)
            encoder.release()
            running.get(10, TimeUnit.SECONDS)

            assertThat(busyHasher.hash("third")).isEqualTo("hash")
        } finally {
            encoder.release()
            executor.shutdownNow()
        }
    }

    @ParameterizedTest
    @CsvSource("1, 1", "999, 1", "1000, 1", "1001, 2", "5000, 5")
    fun `Retry-After is the wait timeout rounded up to whole seconds`(timeoutMillis: Long, expectedSeconds: Long) {
        assertThat(retryAfterSeconds(Duration.ofMillis(timeoutMillis))).isEqualTo(expectedSeconds)
    }

    private class RecordingEncoder(private val alwaysMatches: Boolean = false) : PasswordEncoder {
        val encodedHashes = CopyOnWriteArrayList<String>()
        val verifiedHashes = CopyOnWriteArrayList<String>()

        override fun encode(rawPassword: CharSequence?): String = "hash-of-$rawPassword".also { encodedHashes += it }

        override fun matches(rawPassword: CharSequence?, encodedPassword: String?): Boolean {
            verifiedHashes += encodedPassword.orEmpty()
            return alwaysMatches
        }
    }

    private class BlockingEncoder : PasswordEncoder {
        val running = AtomicInteger()
        val maxRunning = AtomicInteger()
        val calls = AtomicInteger()

        @Volatile
        private var gate = CountDownLatch(0)

        fun block() {
            gate = CountDownLatch(1)
        }

        fun release() = gate.countDown()

        fun awaitRunning(count: Int) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (running.get() < count && System.nanoTime() < deadline) Thread.sleep(5)
        }

        private fun work() {
            calls.incrementAndGet()
            val now = running.incrementAndGet()
            maxRunning.accumulateAndGet(now, ::maxOf)
            try {
                gate.await(10, TimeUnit.SECONDS)
            } finally {
                running.decrementAndGet()
            }
        }

        override fun encode(rawPassword: CharSequence?): String {
            work()
            return "hash"
        }

        override fun matches(rawPassword: CharSequence?, encodedPassword: String?): Boolean {
            work()
            return false
        }
    }
}
