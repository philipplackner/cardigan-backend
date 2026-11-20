package com.plcoding.cardigan.user.domain.model

import java.time.Instant
import java.util.UUID

/**
 * One sign-in of a user on one client. It starts with register or login and ends when it is
 * revoked, by logout or by reuse of a rotated refresh token, or when its newest refresh token expires.
 */
data class Session(
    val id: UUID,
    val userId: UUID,
    val transport: RefreshTokenTransport,
    val createdAt: Instant,
    val revokedAt: Instant? = null,
) {

    val isRevoked: Boolean get() = revokedAt != null
}
