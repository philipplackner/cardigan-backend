package com.plcoding.cardigan.user.infra.security

import com.plcoding.cardigan.user.domain.exception.PasswordHashingBusyException
import com.plcoding.cardigan.user.domain.repository.PasswordHasher
import com.plcoding.cardigan.user.infra.config.PasswordHashingProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * BCrypt over `Base64(SHA-256(password))`. BCrypt reads only 72 bytes, so the pre-hash makes every
 * character of a longer password count. At most `max-concurrent-hashes` computations run at once,
 * so hashing never takes every carrier thread. A request that waits longer than `hash-wait-timeout`
 * for its turn fails with [PasswordHashingBusyException], so a flood can't queue without bound.
 */
@Component
class BcryptPasswordHasher internal constructor(
    private val encoder: PasswordEncoder,
    maxConcurrentHashes: Int,
    private val waitTimeout: Duration,
) : PasswordHasher {

    @Autowired
    constructor(properties: PasswordHashingProperties) : this(
        BCryptPasswordEncoder(properties.bcryptCost),
        properties.maxConcurrentHashes,
        properties.hashWaitTimeout,
    )

    private val permits = Semaphore(maxConcurrentHashes, true)

    private val retryAfterSeconds = retryAfterSeconds(waitTimeout)

    private val dummyHash: String = encode(randomPassword())

    override fun hash(password: String): String = limited { encode(password) }

    override fun matches(password: String, hash: String?): Boolean = limited {
        val matches = encoder.matches(preHash(password), hash ?: dummyHash)
        matches && hash != null
    }

    private fun encode(password: String): String = checkNotNull(encoder.encode(preHash(password)))

    private fun preHash(password: String): String =
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8)))

    private fun randomPassword(): String =
        Base64.getEncoder().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })

    private inline fun <T> limited(block: () -> T): T {
        if (!permits.tryAcquire(waitTimeout.toNanos(), TimeUnit.NANOSECONDS)) {
            throw PasswordHashingBusyException(retryAfterSeconds)
        }
        try {
            return block()
        } finally {
            permits.release()
        }
    }
}

internal fun retryAfterSeconds(waitTimeout: Duration): Long =
    waitTimeout.plusNanos(999_999_999).toSeconds().coerceAtLeast(1)
