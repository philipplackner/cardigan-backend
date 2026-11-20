package com.plcoding.cardigan.user.api.dto

import io.swagger.v3.oas.annotations.media.Schema

/** Where the refresh token travels: in an `HttpOnly` cookie (web) or in the body (mobile). */
@Schema(enumAsRef = true)
enum class RefreshTokenTransportDto {
    COOKIE,
    BODY,
}
