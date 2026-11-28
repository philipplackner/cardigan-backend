package com.plcoding.cardigan

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.matches
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import com.plcoding.cardigan.user.domain.model.hashRefreshToken
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Duration

class AuthRefreshLogoutE2eTest : AuthE2eTestBase() {
    private fun bodySession(email: String = "ann@example.com"): HttpResult = register(email = email).assertStatus(201)

    private fun cookieSession(email: String = "ann@example.com"): String =
        register(email = email, transport = "COOKIE").assertStatus(201).refreshCookie

    private fun revokedAtOf(refreshToken: String): Timestamp? =
        jdbcTemplate.queryForObject(
            """
        SELECT s.revoked_at FROM user_service.sessions s
        JOIN user_service.refresh_tokens t ON t.session_id = s.id
        WHERE t.token_hash = ?
        """,
            Timestamp::class.java,
            hashRefreshToken(refreshToken),
        )

    @Test
    fun `refresh with the body transport returns exactly the three token fields`() {
        val first = bodySession().refreshToken
        clock.advanceBy(Duration.ofMinutes(20))

        val result = refreshWithBody(first).assertStatus(200).assertNoCookie()

        val body = result.assertSessionTokens("accessToken", "accessTokenExpiresAt", "refreshToken")
        assertThat(body["refreshToken"]).isNotEqualTo(first)
        assertThat(body["accessTokenExpiresAt"]).isEqualTo("2026-11-15T09:50:30.000Z")
        assertThat(result.contentType).isEqualTo("application/json")
    }

    @Test
    fun `new access token works and the old refresh token no longer does`() {
        val registered = bodySession()

        val refreshed = refreshWithBody(registered.refreshToken).assertStatus(200)

        assertThat(currentUser(refreshed.accessToken).json()["id"]).isEqualTo(registered.userId)
        refreshWithBody(registered.refreshToken).assertInvalidRefreshToken()
    }

    @Test
    fun `refresh with the cookie transport returns two fields and sets a new cookie`() {
        val cookie = cookieSession()

        val result = refreshWithCookie(cookie).assertStatus(200)

        result.assertSessionTokens("accessToken", "accessTokenExpiresAt")
        assertThat(result.body).doesNotContain("refreshToken")
        val newCookie = result.assertRefreshCookie()
        assertThat(newCookie).isNotEqualTo(cookie)
        refreshWithCookie(newCookie).assertStatus(200)
    }

    @Test
    fun `cookie refresh ignores a refresh token in the body`() {
        val cookie = cookieSession("ann@example.com")
        val other = bodySession("bob@example.com").refreshToken

        refreshWithCookie(cookie, bodyToken = other).assertStatus(200)

        refreshWithBody(other).assertStatus(200)
    }

    @Test
    fun `refresh without a transport is 400 and sets no cookie`() {
        val cookie = cookieSession()

        val result = post("refresh", "{}", cookie = cookie)

        result.assertFieldErrors(ErrorCode.VALIDATION_FAILED, fieldError("refreshTokenTransport", "REQUIRED"))
        result.assertNoCookie()
        refreshWithCookie(cookie).assertStatus(200)
    }

    @Test
    fun `refresh with an unknown transport is 400`() {
        post("refresh", json("refreshTokenTransport" to "HEADER", "refreshToken" to "x"))
            .assertFieldErrors(ErrorCode.VALIDATION_FAILED, fieldError("refreshTokenTransport", "INVALID_VALUE"))
    }

    @Test
    fun `body refresh without a token is 401`() {
        refreshWithBody(null).assertInvalidRefreshToken().assertNoCookie()
        post("refresh", json("refreshTokenTransport" to "BODY", "refreshToken" to null)).assertInvalidRefreshToken()
        refreshWithBody("").assertInvalidRefreshToken()
    }

    @Test
    fun `unknown refresh token is 401`() {
        refreshWithBody("a-random-string").assertInvalidRefreshToken().assertNoCookie()
    }

    @Test
    fun `cookie refresh without a cookie is 401, clears the cookie and leaves the body token's session alone`() {
        val bodyToken = bodySession().refreshToken

        val result = refreshWithCookie(cookie = null, bodyToken = bodyToken)

        result.assertInvalidRefreshToken()
        result.assertClearingCookie()
        refreshWithBody(bodyToken).assertStatus(200)
    }

    @Test
    fun `body token of a cookie session is 401 and the cookie still works`() {
        val cookie = cookieSession()

        refreshWithBody(cookie).assertInvalidRefreshToken().assertNoCookie()

        refreshWithCookie(cookie).assertStatus(200)
    }

    @Test
    fun `cookie is not accepted for the body transport`() {
        val cookie = cookieSession()

        val result = refreshWithBody(refreshToken = null, cookie = cookie)

        result.assertInvalidRefreshToken().assertNoCookie()
        assertThat(result.body).doesNotContain("refreshToken")
        refreshWithCookie(cookie).assertStatus(200)
    }

    @Test
    fun `cookie token of a body session is 401 and the body token still works`() {
        val token = bodySession().refreshToken

        refreshWithCookie(token).assertInvalidRefreshToken().assertClearingCookie()

        refreshWithBody(token).assertStatus(200)
    }

    @Test
    fun `expired cookie token is 401 with the clearing cookie`() {
        val cookie = cookieSession()
        clock.advanceBy(Duration.ofDays(30))

        val result = refreshWithCookie(cookie)

        result.assertInvalidRefreshToken()
        result.assertClearingCookie()
    }

    @Test
    fun `reused cookie token is 401 with the clearing cookie and ends the session`() {
        val first = cookieSession()
        val second = refreshWithCookie(first).refreshCookie

        val reuse = refreshWithCookie(first)

        reuse.assertInvalidRefreshToken()
        reuse.assertClearingCookie()
        refreshWithCookie(second).assertInvalidRefreshToken().assertClearingCookie()
    }

    @Test
    fun `failed body refresh with a valid cookie sets no cookie`() {
        val cookie = cookieSession()

        refreshWithBody("unknown-token", cookie = cookie).assertInvalidRefreshToken().assertNoCookie()

        refreshWithCookie(cookie).assertStatus(200)
    }

    @Test
    fun `reuse of a rotated token is persisted across requests`() {
        val first = bodySession().refreshToken
        val second = refreshWithBody(first).refreshToken

        refreshWithBody(first).assertInvalidRefreshToken()

        refreshWithBody(second).assertInvalidRefreshToken()
        assertThat(revokedAtOf(second)).isNotNull()
    }

    @Test
    fun `refresh works 1 ms before the token expires and fails after 31 idle days`() {
        val first = bodySession().refreshToken
        clock.advanceBy(Duration.ofDays(30).minusMillis(1))
        val second = refreshWithBody(first).assertStatus(200).refreshToken

        clock.advanceBy(Duration.ofDays(31))

        refreshWithBody(second).assertInvalidRefreshToken()
    }

    @Test
    fun `session outlives 30 days by refreshing every 29 days`() {
        var token = bodySession().refreshToken

        repeat(2) {
            clock.advanceBy(Duration.ofDays(29))
            token = refreshWithBody(token).assertStatus(200).refreshToken
        }
    }

    @Test
    fun `20 logins one minute apart leave the first session usable`() {
        bodySession()
        val logins =
            (1..20).map {
                clock.advanceBy(Duration.ofMinutes(1))
                login().assertStatus(200).refreshToken
            }

        refreshWithBody(logins.first()).assertStatus(200)
        assertThat(count("sessions")).isEqualTo(21)
    }

    @Test
    fun `session revoked by reuse leaves the other device's session alone`() {
        val deviceA = bodySession().refreshToken
        val deviceB = login().refreshToken
        refreshWithBody(deviceA).assertStatus(200)

        refreshWithBody(deviceA).assertInvalidRefreshToken()

        refreshWithBody(deviceB).assertStatus(200)
    }

    @Test
    fun `logout answers 204 with an empty body and ends the session`() {
        val token = bodySession().refreshToken

        val result = logoutWithBody(token).assertStatus(204).assertNoCookie()

        assertThatBodyIsEmpty(result)
        refreshWithBody(token).assertInvalidRefreshToken()
        assertThat(revokedAtOf(token)?.toInstant()).isEqualTo(clock.instant())
    }

    @Test
    fun `logout works with an expired access token`() {
        val registered = bodySession()
        clock.advanceBy(Duration.ofMinutes(16))
        currentUser(registered.accessToken).assertProblem(ErrorCode.UNAUTHENTICATED)

        logoutWithBody(registered.refreshToken, token = registered.accessToken).assertStatus(204)

        refreshWithBody(registered.refreshToken).assertInvalidRefreshToken()
    }

    @Test
    fun `logout needs no access token and ignores a broken one`() {
        logoutWithBody(bodySession("ann@example.com").refreshToken).assertStatus(204)
        logoutWithBody(bodySession("bob@example.com").refreshToken, token = "garbage").assertStatus(204)
    }

    @Test
    fun `access token keeps working after logout until it expires`() {
        val registered = bodySession()

        logoutWithBody(registered.refreshToken).assertStatus(204)

        currentUser(registered.accessToken).assertStatus(200)
    }

    @Test
    fun `logout twice answers 204 both times`() {
        val token = bodySession().refreshToken

        logoutWithBody(token).assertStatus(204)
        val revokedAt = revokedAtOf(token)
        clock.advanceBy(Duration.ofSeconds(5))
        logoutWithBody(token).assertStatus(204)

        assertThat(revokedAtOf(token)).isEqualTo(revokedAt)
    }

    @Test
    fun `logout with an unknown or missing token answers 204`() {
        val token = bodySession().refreshToken

        assertThatBodyIsEmpty(logoutWithBody("a-random-string").assertStatus(204))
        assertThatBodyIsEmpty(logoutWithBody(null).assertStatus(204).assertNoCookie())

        assertThat(revokedAtOf(token)).isNull()
    }

    @Test
    fun `logout without a transport is 400`() {
        val result = post("logout", "{}", cookie = cookieSession())

        result.assertFieldErrors(ErrorCode.VALIDATION_FAILED, fieldError("refreshTokenTransport", "REQUIRED"))
        result.assertNoCookie()
    }

    @Test
    fun `logout with the wrong transport answers 204 and the session stays active`() {
        val cookie = cookieSession()

        logoutWithBody(cookie).assertStatus(204).assertNoCookie()

        assertThat(revokedAtOf(cookie)).isNull()
        refreshWithCookie(cookie).assertStatus(200)
    }

    @Test
    fun `logout with the current token one second after its expiry changes nothing`() {
        val token = bodySession().refreshToken
        clock.advanceBy(Duration.ofDays(30).plusSeconds(1))

        logoutWithBody(token).assertStatus(204)

        assertThat(revokedAtOf(token)).isNull()
    }

    @Test
    fun `logout with a rotated token ends the session`() {
        val first = bodySession().refreshToken
        val second = refreshWithBody(first).refreshToken

        logoutWithBody(first).assertStatus(204)

        refreshWithBody(second).assertInvalidRefreshToken()
    }

    @Test
    fun `logout with a rotated token past its own expiry ends the session`() {
        val first = bodySession().refreshToken
        clock.advanceBy(Duration.ofDays(29))
        val second = refreshWithBody(first).refreshToken
        clock.advanceBy(Duration.ofDays(2))

        logoutWithBody(first).assertStatus(204)

        refreshWithBody(second).assertInvalidRefreshToken()
    }

    @Test
    fun `cookie logout clears the cookie and ends the session`() {
        val cookie = cookieSession()

        val result = logoutWithCookie(cookie).assertStatus(204)

        result.assertClearingCookie()
        assertThatBodyIsEmpty(result)
        refreshWithCookie(cookie).assertInvalidRefreshToken()
    }

    @Test
    fun `cookie logout without a cookie still clears the cookie`() {
        val bodyToken = bodySession().refreshToken

        val result = logoutWithCookie(cookie = null, bodyToken = bodyToken).assertStatus(204)

        result.assertClearingCookie()
        assertThat(revokedAtOf(bodyToken)).isNull()
    }

    @Test
    fun `body logout ignores the cookie and sets none`() {
        val cookie = cookieSession("ann@example.com")
        val token = bodySession("bob@example.com").refreshToken

        logoutWithBody(token, cookie = cookie).assertStatus(204).assertNoCookie()

        refreshWithCookie(cookie).assertStatus(200)
        refreshWithBody(token).assertInvalidRefreshToken()
    }

    @Test
    fun `logout on web leaves the Android session working`() {
        val web = cookieSession()
        val android = login(transport = "BODY").refreshToken

        logoutWithCookie(web).assertStatus(204)

        refreshWithCookie(web).assertInvalidRefreshToken()
        refreshWithBody(android).assertStatus(200)
    }

    @Test
    fun `refresh token is not accepted as a bearer token`() {
        val token = bodySession().refreshToken

        currentUser(token).assertProblem(ErrorCode.UNAUTHENTICATED)
    }

    @Test
    fun `cookie value has the token format`() {
        assertThat(cookieSession()).matches(Regex("^[A-Za-z0-9_-]{43}$"))
    }
}
