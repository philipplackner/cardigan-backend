package com.plcoding.cardigan.user.service

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.scheduling.support.CronExpression
import java.time.Duration

/**
 * @property refreshTokenTtl how long a refresh token stays valid after it was issued.
 * @property sessionCleanupCron when ended sessions are deleted, as a Spring cron expression in UTC.
 */
@ConfigurationProperties("cardigan.user.auth")
data class AuthProperties(
    val refreshTokenTtl: Duration = Duration.ofDays(30),
    val sessionCleanupCron: String = "0 0 3 * * *",
) {

    init {
        require(refreshTokenTtl.isPositive) { "refresh-token-ttl must be positive" }
        require(CronExpression.isValidExpression(sessionCleanupCron)) { "session-cleanup-cron is not a cron expression" }
    }
}
