package com.plcoding.cardigan.board.infra.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * @property lockTimeout how long a write waits for the row lock of its board before it fails.
 */
@ConfigurationProperties("cardigan.board")
data class BoardProperties(
    val lockTimeout: Duration,
) {
    init {
        // Postgres reads a lock_timeout of 0 as "wait forever".
        require(lockTimeout.toMillis() > 0) { "cardigan.board.lock-timeout must be at least 1ms" }
    }
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BoardProperties::class)
class BoardConfig
