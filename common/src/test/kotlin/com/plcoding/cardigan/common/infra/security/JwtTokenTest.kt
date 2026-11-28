package com.plcoding.cardigan.common.infra.security

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.plcoding.cardigan.common.MutableClock
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

class JwtTokenTest {
    private val secret = ByteArray(64) { (it * 7 + 3).toByte() }
    private val key = SecretKeySpec(secret, "HmacSHA256")
    private val clock = MutableClock(Instant.parse("2026-11-15T09:15:30.789Z"))
    private val config = JwtConfig()
    private val decoder: JwtDecoder = config.jwtDecoder(key, clock)
    private val issuer =
        JwtAccessTokenIssuer(
            config.jwtEncoder(key),
            clock,
            JwtProperties(secret = null, accessTokenTtl = Duration.ofMinutes(15)),
        )
    private val userId = UUID.fromString("0b6a1f43-58c2-4d0e-9a35-3f1c9e2b7d10")

    private val now = Date.from(clock.instant())
    private val inFiveMinutes = Date.from(clock.instant().plusSeconds(300))

    private fun signed(
        claims: JWTClaimsSet.Builder.() -> Unit,
        algorithm: JWSAlgorithm = JWSAlgorithm.HS256,
        signingSecret: ByteArray = secret,
    ): String {
        val jwt = SignedJWT(JWSHeader(algorithm), JWTClaimsSet.Builder().apply(claims).build())
        jwt.sign(MACSigner(signingSecret))
        return jwt.serialize()
    }

    private fun validClaims(): JWTClaimsSet.Builder.() -> Unit =
        {
            subject(userId.toString())
            claim("type", "access")
            issueTime(now)
            expirationTime(inFiveMinutes)
        }

    @Test
    fun `issued token has HS256 and exactly sub, type, iat and exp`() {
        val token = issuer.issue(userId).token

        val parsed = SignedJWT.parse(token)
        assertThat(parsed.header.algorithm).isEqualTo(JWSAlgorithm.HS256)
        assertThat(parsed.jwtClaimsSet.claims.keys).isEqualTo(setOf("sub", "type", "iat", "exp"))
        assertThat(parsed.jwtClaimsSet.subject).isEqualTo(userId.toString())
        assertThat(parsed.jwtClaimsSet.getStringClaim("type")).isEqualTo("access")
    }

    @Test
    fun `issue time is truncated to whole seconds and expiry is 900 seconds later`() {
        val issued = issuer.issue(userId)

        val claims = SignedJWT.parse(issued.token).jwtClaimsSet
        assertThat(claims.issueTime.toInstant()).isEqualTo(Instant.parse("2026-11-15T09:15:30Z"))
        assertThat(claims.expirationTime.toInstant()).isEqualTo(Instant.parse("2026-11-15T09:30:30Z"))
        assertThat(issued.expiresAt).isEqualTo(Instant.parse("2026-11-15T09:30:30.000Z"))
    }

    @Test
    fun `issued token decodes to its user`() {
        val jwt = decoder.decode(issuer.issue(userId).token)

        assertThat(jwt.subject).isEqualTo(userId.toString())
    }

    @Test
    fun `token is accepted 1 ms before its expiry`() {
        val issued = issuer.issue(userId)

        clock.set(issued.expiresAt.minusMillis(1))

        assertThat(decoder.decode(issued.token).subject).isEqualTo(userId.toString())
    }

    @Test
    fun `token is rejected exactly at its expiry`() {
        val issued = issuer.issue(userId)

        clock.set(issued.expiresAt)

        assertFailure { decoder.decode(issued.token) }.isInstanceOf<JwtException>()
    }

    @Test
    fun `token is rejected 1 second after its expiry`() {
        val issued = issuer.issue(userId)

        clock.set(issued.expiresAt.plusSeconds(1))

        assertFailure { decoder.decode(issued.token) }.isInstanceOf<JwtException>()
    }

    @Test
    fun `alg none is rejected`() {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"none"}""".toByteArray())
        val payload =
            encoder.encodeToString(
                """{"sub":"$userId","type":"access","iat":${now.time / 1000},"exp":${inFiveMinutes.time / 1000}}"""
                    .toByteArray(),
            )

        assertFailure { decoder.decode("$header.$payload.") }.isInstanceOf<JwtException>()
    }

    @Test
    fun `HS512 with the same secret is rejected`() {
        val token = signed(validClaims(), algorithm = JWSAlgorithm.HS512)

        assertFailure { decoder.decode(token) }.isInstanceOf<JwtException>()
    }

    @Test
    fun `HS256 with another secret is rejected`() {
        val token = signed(validClaims(), signingSecret = ByteArray(32) { 42 })

        assertFailure { decoder.decode(token) }.isInstanceOf<JwtException>()
    }

    @Test
    fun `valid hand-built token is accepted`() {
        assertThat(decoder.decode(signed(validClaims())).subject).isEqualTo(userId.toString())
    }

    @Test
    fun `type refresh is rejected`() {
        val token =
            signed({
                validClaims()()
                claim("type", "refresh")
            })

        assertFailure { decoder.decode(token) }.isInstanceOf<JwtException>()
    }

    @Test
    fun `missing type is rejected`() {
        val token =
            signed({
                subject(userId.toString())
                issueTime(now)
                expirationTime(inFiveMinutes)
            })

        assertFailure { decoder.decode(token) }.isInstanceOf<JwtException>()
    }

    @Test
    fun `missing expiry is rejected`() {
        val token =
            signed({
                subject(userId.toString())
                claim("type", "access")
                issueTime(now)
            })

        assertFailure { decoder.decode(token) }.isInstanceOf<JwtException>()
    }

    @Test
    fun `non-UUID subject is rejected`() {
        val token =
            signed({
                validClaims()()
                subject("admin")
            })

        assertFailure { decoder.decode(token) }.isInstanceOf<JwtException>()
    }

    @Test
    fun `missing subject is rejected`() {
        val token =
            signed({
                claim("type", "access")
                issueTime(now)
                expirationTime(inFiveMinutes)
            })

        assertFailure { decoder.decode(token) }.isInstanceOf<JwtException>()
    }
}
