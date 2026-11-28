package com.plcoding.cardigan.user.api.dto

import com.plcoding.cardigan.common.api.validation.Required
import com.plcoding.cardigan.common.api.validation.ValidEnum
import com.plcoding.cardigan.common.api.validation.ValidText
import com.plcoding.cardigan.user.api.validation.NotEmptyPassword
import com.plcoding.cardigan.user.api.validation.ValidEmail
import com.plcoding.cardigan.user.api.validation.ValidPassword
import com.plcoding.cardigan.user.domain.model.EMAIL_MAX_LENGTH
import io.swagger.v3.oas.annotations.media.Schema

data class RegisterRequest(
    @Required
    @ValidText(min = 1, max = EMAIL_MAX_LENGTH)
    @ValidEmail
    val email: String?,
    @Required
    @ValidPassword
    val password: String?,
    @Required
    @ValidEnum(RefreshTokenTransportDto::class)
    @field:Schema(implementation = RefreshTokenTransportDto::class)
    val refreshTokenTransport: String?,
) {
    override fun toString(): String = "RegisterRequest(email=$email, refreshTokenTransport=$refreshTokenTransport)"
}

/**
 * Login checks that the fields are there and that the email has no forbidden character and isn't too
 * long. It never checks the email format or the password rules: anything else is a wrong credential.
 */
data class LoginRequest(
    @Required
    @ValidText(min = 1, max = EMAIL_MAX_LENGTH)
    val email: String?,
    @Required
    @NotEmptyPassword
    val password: String?,
    @Required
    @ValidEnum(RefreshTokenTransportDto::class)
    @field:Schema(implementation = RefreshTokenTransportDto::class)
    val refreshTokenTransport: String?,
) {
    override fun toString(): String = "LoginRequest(email=$email, refreshTokenTransport=$refreshTokenTransport)"
}

data class RefreshRequest(
    @Required
    @ValidEnum(RefreshTokenTransportDto::class)
    @field:Schema(implementation = RefreshTokenTransportDto::class)
    val refreshTokenTransport: String?,
    val refreshToken: String?,
) {
    override fun toString(): String = "RefreshRequest(refreshTokenTransport=$refreshTokenTransport)"
}

data class LogoutRequest(
    @Required
    @ValidEnum(RefreshTokenTransportDto::class)
    @field:Schema(implementation = RefreshTokenTransportDto::class)
    val refreshTokenTransport: String?,
    val refreshToken: String?,
) {
    override fun toString(): String = "LogoutRequest(refreshTokenTransport=$refreshTokenTransport)"
}
