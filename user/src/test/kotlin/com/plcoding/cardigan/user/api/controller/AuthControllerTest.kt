package com.plcoding.cardigan.user.api.controller

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import com.plcoding.cardigan.common.api.ProblemFieldError
import com.plcoding.cardigan.common.api.validation.RequestValidationException
import com.plcoding.cardigan.common.api.validation.RequestValidator
import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import com.plcoding.cardigan.common.domain.exception.InvalidRefreshTokenException
import com.plcoding.cardigan.user.api.dto.AuthSessionDto
import com.plcoding.cardigan.user.api.dto.AuthTokensDto
import com.plcoding.cardigan.user.api.dto.CurrentUserDto
import com.plcoding.cardigan.user.api.dto.LoginRequest
import com.plcoding.cardigan.user.api.dto.LogoutRequest
import com.plcoding.cardigan.user.api.dto.RefreshRequest
import com.plcoding.cardigan.user.api.dto.RegisterRequest
import com.plcoding.cardigan.user.domain.exception.AuthRateLimitedException
import com.plcoding.cardigan.user.domain.model.AuthAction
import com.plcoding.cardigan.user.domain.model.RefreshTokenTransport.BODY
import com.plcoding.cardigan.user.domain.model.RefreshTokenTransport.COOKIE
import com.plcoding.cardigan.user.service.AuthServiceTestBase
import jakarta.servlet.http.Cookie
import jakarta.validation.Validation
import jakarta.validation.ValidatorFactory
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockCookie
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Duration

class AuthControllerTest : AuthServiceTestBase() {

    private val controller = AuthController(service, RequestValidator(validatorFactory.validator), RefreshTokenCookie(clock))

    private val response = MockHttpServletResponse()

    private val thirtyDays = Duration.ofDays(30)

    private fun registerRequest(email: String? = "Ann@Example.com", transport: String? = "BODY") =
        RegisterRequest(email, password, transport)

    private fun refreshCookie(): Cookie? = response.getCookie(RefreshTokenCookie.NAME)

    private fun assertCookieCarries(value: String, maxAge: Duration) {
        val cookie = refreshCookie() as MockCookie
        assertThat(cookie.value).isEqualTo(value)
        assertThat(cookie.maxAge).isEqualTo(maxAge.toSeconds().toInt())
        assertThat(cookie.path).isEqualTo("/api/v1/auth")
        assertThat(cookie.secure).isTrue()
        assertThat(cookie.isHttpOnly).isTrue()
        assertThat(cookie.sameSite).isEqualTo("Strict")
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE).size).isEqualTo(1)
    }

    private fun assertNoCookie() {
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty()
    }

    @Test
    fun `register with BODY returns every session field and the refresh token in the body`() {
        val session = controller.register(registerRequest(), response)

        val user = users.findByEmail("ann@example.com")!!
        val issued = accessTokens.issued.single().second
        assertThat(session).all {
            prop(AuthSessionDto::accessToken).isEqualTo(issued.token)
            prop(AuthSessionDto::accessTokenExpiresAt).isEqualTo(issued.expiresAt)
            prop(AuthSessionDto::refreshToken).isEqualTo(generator.generated.single())
            prop(AuthSessionDto::user).all {
                prop(CurrentUserDto::id).isEqualTo(user.id)
                prop(CurrentUserDto::email).isEqualTo("ann@example.com")
                prop(CurrentUserDto::createdAt).isEqualTo(start)
            }
        }
        assertNoCookie()
    }

    @Test
    fun `register with COOKIE sets the refresh cookie and leaves the token out of the body`() {
        val session = controller.register(registerRequest(transport = "COOKIE"), response)

        assertThat(session.refreshToken).isNull()
        assertThat(session.accessToken).isEqualTo(accessTokens.issued.single().second.token)
        assertCookieCarries(generator.generated.single(), thirtyDays)
    }

    @Test
    fun `register checks the rate limit before the fields`() {
        rateLimiter.rejectWithRetryAfter = 7

        assertFailure { controller.register(RegisterRequest(" Ann@Example.com", null, null), response) }
            .isInstanceOf(AuthRateLimitedException::class)
            .prop(AuthRateLimitedException::retryAfterSeconds).isEqualTo(7)
        assertThat(rateLimiter.checks).containsExactly(AuthAction.REGISTER to "ann@example.com")
    }

    @Test
    fun `register under the limit reports field errors and stores nothing`() {
        assertFailure { controller.register(RegisterRequest("Ann@Example.com", null, "BODY"), response) }
            .isInstanceOf(RequestValidationException::class)
            .prop(RequestValidationException::fieldErrors)
            .containsExactly(ProblemFieldError("password", FieldErrorCode.REQUIRED))
        assertThat(rateLimiter.checks).containsExactly(AuthAction.REGISTER to "ann@example.com")
        assertThat(users.users).isEqualTo(emptyMap())
    }

    @Test
    fun `login with BODY returns every session field and the refresh token in the body`() {
        val registered = register()

        val session = controller.login(LoginRequest("ANN@example.com", password, "BODY"), response)

        val issued = accessTokens.issued.last().second
        assertThat(session).all {
            prop(AuthSessionDto::accessToken).isEqualTo(issued.token)
            prop(AuthSessionDto::accessTokenExpiresAt).isEqualTo(issued.expiresAt)
            prop(AuthSessionDto::refreshToken).isEqualTo(generator.generated.last())
            prop(AuthSessionDto::user).all {
                prop(CurrentUserDto::id).isEqualTo(registered.user.id)
                prop(CurrentUserDto::email).isEqualTo("ann@example.com")
                prop(CurrentUserDto::createdAt).isEqualTo(start)
            }
        }
        assertNoCookie()
    }

    @Test
    fun `login with COOKIE sets the refresh cookie and leaves the token out of the body`() {
        register()
        clock.advanceBy(Duration.ofHours(2))

        val session = controller.login(LoginRequest("ann@example.com", password, "COOKIE"), response)

        assertThat(session.refreshToken).isNull()
        assertCookieCarries(generator.generated.last(), thirtyDays)
    }

    @Test
    fun `login checks the rate limit before the fields`() {
        rateLimiter.rejectWithRetryAfter = 3

        assertFailure { controller.login(LoginRequest("Ann@Example.com", "", null), response) }
            .isInstanceOf(AuthRateLimitedException::class)
        assertThat(rateLimiter.checks).containsExactly(AuthAction.LOGIN to "ann@example.com")
    }

    @Test
    fun `login under the limit reports field errors before checking credentials`() {
        assertFailure { controller.login(LoginRequest("ann@example.com", "", "BODY"), response) }
            .isInstanceOf(RequestValidationException::class)
            .prop(RequestValidationException::fieldErrors)
            .containsExactly(ProblemFieldError("password", FieldErrorCode.REQUIRED))
        assertThat(hasher.verified).isEmpty()
    }

    @Test
    fun `refresh with BODY reads the body token and returns every token field`() {
        val first = register().tokens.refreshToken

        val tokens = controller.refresh(RefreshRequest("BODY", first), cookie = "ignored", response)

        val issued = accessTokens.issued.last().second
        assertThat(tokens).all {
            prop(AuthTokensDto::accessToken).isEqualTo(issued.token)
            prop(AuthTokensDto::accessTokenExpiresAt).isEqualTo(issued.expiresAt)
            prop(AuthTokensDto::refreshToken).isEqualTo(generator.generated.last())
        }
        assertThat(stored(first).rotatedAt).isEqualTo(start)
        assertNoCookie()
    }

    @Test
    fun `refresh with COOKIE reads the cookie, ignores the body token and sets the new cookie`() {
        val first = register(transport = COOKIE).tokens.refreshToken
        clock.advanceBy(Duration.ofDays(1))

        val tokens = controller.refresh(RefreshRequest("COOKIE", "not-the-token"), cookie = first, response)

        assertThat(tokens.refreshToken).isNull()
        assertThat(tokens.accessToken).isEqualTo(accessTokens.issued.last().second.token)
        assertCookieCarries(generator.generated.last(), thirtyDays)
    }

    @Test
    fun `refresh with COOKIE doesn't take the token from the body`() {
        val first = register(transport = COOKIE).tokens.refreshToken

        assertFailure { controller.refresh(RefreshRequest("COOKIE", first), cookie = null, response) }
            .isInstanceOf(InvalidRefreshTokenException::class)
        assertThat(stored(first).rotatedAt).isNull()
    }

    @Test
    fun `refresh with BODY doesn't take the token from the cookie`() {
        val first = register().tokens.refreshToken

        assertFailure { controller.refresh(RefreshRequest("BODY", null), cookie = first, response) }
            .isInstanceOf(InvalidRefreshTokenException::class)
        assertThat(stored(first).rotatedAt).isNull()
    }

    @Test
    fun `failed COOKIE refresh clears the cookie`() {
        assertFailure { controller.refresh(RefreshRequest("COOKIE", null), cookie = "unknown", response) }
            .isInstanceOf(InvalidRefreshTokenException::class)

        assertCookieCarries("", Duration.ZERO)
    }

    @Test
    fun `failed BODY refresh sets no cookie`() {
        assertFailure { controller.refresh(RefreshRequest("BODY", "unknown"), cookie = null, response) }
            .isInstanceOf(InvalidRefreshTokenException::class)

        assertNoCookie()
    }

    @Test
    fun `logout with BODY ends the session of the body token and sets no cookie`() {
        val token = register().tokens.refreshToken

        controller.logout(LogoutRequest("BODY", token), cookie = "ignored", response)

        assertThat(sessionOf(token).revokedAt).isEqualTo(start)
        assertNoCookie()
    }

    @Test
    fun `logout with COOKIE ends the session of the cookie token and clears the cookie`() {
        val token = register(transport = COOKIE).tokens.refreshToken

        controller.logout(LogoutRequest("COOKIE", "not-the-token"), cookie = token, response)

        assertThat(sessionOf(token).revokedAt).isEqualTo(start)
        assertCookieCarries("", Duration.ZERO)
    }

    @Test
    fun `logout with COOKIE and no cookie still clears it`() {
        val token = register(transport = COOKIE).tokens.refreshToken

        controller.logout(LogoutRequest("COOKIE", token), cookie = null, response)

        assertThat(sessionOf(token).revokedAt).isNull()
        assertCookieCarries("", Duration.ZERO)
    }

    private companion object {
        val validatorFactory: ValidatorFactory = Validation.buildDefaultValidatorFactory()

        @JvmStatic
        @AfterAll
        fun close() = validatorFactory.close()
    }
}
