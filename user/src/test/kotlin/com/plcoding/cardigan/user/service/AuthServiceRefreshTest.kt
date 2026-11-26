package com.plcoding.cardigan.user.service

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.plcoding.cardigan.user.domain.model.RefreshResult
import com.plcoding.cardigan.user.domain.model.RefreshTokenTransport
import com.plcoding.cardigan.user.domain.model.RefreshTokenTransport.BODY
import com.plcoding.cardigan.user.domain.model.RefreshTokenTransport.COOKIE
import com.plcoding.cardigan.user.domain.model.hashRefreshToken
import org.junit.jupiter.api.Test
import java.time.Duration

class AuthServiceRefreshTest : AuthServiceTestBase() {

    private fun assertRejected(token: String?, transport: RefreshTokenTransport = BODY) {
        assertThat(service.refresh(transport, token)).isEqualTo(RefreshResult.Rejected)
    }

    @Test
    fun `refresh rotates the token and issues one that lasts 30 days`() {
        val first = register().tokens
        clock.advanceBy(Duration.ofDays(3))

        val result = service.refresh(BODY, first.refreshToken)

        assertThat(result).isInstanceOf<RefreshResult.Refreshed>()
        val tokens = (result as RefreshResult.Refreshed).tokens
        assertThat(tokens.refreshToken).isNotEqualTo(first.refreshToken)
        assertThat(tokens.refreshTokenExpiresAt).isEqualTo(clock.instant().plus(Duration.ofDays(30)))
        assertThat(tokens.transport).isEqualTo(BODY)
        assertThat(stored(first.refreshToken).rotatedAt).isEqualTo(clock.instant())
        val newToken = stored(tokens.refreshToken)
        assertThat(newToken.sessionId).isEqualTo(stored(first.refreshToken).sessionId)
        assertThat(newToken.createdAt).isEqualTo(clock.instant())
        assertThat(newToken.expiresAt).isEqualTo(clock.instant().plus(Duration.ofDays(30)))
        assertThat(newToken.rotatedAt).isNull()
        assertThat(sessions.sessions).hasSize(1)
        assertThat(sessionOf(first.refreshToken).revokedAt).isNull()
    }

    @Test
    fun `refresh issues an access token for the session's user`() {
        val issued = register()
        clock.advanceBy(Duration.ofMinutes(20))

        val tokens = refreshed(issued.tokens.refreshToken)

        val (userId, accessToken) = accessTokens.issued.last()
        assertThat(userId).isEqualTo(issued.user.id)
        assertThat(tokens.accessToken).isEqualTo(accessToken.token)
        assertThat(tokens.accessToken).isNotEqualTo(issued.tokens.accessToken)
        assertThat(tokens.accessTokenExpiresAt).isEqualTo(accessToken.expiresAt)
    }

    @Test
    fun `unknown token fails`() {
        register()

        assertRejected("no-such-token")
        assertRejected("")

        assertThat(sessions.active).hasSize(1)
    }

    @Test
    fun `token hash is not accepted as a token`() {
        val token = register().tokens.refreshToken

        assertRejected(hashRefreshToken(token))
        assertRejected(token.uppercase())
        assertRejected("$token ")
    }

    @Test
    fun `token whose session was deleted meanwhile fails and issues nothing`() {
        val token = register().tokens.refreshToken
        sessions.sessions.remove(sessionOf(token).id)
        val issuedBefore = accessTokens.issued.size

        assertRejected(token)

        assertThat(stored(token).rotatedAt).isNull()
        assertThat(accessTokens.issued).hasSize(issuedBefore)
    }

    @Test
    fun `missing token fails`() {
        register()

        assertRejected(null)

        assertThat(sessions.active).hasSize(1)
    }

    @Test
    fun `token works 1 ms before its expiry`() {
        val tokens = register().tokens

        clock.set(tokens.refreshTokenExpiresAt.minusMillis(1))

        assertThat(service.refresh(BODY, tokens.refreshToken)).isInstanceOf<RefreshResult.Refreshed>()
    }

    @Test
    fun `token fails at its exact expiry and nothing changes`() {
        val tokens = register().tokens
        val tokenBefore = stored(tokens.refreshToken)

        clock.set(tokens.refreshTokenExpiresAt)
        assertRejected(tokens.refreshToken)

        assertThat(stored(tokens.refreshToken)).isEqualTo(tokenBefore)
        assertThat(sessionOf(tokens.refreshToken).revokedAt).isNull()
        assertThat(refreshTokens.tokens).hasSize(1)
    }

    @Test
    fun `token fails after 31 idle days`() {
        val tokens = register().tokens

        clock.advanceBy(Duration.ofDays(31))

        assertRejected(tokens.refreshToken)
    }

    @Test
    fun `token of a revoked session fails`() {
        val tokens = register().tokens
        service.logout(BODY, tokens.refreshToken)
        val revokedAt = sessionOf(tokens.refreshToken).revokedAt

        clock.advanceBy(Duration.ofSeconds(1))
        assertRejected(tokens.refreshToken)

        assertThat(stored(tokens.refreshToken).rotatedAt).isNull()
        assertThat(sessionOf(tokens.refreshToken).revokedAt).isEqualTo(revokedAt)
    }

    @Test
    fun `wrong transport fails and changes nothing`() {
        val tokens = register(transport = COOKIE).tokens
        val tokenBefore = stored(tokens.refreshToken)

        assertRejected(tokens.refreshToken, BODY)

        assertThat(stored(tokens.refreshToken)).isEqualTo(tokenBefore)
        assertThat(sessionOf(tokens.refreshToken).revokedAt).isNull()
        assertThat(service.refresh(COOKIE, tokens.refreshToken)).isInstanceOf<RefreshResult.Refreshed>()
    }

    @Test
    fun `rotated token with the wrong transport doesn't revoke the session`() {
        val first = register(transport = COOKIE).tokens
        val second = refreshed(first.refreshToken, COOKIE)

        assertRejected(first.refreshToken, BODY)

        assertThat(sessionOf(first.refreshToken).revokedAt).isNull()
        assertThat(service.refresh(COOKIE, second.refreshToken)).isInstanceOf<RefreshResult.Refreshed>()
    }

    @Test
    fun `rotated token revokes the session and the current token then fails`() {
        val first = register().tokens
        val second = refreshed(first.refreshToken)
        clock.advanceBy(Duration.ofMinutes(5))

        assertRejected(first.refreshToken)

        assertThat(sessionOf(first.refreshToken).revokedAt).isEqualTo(clock.instant())
        assertRejected(second.refreshToken)
        assertThat(stored(second.refreshToken).rotatedAt).isNull()
        assertThat(refreshTokens.tokens).hasSize(2)
    }

    @Test
    fun `rotated token is detected after its own 30 days`() {
        val first = register().tokens
        clock.advanceBy(Duration.ofDays(29))
        val second = refreshed(first.refreshToken)
        clock.advanceBy(Duration.ofDays(2))

        assertRejected(first.refreshToken)

        assertThat(sessionOf(first.refreshToken).revokedAt).isNotNull()
        assertRejected(second.refreshToken)
    }

    @Test
    fun `reuse of an old token in a long chain revokes the session`() {
        val first = register().tokens
        var current = first
        repeat(5) { current = refreshed(current.refreshToken) }

        assertRejected(first.refreshToken)

        assertRejected(current.refreshToken)
    }

    @Test
    fun `losing the claim to a parallel refresh counts as reuse`() {
        val tokens = register().tokens
        refreshTokens.nextClaimIsLost = true

        assertRejected(tokens.refreshToken)

        assertThat(sessionOf(tokens.refreshToken).revokedAt).isEqualTo(clock.instant())
        assertThat(refreshTokens.tokens).hasSize(1)
        assertThat(accessTokens.issued).hasSize(1)
    }

    @Test
    fun `second session of the same user is unaffected by reuse`() {
        val deviceA = register().tokens
        val deviceB = login().tokens
        refreshed(deviceA.refreshToken)

        assertRejected(deviceA.refreshToken)

        assertThat(sessionOf(deviceB.refreshToken).revokedAt).isNull()
        assertThat(service.refresh(BODY, deviceB.refreshToken)).isInstanceOf<RefreshResult.Refreshed>()
    }

    @Test
    fun `session survives 29-day refresh intervals`() {
        var tokens = register().tokens

        repeat(3) {
            clock.advanceBy(Duration.ofDays(29))
            tokens = refreshed(tokens.refreshToken)
        }

        assertThat(sessionOf(tokens.refreshToken).revokedAt).isNull()
        assertThat(refreshTokens.tokens).hasSize(4)
    }

    @Test
    fun `token from a refresh on day 29 fails 31 days after that refresh`() {
        val first = register().tokens
        clock.advanceBy(Duration.ofDays(29))
        val second = refreshed(first.refreshToken)

        clock.advanceBy(Duration.ofDays(31))

        assertRejected(second.refreshToken)
    }

    @Test
    fun `failed refresh issues no tokens`() {
        register()
        val issuedBefore = accessTokens.issued.size
        val generatedBefore = generator.generated.size

        assertRejected("no-such-token")
        assertRejected(null)

        assertThat(accessTokens.issued).hasSize(issuedBefore)
        assertThat(generator.generated).hasSize(generatedBefore)
    }
}
