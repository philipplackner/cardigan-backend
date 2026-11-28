package com.plcoding.cardigan.user.api.mapper

import com.plcoding.cardigan.common.domain.text.normalizePassword
import com.plcoding.cardigan.user.api.dto.AuthSessionDto
import com.plcoding.cardigan.user.api.dto.AuthTokensDto
import com.plcoding.cardigan.user.api.dto.LoginRequest
import com.plcoding.cardigan.user.api.dto.LogoutRequest
import com.plcoding.cardigan.user.api.dto.RefreshRequest
import com.plcoding.cardigan.user.api.dto.RegisterRequest
import com.plcoding.cardigan.user.domain.model.Credentials
import com.plcoding.cardigan.user.domain.model.IssuedSession
import com.plcoding.cardigan.user.domain.model.IssuedTokens
import com.plcoding.cardigan.user.domain.model.RefreshTokenTransport
import com.plcoding.cardigan.user.domain.model.normalizeEmail

fun RegisterRequest.toCredentials(): Credentials = credentials(email, password, refreshTokenTransport)

fun LoginRequest.toCredentials(): Credentials = credentials(email, password, refreshTokenTransport)

fun RefreshRequest.toRefreshTokenTransport(): RefreshTokenTransport = transport(refreshTokenTransport)

fun LogoutRequest.toRefreshTokenTransport(): RefreshTokenTransport = transport(refreshTokenTransport)

fun IssuedSession.toAuthSessionDto(): AuthSessionDto =
    AuthSessionDto(
        accessToken = tokens.accessToken,
        accessTokenExpiresAt = tokens.accessTokenExpiresAt,
        refreshToken = tokens.bodyRefreshToken(),
        user = user.toCurrentUserDto(),
    )

fun IssuedTokens.toAuthTokensDto(): AuthTokensDto =
    AuthTokensDto(
        accessToken = accessToken,
        accessTokenExpiresAt = accessTokenExpiresAt,
        refreshToken = bodyRefreshToken(),
    )

private fun IssuedTokens.bodyRefreshToken(): String? = refreshToken.takeIf { transport == RefreshTokenTransport.BODY }

private fun credentials(
    email: String?,
    password: String?,
    refreshTokenTransport: String?,
) = Credentials(
    email = normalizeEmail(validated(email)),
    password = normalizePassword(validated(password)),
    transport = transport(refreshTokenTransport),
)

private fun transport(refreshTokenTransport: String?): RefreshTokenTransport =
    RefreshTokenTransport.valueOf(validated(refreshTokenTransport))

private fun validated(value: String?): String = checkNotNull(value) { "The request was mapped before it was validated" }
