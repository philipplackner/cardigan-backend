package com.plcoding.cardigan.common.infra.security

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.time.Clock
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties::class)
class JwtConfig {
    /**
     * Decodes and checks `JWT_SECRET`. Error messages name the problem and never the value.
     */
    @Bean
    fun jwtSecretKey(properties: JwtProperties): SecretKey {
        val secret = properties.secret?.trim()
        check(!secret.isNullOrEmpty() && !secret.startsWith("\${")) { "JWT_SECRET is missing" }
        val bytes =
            try {
                Base64.getDecoder().decode(secret)
            } catch (_: IllegalArgumentException) {
                throw IllegalStateException("JWT_SECRET is not valid base64")
            }
        check(bytes.size >= MIN_SECRET_BYTES) {
            "JWT_SECRET is too short: it must decode to at least $MIN_SECRET_BYTES bytes"
        }
        return SecretKeySpec(bytes, "HmacSHA256")
    }

    @Bean
    fun jwtDecoder(
        jwtSecretKey: SecretKey,
        clock: Clock,
    ): JwtDecoder =
        NimbusJwtDecoder
            .withSecretKey(jwtSecretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build()
            .apply {
                setJwtValidator(
                    DelegatingOAuth2TokenValidator(
                        ExpiryValidator(clock),
                        TokenTypeValidator(),
                        SubjectValidator(),
                    ),
                )
            }

    @Bean
    fun jwtEncoder(jwtSecretKey: SecretKey): JwtEncoder =
        NimbusJwtEncoder(ImmutableSecret<SecurityContext>(jwtSecretKey))

    private companion object {
        const val MIN_SECRET_BYTES = 32
    }
}
