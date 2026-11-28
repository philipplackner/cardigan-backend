package com.plcoding.cardigan.user.domain.model

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import com.plcoding.cardigan.user.api.dto.AuthSessionDto
import com.plcoding.cardigan.user.api.dto.AuthTokensDto
import com.plcoding.cardigan.user.api.dto.CurrentUserDto
import com.plcoding.cardigan.user.api.dto.LoginRequest
import com.plcoding.cardigan.user.api.dto.LogoutRequest
import com.plcoding.cardigan.user.api.dto.RefreshRequest
import com.plcoding.cardigan.user.api.dto.RegisterRequest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class SecretsInToStringTest {
    private val now = Instant.parse("2026-11-15T09:15:30.123Z")
    private val user = User(UUID.randomUUID(), "ann@example.com", "SECRET-password-hash", now, now)
    private val tokens =
        IssuedTokens("SECRET-access-token", now, "SECRET-refresh-token", now, RefreshTokenTransport.BODY)

    private fun assertNoSecret(value: Any) {
        assertThat(value.toString()).doesNotContain("SECRET")
    }

    @Test
    fun `request DTOs leave out the password and the refresh token`() {
        assertNoSecret(RegisterRequest("ann@example.com", "SECRET-password", "BODY"))
        assertNoSecret(LoginRequest("ann@example.com", "SECRET-password", "BODY"))
        assertNoSecret(RefreshRequest("BODY", "SECRET-refresh-token"))
        assertNoSecret(LogoutRequest("BODY", "SECRET-refresh-token"))
    }

    @Test
    fun `response DTOs leave out both tokens`() {
        val userDto = CurrentUserDto(user.id, user.email, user.createdAt)

        assertNoSecret(AuthSessionDto("SECRET-access-token", now, "SECRET-refresh-token", userDto))
        assertNoSecret(AuthTokensDto("SECRET-access-token", now, "SECRET-refresh-token"))
    }

    @Test
    fun `domain models leave out passwords, tokens and hashes`() {
        assertNoSecret(Credentials("ann@example.com", "SECRET-password", RefreshTokenTransport.BODY))
        assertNoSecret(tokens)
        assertNoSecret(IssuedSession(user, tokens))
        assertNoSecret(RefreshResult.Refreshed(tokens))
        assertNoSecret(IssuedRefreshToken("SECRET-refresh-token", now))
        assertNoSecret(RefreshToken(UUID.randomUUID(), UUID.randomUUID(), "SECRET-token-hash", now, now))
        assertNoSecret(user)
    }

    @Test
    fun `toString still names what is safe to log`() {
        assertThat(RegisterRequest("ann@example.com", "SECRET-password", "BODY").toString()).contains("ann@example.com")
        assertThat(IssuedSession(user, tokens).toString()).contains(user.id.toString())
    }
}
