package com.plcoding.cardigan.user.api.dto

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

/**
 * The answer to register and login. [refreshToken] is there only with the `BODY` transport; with
 * `COOKIE` the property is left out, not `null`.
 */
data class AuthSessionDto(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    @field:JsonInclude(JsonInclude.Include.NON_NULL)
    val refreshToken: String?,
    val user: CurrentUserDto,
) {

    override fun toString(): String = "AuthSessionDto(accessTokenExpiresAt=$accessTokenExpiresAt, user=$user)"
}

/** The answer to refresh. [refreshToken] follows the same rule as in [AuthSessionDto]. */
data class AuthTokensDto(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    @field:JsonInclude(JsonInclude.Include.NON_NULL)
    val refreshToken: String?,
) {

    override fun toString(): String = "AuthTokensDto(accessTokenExpiresAt=$accessTokenExpiresAt)"
}
