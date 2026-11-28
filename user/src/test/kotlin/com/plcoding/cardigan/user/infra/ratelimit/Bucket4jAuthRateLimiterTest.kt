package com.plcoding.cardigan.user.infra.ratelimit

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThanOrEqualTo
import com.plcoding.cardigan.common.MutableClock
import com.plcoding.cardigan.common.domain.exception.RateLimitedException
import com.plcoding.cardigan.user.domain.exception.AuthRateLimitedException
import com.plcoding.cardigan.user.domain.model.AuthAction
import com.plcoding.cardigan.user.domain.model.AuthAction.LOGIN
import com.plcoding.cardigan.user.domain.model.AuthAction.REGISTER
import com.plcoding.cardigan.user.infra.config.AuthRateLimitProperties
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class Bucket4jAuthRateLimiterTest {
    private val clock = MutableClock()
    private val properties = AuthRateLimitProperties()
    private val limiter = Bucket4jAuthRateLimiter(properties, clock)

    private val ann = "ann@example.com"
    private val bob = "bob@example.com"

    private fun isAllowed(
        action: AuthAction = LOGIN,
        email: String = ann,
        limiter: Bucket4jAuthRateLimiter = this.limiter,
    ) = try {
        limiter.check(action, email)
        true
    } catch (_: AuthRateLimitedException) {
        false
    }

    private fun useUp(
        action: AuthAction = LOGIN,
        email: String = ann,
        limiter: Bucket4jAuthRateLimiter = this.limiter,
    ) {
        repeat(5) { limiter.check(action, email) }
    }

    private fun retryAfter(
        action: AuthAction = LOGIN,
        email: String = ann,
    ): Long =
        try {
            limiter.check(action, email)
            error("The request was not limited")
        } catch (e: AuthRateLimitedException) {
            e.retryAfterSeconds
        }

    @Test
    fun `five requests pass and the sixth fails`() {
        val results = (1..6).map { isAllowed() }

        assertThat(results).isEqualTo(listOf(true, true, true, true, true, false))
    }

    @Test
    fun `sixth register request fails`() {
        val results = (1..6).map { isAllowed(REGISTER) }

        assertThat(results).isEqualTo(listOf(true, true, true, true, true, false))
    }

    @Test
    fun `failure is the shared rate limit exception`() {
        useUp()

        assertFailure { limiter.check(LOGIN, ann) }.isInstanceOf<RateLimitedException>()
    }

    @Test
    fun `still limited 11_999 seconds later, and exactly one request passes after 12 seconds`() {
        useUp()

        clock.advanceBy(Duration.ofMillis(11_999))
        assertThat(isAllowed()).isEqualTo(false)

        clock.advanceBy(Duration.ofMillis(1))
        assertThat(isAllowed()).isEqualTo(true)
        assertThat(isAllowed()).isEqualTo(false)
    }

    @Test
    fun `rejected requests don't push the refill back`() {
        useUp()

        repeat(20) {
            clock.advanceBy(Duration.ofMillis(500))
            isAllowed()
        }
        clock.advanceBy(Duration.ofSeconds(2))

        assertThat(isAllowed()).isEqualTo(true)
    }

    @Test
    fun `a full minute refills five requests and never more`() {
        useUp()

        clock.advanceBy(Duration.ofMinutes(1))
        assertThat((1..6).map { isAllowed() }).isEqualTo(listOf(true, true, true, true, true, false))

        clock.advanceBy(Duration.ofSeconds(59))
        assertThat((1..5).map { isAllowed() }).isEqualTo(listOf(true, true, true, true, false))
    }

    @Test
    fun `login and register count separately`() {
        useUp(LOGIN)

        assertThat(isAllowed(REGISTER)).isEqualTo(true)
        assertThat(isAllowed(LOGIN)).isEqualTo(false)
    }

    @Test
    fun `emails count separately`() {
        useUp(email = ann)

        assertThat(isAllowed(email = bob)).isEqualTo(true)
        assertThat(isAllowed(email = "ann@example.co")).isEqualTo(true)
        assertThat(isAllowed(email = ann)).isEqualTo(false)
    }

    @Test
    fun `Retry-After is 12, then 7 after 5 seconds, then 1 after 11_5 seconds`() {
        useUp()

        assertThat(retryAfter()).isEqualTo(12)

        clock.advanceBy(Duration.ofSeconds(5))
        assertThat(retryAfter()).isEqualTo(7)

        clock.advanceBy(Duration.ofMillis(6_500))
        assertThat(retryAfter()).isEqualTo(1)
    }

    @Test
    fun `Retry-After is never 0`() {
        useUp()

        clock.advanceBy(Duration.ofMillis(11_999))

        assertThat(retryAfter()).isEqualTo(1)
    }

    @Test
    fun `waiting for Retry-After is enough`() {
        useUp()
        clock.advanceBy(Duration.ofMillis(3_300))

        val seconds = retryAfter()
        clock.advanceBy(Duration.ofSeconds(seconds))

        assertThat(seconds).isEqualTo(9)
        assertThat(isAllowed()).isEqualTo(true)
    }

    @Test
    fun `idle email is no longer tracked after 61 seconds and has its full limit`() {
        useUp()
        assertThat(limiter.trackedEmails(LOGIN)).isEqualTo(1)

        clock.advanceBy(Duration.ofSeconds(61))

        assertThat(limiter.trackedEmails(LOGIN)).isEqualTo(0)
        assertThat((1..6).map { isAllowed() }).isEqualTo(listOf(true, true, true, true, true, false))
    }

    @Test
    fun `email in use stays tracked`() {
        repeat(4) {
            isAllowed()
            clock.advanceBy(Duration.ofSeconds(40))
        }

        assertThat(limiter.trackedEmails(LOGIN)).isEqualTo(1)
        assertThat(limiter.trackedEmails(REGISTER)).isEqualTo(0)
    }

    @Test
    fun `new limiter instance starts with full limits`() {
        useUp()
        assertThat(isAllowed()).isEqualTo(false)

        val restarted = Bucket4jAuthRateLimiter(properties, clock)

        assertThat(isAllowed(limiter = restarted)).isEqualTo(true)
    }

    @Test
    fun `tracks at most the configured number of emails per action`() {
        val bounded = Bucket4jAuthRateLimiter(properties.copy(maxTrackedEmails = 2), clock)

        listOf("a@example.com", "b@example.com", "c@example.com").forEach { bounded.check(LOGIN, it) }
        assertThat(bounded.trackedEmails(LOGIN)).isLessThanOrEqualTo(2)

        (1..500).forEach { bounded.check(LOGIN, "user$it@example.com") }
        assertThat(bounded.trackedEmails(LOGIN)).isLessThanOrEqualTo(2)

        listOf("a@example.com", "b@example.com").forEach { bounded.check(REGISTER, it) }
        assertThat(bounded.trackedEmails(REGISTER)).isEqualTo(2)
    }

    @Test
    fun `limits come from the configuration of each action`() {
        val configured =
            Bucket4jAuthRateLimiter(
                AuthRateLimitProperties(
                    login = AuthRateLimitProperties.Limit(2, Duration.ofSeconds(10)),
                    register = AuthRateLimitProperties.Limit(1, Duration.ofHours(1)),
                ),
                clock,
            )

        assertThat((1..3).map { isAllowed(LOGIN, limiter = configured) }).isEqualTo(listOf(true, true, false))
        assertThat((1..2).map { isAllowed(REGISTER, limiter = configured) }).isEqualTo(listOf(true, false))

        clock.advanceBy(Duration.ofSeconds(5))
        assertThat(isAllowed(LOGIN, limiter = configured)).isEqualTo(true)
        assertThat(isAllowed(REGISTER, limiter = configured)).isEqualTo(false)
    }

    @Test
    fun `clear forgets every counter`() {
        useUp(LOGIN)
        useUp(REGISTER)

        limiter.clear()

        assertThat(limiter.trackedEmails(LOGIN)).isEqualTo(0)
        assertThat(isAllowed(LOGIN)).isEqualTo(true)
        assertThat(isAllowed(REGISTER)).isEqualTo(true)
    }

    @Test
    fun `clock set back needs a clear to give the limit back`() {
        useUp()
        clock.set(clock.instant().minus(Duration.ofHours(1)))
        assertThat(isAllowed()).isEqualTo(false)

        limiter.clear()

        assertThat(isAllowed()).isEqualTo(true)
    }

    @Test
    fun `very long and unusual emails are counted like any other`() {
        val long = "a".repeat(200_000) + "@example.com"
        val odd = "\u0000😀 @"

        assertThat((1..6).map { isAllowed(email = long) }).isEqualTo(listOf(true, true, true, true, true, false))
        assertThat((1..6).map { isAllowed(email = odd) }).isEqualTo(listOf(true, true, true, true, true, false))
        assertThat(isAllowed(email = "a".repeat(200_001) + "@example.com")).isEqualTo(true)
    }

    @Test
    fun `parallel requests for one email pass exactly five times`() {
        val threads = 32
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threads)
        try {
            val futures =
                (1..threads).map {
                    executor.submit<Boolean> {
                        start.await()
                        isAllowed()
                    }
                }
            start.countDown()

            assertThat(futures.count { it.get(10, TimeUnit.SECONDS) }).isEqualTo(5)
        } finally {
            executor.shutdownNow()
        }
    }
}
