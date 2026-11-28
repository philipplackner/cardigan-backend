package com.plcoding.cardigan.common.domain.security

import java.time.Instant
import java.util.UUID

/** Issues access tokens for users. */
interface AccessTokenIssuer {
    fun issue(userId: UUID): IssuedAccessToken
}

/** An access token and its expiry, which always has whole seconds and equals the token's `exp`. */
data class IssuedAccessToken(
    val token: String,
    val expiresAt: Instant,
)
