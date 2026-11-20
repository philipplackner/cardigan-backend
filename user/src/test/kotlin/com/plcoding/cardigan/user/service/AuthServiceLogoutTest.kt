package com.plcoding.cardigan.user.service

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import com.plcoding.cardigan.user.domain.model.RefreshResult
import com.plcoding.cardigan.user.domain.model.RefreshTokenTransport.BODY
import com.plcoding.cardigan.user.domain.model.RefreshTokenTransport.COOKIE
import org.junit.jupiter.api.Test
import java.time.Duration

class AuthServiceLogoutTest : AuthServiceTestBase() {

    @Test
    fun `current token revokes the session`() {
        val tokens = register().tokens
        clock.advanceBy(Duration.ofMinutes(3))

        service.logout(BODY, tokens.refreshToken)

        assertThat(sessionOf(tokens.refreshToken).revokedAt).isEqualTo(clock.instant())
        assertThat(service.refresh(BODY, tokens.refreshToken)).isEqualTo(RefreshResult.Rejected)
        assertThat(refreshTokens.tokens).hasSize(1)
    }

    @Test
    fun `current token works 1 ms before its expiry`() {
        val tokens = register().tokens
        clock.set(tokens.refreshTokenExpiresAt.minusMillis(1))

        service.logout(BODY, tokens.refreshToken)

        assertThat(sessionOf(tokens.refreshToken).revokedAt).isEqualTo(clock.instant())
    }

    @Test
    fun `rotated token revokes the session and its current token`() {
        val first = register().tokens
        val second = refreshed(first.refreshToken)

        service.logout(BODY, first.refreshToken)

        assertThat(sessionOf(first.refreshToken).revokedAt).isEqualTo(clock.instant())
        assertThat(service.refresh(BODY, second.refreshToken)).isEqualTo(RefreshResult.Rejected)
    }

    @Test
    fun `rotated token revokes the session also after its own expiry`() {
        val first = register().tokens
        clock.advanceBy(Duration.ofDays(29))
        val second = refreshed(first.refreshToken)
        clock.advanceBy(Duration.ofDays(2))

        service.logout(BODY, first.refreshToken)

        assertThat(sessionOf(first.refreshToken).revokedAt).isEqualTo(clock.instant())
        assertThat(service.refresh(BODY, second.refreshToken)).isEqualTo(RefreshResult.Rejected)
    }

    @Test
    fun `unknown and missing tokens change nothing and don't throw`() {
        val tokens = register().tokens

        service.logout(BODY, "no-such-token")
        service.logout(BODY, "")
        service.logout(BODY, null)
        service.logout(COOKIE, null)

        assertThat(sessionOf(tokens.refreshToken).revokedAt).isNull()
    }

    @Test
    fun `expired unrotated token changes nothing`() {
        val tokens = register().tokens

        clock.set(tokens.refreshTokenExpiresAt)
        service.logout(BODY, tokens.refreshToken)
        clock.advanceBy(Duration.ofSeconds(1))
        service.logout(BODY, tokens.refreshToken)

        assertThat(sessionOf(tokens.refreshToken).revokedAt).isNull()
    }

    @Test
    fun `already revoked session keeps its first revocation time`() {
        val tokens = register().tokens
        service.logout(BODY, tokens.refreshToken)
        val revokedAt = sessionOf(tokens.refreshToken).revokedAt

        clock.advanceBy(Duration.ofMinutes(1))
        service.logout(BODY, tokens.refreshToken)

        assertThat(sessionOf(tokens.refreshToken).revokedAt).isEqualTo(revokedAt)
    }

    @Test
    fun `wrong transport changes nothing`() {
        val tokens = register(transport = COOKIE).tokens

        service.logout(BODY, tokens.refreshToken)

        assertThat(sessionOf(tokens.refreshToken).revokedAt).isNull()
        assertThat(service.refresh(COOKIE, tokens.refreshToken)).isInstanceOf<RefreshResult.Refreshed>()
    }

    @Test
    fun `rotated token with the wrong transport changes nothing`() {
        val first = register(transport = COOKIE).tokens
        refreshed(first.refreshToken, COOKIE)

        service.logout(BODY, first.refreshToken)

        assertThat(sessionOf(first.refreshToken).revokedAt).isNull()
    }

    @Test
    fun `logout ends only its own session`() {
        val web = register(transport = COOKIE).tokens
        val android = login(transport = BODY).tokens

        service.logout(COOKIE, web.refreshToken)

        assertThat(service.refresh(COOKIE, web.refreshToken)).isEqualTo(RefreshResult.Rejected)
        assertThat(sessionOf(android.refreshToken).revokedAt).isNull()
        assertThat(service.refresh(BODY, android.refreshToken)).isInstanceOf<RefreshResult.Refreshed>()
    }

    @Test
    fun `logout issues no tokens`() {
        val tokens = register().tokens
        val issuedBefore = accessTokens.issued.size

        service.logout(BODY, tokens.refreshToken)

        assertThat(accessTokens.issued).hasSize(issuedBefore)
        assertThat(generator.generated).hasSize(1)
    }
}
