package com.plcoding.cardigan.common.infra.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * @property secret the base64 HMAC secret from `JWT_SECRET`. Bound as a plain string with no checks,
 *   so a binding failure can never print it. [JwtConfig] checks it after binding.
 * @property accessTokenTtl how long an access token stays valid.
 */
@ConfigurationProperties("cardigan.security.jwt")
data class JwtProperties(
    val secret: String? = null,
    val accessTokenTtl: Duration,
) {
    override fun toString(): String = "JwtProperties(secret=***, accessTokenTtl=$accessTokenTtl)"
}
