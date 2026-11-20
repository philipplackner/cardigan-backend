package com.plcoding.cardigan.user.api.controller

import com.plcoding.cardigan.common.api.openapi.ApiErrors
import com.plcoding.cardigan.common.api.validation.RequestValidator
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import com.plcoding.cardigan.common.domain.exception.InvalidRefreshTokenException
import com.plcoding.cardigan.user.api.dto.AuthSessionDto
import com.plcoding.cardigan.user.api.dto.AuthTokensDto
import com.plcoding.cardigan.user.api.dto.LoginRequest
import com.plcoding.cardigan.user.api.dto.LogoutRequest
import com.plcoding.cardigan.user.api.dto.RefreshRequest
import com.plcoding.cardigan.user.api.dto.RegisterRequest
import com.plcoding.cardigan.user.api.mapper.toAuthSessionDto
import com.plcoding.cardigan.user.api.mapper.toAuthTokensDto
import com.plcoding.cardigan.user.api.mapper.toCredentials
import com.plcoding.cardigan.user.api.mapper.toRefreshTokenTransport
import com.plcoding.cardigan.user.domain.model.AuthAction
import com.plcoding.cardigan.user.domain.model.RefreshResult
import com.plcoding.cardigan.user.domain.model.RefreshTokenTransport
import com.plcoding.cardigan.user.service.AuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
internal class AuthController(
    private val authService: AuthService,
    private val requestValidator: RequestValidator,
    private val refreshTokenCookie: RefreshTokenCookie,
) {

    // No @Valid on register and login: the rate limit is checked first, so 429 wins over 400.
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        operationId = "register",
        summary = "Create an account and start a session",
        description = "With the `COOKIE` transport the refresh token is set in the `$COOKIE_NAME` cookie and left " +
            "out of the body. With `BODY` it is in the body.",
    )
    @ApiErrors(400, ErrorCode.VALIDATION_FAILED)
    @ApiErrors(409, ErrorCode.CONFLICT)
    @ApiErrors(429, ErrorCode.RATE_LIMITED)
    fun register(@RequestBody request: RegisterRequest, response: HttpServletResponse): AuthSessionDto {
        authService.checkRateLimit(AuthAction.REGISTER, request.email)
        requestValidator.validate(request)
        val session = authService.register(request.toCredentials())
        refreshTokenCookie.setFor(session.tokens, response)
        return session.toAuthSessionDto()
    }

    @PostMapping("/login")
    @Operation(
        operationId = "login",
        summary = "Start a session with an email and a password",
        description = "With the `COOKIE` transport the refresh token is set in the `$COOKIE_NAME` cookie and left " +
            "out of the body. With `BODY` it is in the body.",
    )
    @ApiErrors(400, ErrorCode.VALIDATION_FAILED)
    @ApiErrors(401, ErrorCode.INVALID_CREDENTIALS)
    @ApiErrors(429, ErrorCode.RATE_LIMITED)
    fun login(@RequestBody request: LoginRequest, response: HttpServletResponse): AuthSessionDto {
        authService.checkRateLimit(AuthAction.LOGIN, request.email)
        requestValidator.validate(request)
        val session = authService.login(request.toCredentials())
        refreshTokenCookie.setFor(session.tokens, response)
        return session.toAuthSessionDto()
    }

    @PostMapping("/refresh")
    @Operation(
        operationId = "refresh",
        summary = "Exchange the refresh token for new tokens",
        description = "With the `COOKIE` transport the refresh token is read from the `$COOKIE_NAME` cookie, " +
            "`refreshToken` in the body is ignored, and the new refresh token is set in that cookie. A failed " +
            "refresh clears the cookie. With `BODY` the refresh token is read from and returned in the body.",
    )
    @ApiErrors(400, ErrorCode.VALIDATION_FAILED)
    @ApiErrors(401, ErrorCode.INVALID_REFRESH_TOKEN)
    fun refresh(
        @Valid @RequestBody request: RefreshRequest,
        @Parameter(hidden = true) @CookieValue(name = COOKIE_NAME, required = false) cookie: String?,
        response: HttpServletResponse,
    ): AuthTokensDto {
        val transport = request.toRefreshTokenTransport()
        return when (val result = authService.refresh(transport, tokenFor(transport, request.refreshToken, cookie))) {
            is RefreshResult.Refreshed -> {
                refreshTokenCookie.setFor(result.tokens, response)
                result.tokens.toAuthTokensDto()
            }
            RefreshResult.Rejected -> {
                if (transport == RefreshTokenTransport.COOKIE) refreshTokenCookie.clear(response)
                throw InvalidRefreshTokenException()
            }
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "logout",
        summary = "End the session of the refresh token",
        description = "Always answers 204 for a valid request, whether or not a session was ended. With the " +
            "`COOKIE` transport the refresh token is read from the `$COOKIE_NAME` cookie, `refreshToken` in the " +
            "body is ignored, and the cookie is cleared. With `BODY` the refresh token is read from the body.",
    )
    @ApiErrors(400, ErrorCode.VALIDATION_FAILED)
    fun logout(
        @Valid @RequestBody request: LogoutRequest,
        @Parameter(hidden = true) @CookieValue(name = COOKIE_NAME, required = false) cookie: String?,
        response: HttpServletResponse,
    ) {
        val transport = request.toRefreshTokenTransport()
        authService.logout(transport, tokenFor(transport, request.refreshToken, cookie))
        if (transport == RefreshTokenTransport.COOKIE) refreshTokenCookie.clear(response)
    }

    private fun tokenFor(transport: RefreshTokenTransport, bodyToken: String?, cookieToken: String?): String? =
        when (transport) {
            RefreshTokenTransport.COOKIE -> cookieToken
            RefreshTokenTransport.BODY -> bodyToken
        }

    private companion object {
        const val COOKIE_NAME = RefreshTokenCookie.NAME
    }
}
