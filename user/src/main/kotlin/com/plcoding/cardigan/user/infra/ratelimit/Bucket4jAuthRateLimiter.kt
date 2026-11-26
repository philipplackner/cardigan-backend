package com.plcoding.cardigan.user.infra.ratelimit

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.plcoding.cardigan.user.domain.exception.AuthRateLimitedException
import com.plcoding.cardigan.user.domain.model.AuthAction
import com.plcoding.cardigan.user.domain.repository.AuthRateLimiter
import com.plcoding.cardigan.user.infra.config.AuthRateLimitProperties
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.TimeMeter
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Clock
import java.util.HexFormat
import java.util.concurrent.TimeUnit

/**
 * Keeps one token bucket per action and email in the memory of this instance. Limits therefore
 * count per instance and start full after a restart.
 */
@Component
class Bucket4jAuthRateLimiter(
    private val properties: AuthRateLimitProperties,
    private val clock: Clock,
) : AuthRateLimiter {

    private val timeMeter = object : TimeMeter {
        override fun currentTimeNanos(): Long = clockNanos()
        override fun isWallClockBased(): Boolean = true
    }

    private val buckets: Map<AuthAction, Cache<String, Bucket>> =
        AuthAction.entries.associateWith { action ->
            Caffeine.newBuilder()
                .maximumSize(properties.maxTrackedEmails)
                // A bucket that was idle for a full period is full again, so dropping it changes nothing.
                .expireAfterAccess(limitOf(action).period)
                .ticker(::clockNanos)
                // Evicts on the calling thread, so the size bound holds after every call.
                .executor(Runnable::run)
                .build()
        }

    override fun check(action: AuthAction, email: String) {
        val bucket = buckets.getValue(action).get(keyOf(email)) { newBucket(action) }
        val probe = bucket.tryConsumeAndReturnRemaining(1)
        if (!probe.isConsumed) {
            throw AuthRateLimitedException(action, retryAfterSeconds(probe.nanosToWaitForRefill))
        }
    }

    /** How many emails are tracked for [action] right now. */
    fun trackedEmails(action: AuthAction): Long {
        val cache = buckets.getValue(action)
        cache.cleanUp()
        return cache.estimatedSize()
    }

    /** Forgets every counter. Tests call it, because a test clock that is set back never refills a bucket. */
    fun clear() {
        buckets.values.forEach { it.invalidateAll() }
    }

    private fun limitOf(action: AuthAction): AuthRateLimitProperties.Limit = when (action) {
        AuthAction.LOGIN -> properties.login
        AuthAction.REGISTER -> properties.register
    }

    private fun newBucket(action: AuthAction): Bucket {
        val limit = limitOf(action)
        return Bucket.builder()
            .addLimit(Bandwidth.builder().capacity(limit.capacity).refillGreedy(limit.capacity, limit.period).build())
            .withCustomTimePrecision(timeMeter)
            .build()
    }

    private fun retryAfterSeconds(nanosToWait: Long): Long =
        ((nanosToWait + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND).coerceAtLeast(1)

    private fun clockNanos(): Long {
        val now = clock.instant()
        return TimeUnit.SECONDS.toNanos(now.epochSecond) + now.nano
    }

    // The key is a hash, so a very long email takes no more memory than a short one.
    private fun keyOf(email: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(email.toByteArray(Charsets.UTF_8)))

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
