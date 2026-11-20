package com.plcoding.cardigan.user.domain.model

/** How a session's refresh token travels. Fixed when the session starts. */
enum class RefreshTokenTransport {
    COOKIE,
    BODY,
}
