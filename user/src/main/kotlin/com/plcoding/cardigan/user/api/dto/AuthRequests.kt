package com.plcoding.cardigan.user.api.dto

import com.plcoding.cardigan.common.api.validation.Required
import com.plcoding.cardigan.common.api.validation.ValidEnum
import com.plcoding.cardigan.common.api.validation.ValidText
import com.plcoding.cardigan.user.api.validation.NotBlankText
import com.plcoding.cardigan.user.api.validation.NotEmptyPassword
import com.plcoding.cardigan.user.api.validation.ValidEmail
import com.plcoding.cardigan.user.api.validation.ValidPassword
import com.plcoding.cardigan.user.domain.model.EMAIL_MAX_LENGTH

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
    val refreshTokenTransport: String?,
) {

    override fun toString(): String = "RegisterRequest(email=$email, refreshTokenTransport=$refreshTokenTransport)"
}

/** Login checks only that the fields are there. Any other email or password is a wrong credential. */
data class LoginRequest(
    @Required
    @NotBlankText
    val email: String?,
    @Required
    @NotEmptyPassword
    val password: String?,
    @Required
    @ValidEnum(RefreshTokenTransportDto::class)
    val refreshTokenTransport: String?,
) {

    override fun toString(): String = "LoginRequest(email=$email, refreshTokenTransport=$refreshTokenTransport)"
}

data class RefreshRequest(
    @Required
    @ValidEnum(RefreshTokenTransportDto::class)
    val refreshTokenTransport: String?,
    val refreshToken: String?,
) {

    override fun toString(): String = "RefreshRequest(refreshTokenTransport=$refreshTokenTransport)"
}

data class LogoutRequest(
    @Required
    @ValidEnum(RefreshTokenTransportDto::class)
    val refreshTokenTransport: String?,
    val refreshToken: String?,
) {

    override fun toString(): String = "LogoutRequest(refreshTokenTransport=$refreshTokenTransport)"
}
