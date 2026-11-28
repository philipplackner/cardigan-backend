package com.plcoding.cardigan.user.domain.model

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class RefreshTokenTest {
    private val issuedAt = Instant.parse("2026-11-15T09:15:30.123Z")
    private val expiresAt = Instant.parse("2026-12-15T09:15:30.123Z")

    private fun token(rotatedAt: Instant? = null) =
        RefreshToken(
            UUID.randomUUID(),
            UUID.randomUUID(),
            hashRefreshToken("secret-token"),
            issuedAt,
            expiresAt,
            rotatedAt,
        )

    @Test
    fun `hash is the lowercase hex SHA-256`() {
        assertThat(hashRefreshToken("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        assertThat(hashRefreshToken(""))
            .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    }

    @Test
    fun `hash covers the UTF-8 bytes of the whole value`() {
        assertThat(hashRefreshToken("é"))
            .isEqualTo("4a99557e4033c3539de2eb65472017cad5f9557f7a0625a09f1c3f6e2ba69c4c")
        assertThat(hashRefreshToken("token ")).isNotEqualTo(hashRefreshToken("token"))
        assertThat(hashRefreshToken("Token")).isNotEqualTo(hashRefreshToken("token"))
    }

    @Test
    fun `token is valid 1 ms before its expiry and expired at it`() {
        assertThat(token().isExpiredAt(expiresAt.minusMillis(1))).isFalse()
        assertThat(token().isExpiredAt(expiresAt)).isTrue()
        assertThat(token().isExpiredAt(expiresAt.plusMillis(1))).isTrue()
    }

    @Test
    fun `token is rotated once it has a rotation time`() {
        assertThat(token().isRotated).isFalse()
        assertThat(token(rotatedAt = issuedAt).isRotated).isTrue()
    }

    @Test
    fun `session is revoked once it has a revocation time`() {
        val session = Session(UUID.randomUUID(), UUID.randomUUID(), RefreshTokenTransport.BODY, issuedAt)

        assertThat(session.isRevoked).isFalse()
        assertThat(session.copy(revokedAt = issuedAt).isRevoked).isTrue()
    }

    @Test
    fun `toString leaves out the token hash`() {
        assertThat(token().toString()).doesNotContain(hashRefreshToken("secret-token"))
    }
}
