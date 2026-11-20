package com.plcoding.cardigan.user.api.controller

import com.plcoding.cardigan.user.domain.model.IssuedTokens
import com.plcoding.cardigan.user.domain.model.RefreshTokenTransport
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

/**
 * Writes the refresh cookie of the `COOKIE` transport. `Secure` is always set, whatever scheme the
 * backend sees, and the path limits the cookie to the auth endpoints.
 */
@Component
internal class RefreshTokenCookie(
    private val clock: Clock,
) {

    /** Sets the cookie when the session uses the `COOKIE` transport. It expires with the token. */
    fun setFor(tokens: IssuedTokens, response: HttpServletResponse) {
        if (tokens.transport != RefreshTokenTransport.COOKIE) return
        val maxAge = Duration.between(clock.instant(), tokens.refreshTokenExpiresAt)
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(tokens.refreshToken, maxAge).toString())
    }

    fun clear(response: HttpServletResponse) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
    }

    private fun cookie(value: String, maxAge: Duration): ResponseCookie =
        ResponseCookie.from(NAME, value)
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path(PATH)
            .maxAge(maxAge)
            .build()

    companion object {
        const val NAME = "cardigan_refresh_token"
        const val PATH = "/api/v1/auth"
    }
}
