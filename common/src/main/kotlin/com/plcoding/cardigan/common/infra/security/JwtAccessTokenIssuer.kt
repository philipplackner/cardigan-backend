package com.plcoding.cardigan.common.infra.security

import com.plcoding.cardigan.common.domain.security.AccessTokenIssuer
import com.plcoding.cardigan.common.domain.security.IssuedAccessToken
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Issues HS256 access tokens with exactly `sub`, `type`, `iat` and `exp`. The issue time is cut to
 * whole seconds, so the returned expiry equals the token's `exp` to the millisecond.
 */
@Component
class JwtAccessTokenIssuer(
    private val jwtEncoder: JwtEncoder,
    private val clock: Clock,
    private val properties: JwtProperties,
) : AccessTokenIssuer {

    override fun issue(userId: UUID): IssuedAccessToken {
        val issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS)
        val expiresAt = issuedAt.plus(properties.accessTokenTtl)
        val claims = JwtClaimsSet.builder()
            .subject(userId.toString())
            .claim(JwtClaims.TYPE, JwtClaims.TYPE_ACCESS)
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
        return IssuedAccessToken(token = token, expiresAt = expiresAt)
    }
}
