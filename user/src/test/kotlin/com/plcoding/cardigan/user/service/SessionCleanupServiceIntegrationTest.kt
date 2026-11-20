package com.plcoding.cardigan.user.service

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import com.plcoding.cardigan.user.UserIntegrationTest
import com.plcoding.cardigan.user.domain.model.Credentials
import com.plcoding.cardigan.user.domain.model.IssuedTokens
import com.plcoding.cardigan.user.domain.model.RefreshResult
import com.plcoding.cardigan.user.domain.model.RefreshTokenTransport.BODY
import com.plcoding.cardigan.user.domain.model.hashRefreshToken
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.sql.Timestamp
import java.time.Duration

class SessionCleanupServiceIntegrationTest : UserIntegrationTest() {

    @Autowired
    private lateinit var authService: AuthService

    @Autowired
    private lateinit var cleanupService: SessionCleanupService

    private val password = "Correct horse 7"

    private fun register(email: String = "ann@example.com"): IssuedTokens =
        authService.register(Credentials(email, password, BODY)).tokens

    private fun login(email: String = "ann@example.com"): IssuedTokens =
        authService.login(Credentials(email, password, BODY)).tokens

    private fun refresh(token: String): IssuedTokens = (authService.refresh(BODY, token) as RefreshResult.Refreshed).tokens

    private fun count(table: String): Int =
        jdbcTemplate.queryForObject("SELECT count(*) FROM user_service.$table", Int::class.java)!!

    private fun isStored(refreshToken: String): Boolean = jdbcTemplate.queryForObject(
        "SELECT count(*) FROM user_service.refresh_tokens WHERE token_hash = ?",
        Int::class.java,
        hashRefreshToken(refreshToken),
    ) == 1

    @Test
    fun `logged-out session and all its tokens are deleted`() {
        val first = register()
        val second = refresh(first.refreshToken)
        val third = refresh(second.refreshToken)
        authService.logout(BODY, third.refreshToken)

        cleanupService.deleteEndedSessions()

        assertThat(count("sessions")).isEqualTo(0)
        assertThat(count("refresh_tokens")).isEqualTo(0)
        assertThat(count("users")).isEqualTo(1)
    }

    @Test
    fun `session revoked after token reuse is deleted`() {
        val first = register()
        refresh(first.refreshToken)
        authService.refresh(BODY, first.refreshToken)

        cleanupService.deleteEndedSessions()

        assertThat(count("sessions")).isEqualTo(0)
        assertThat(count("refresh_tokens")).isEqualTo(0)
    }

    @Test
    fun `session whose current token expires at this instant is deleted`() {
        val tokens = register()
        clock.set(tokens.refreshTokenExpiresAt)

        cleanupService.deleteEndedSessions()

        assertThat(count("sessions")).isEqualTo(0)
        assertThat(count("refresh_tokens")).isEqualTo(0)
    }

    @Test
    fun `session whose current token expires in 1 ms is kept and still refreshes`() {
        val tokens = register()
        clock.set(tokens.refreshTokenExpiresAt.minusMillis(1))

        cleanupService.deleteEndedSessions()

        assertThat(count("sessions")).isEqualTo(1)
        assertThat(authService.refresh(BODY, tokens.refreshToken)).isInstanceOf<RefreshResult.Refreshed>()
    }

    @Test
    fun `session with expired rotated tokens and an expired current token is deleted with all of them`() {
        val first = register()
        clock.advanceBy(Duration.ofDays(20))
        refresh(first.refreshToken)
        clock.advanceBy(Duration.ofDays(30))

        cleanupService.deleteEndedSessions()

        assertThat(count("sessions")).isEqualTo(0)
        assertThat(count("refresh_tokens")).isEqualTo(0)
    }

    @Test
    fun `active session keeps all 41 tokens after 40 refreshes over 60 days`() {
        val first = register()
        var current = first
        repeat(40) {
            clock.advanceBy(Duration.ofHours(36))
            current = refresh(current.refreshToken)
        }

        cleanupService.deleteEndedSessions()

        assertThat(count("sessions")).isEqualTo(1)
        assertThat(count("refresh_tokens")).isEqualTo(41)
        assertThat(authService.refresh(BODY, first.refreshToken)).isEqualTo(RefreshResult.Rejected)
        val revokedAt = jdbcTemplate.queryForObject("SELECT revoked_at FROM user_service.sessions", Timestamp::class.java)
        assertThat(revokedAt).isNotNull()
        assertThat(authService.refresh(BODY, current.refreshToken)).isEqualTo(RefreshResult.Rejected)
    }

    @Test
    fun `token of a deleted session is rejected`() {
        val tokens = register()
        authService.logout(BODY, tokens.refreshToken)
        cleanupService.deleteEndedSessions()

        assertThat(isStored(tokens.refreshToken)).isEqualTo(false)
        assertThat(authService.refresh(BODY, tokens.refreshToken)).isEqualTo(RefreshResult.Rejected)
        authService.logout(BODY, tokens.refreshToken)
    }

    @Test
    fun `only ended sessions are deleted`() {
        val loggedOut = register("ann@example.com")
        val active = login("ann@example.com")
        val otherUser = register("bob@example.com")
        clock.advanceBy(Duration.ofDays(10))
        val expiring = login("bob@example.com")
        authService.logout(BODY, loggedOut.refreshToken)
        clock.set(active.refreshTokenExpiresAt)

        cleanupService.deleteEndedSessions()

        assertThat(count("sessions")).isEqualTo(1)
        assertThat(isStored(expiring.refreshToken)).isEqualTo(true)
        assertThat(isStored(active.refreshToken)).isEqualTo(false)
        assertThat(isStored(otherUser.refreshToken)).isEqualTo(false)
        assertThat(count("users")).isEqualTo(2)
    }

    @Test
    fun `running twice and on an empty table changes nothing more`() {
        cleanupService.deleteEndedSessions()
        val tokens = register()

        cleanupService.deleteEndedSessions()
        cleanupService.deleteEndedSessions()

        assertThat(count("sessions")).isEqualTo(1)
        assertThat(isStored(tokens.refreshToken)).isEqualTo(true)
    }
}
