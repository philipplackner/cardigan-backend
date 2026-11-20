package com.plcoding.cardigan.user.infra.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * @property maxTrackedEmails how many emails the limiter tracks at most, per limited endpoint.
 */
@ConfigurationProperties("cardigan.user.auth.rate-limit")
data class AuthRateLimitProperties(
    val maxTrackedEmails: Long = 100_000,
    val login: Limit = Limit(),
    val register: Limit = Limit(),
) {

    init {
        require(maxTrackedEmails > 0) { "max-tracked-emails must be positive" }
    }

    /** [capacity] requests per [period] for one email, refilled evenly over the period. */
    data class Limit(
        val capacity: Long = 5,
        val period: Duration = Duration.ofMinutes(1),
    ) {

        init {
            require(capacity > 0) { "capacity must be positive" }
            require(period.isPositive) { "period must be positive" }
        }
    }
}
