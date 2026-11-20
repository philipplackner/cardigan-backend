package com.plcoding.cardigan.user.infra.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * @property bcryptCost the BCrypt cost factor (log rounds), 4 to 31.
 * @property maxConcurrentHashes how many BCrypt computations may run at once. Further requests wait.
 */
@ConfigurationProperties("cardigan.user.auth")
data class PasswordHashingProperties(
    val bcryptCost: Int = 12,
    val maxConcurrentHashes: Int = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1),
) {

    init {
        require(bcryptCost in 4..31) { "bcrypt-cost must be between 4 and 31" }
        require(maxConcurrentHashes > 0) { "max-concurrent-hashes must be positive" }
    }
}
