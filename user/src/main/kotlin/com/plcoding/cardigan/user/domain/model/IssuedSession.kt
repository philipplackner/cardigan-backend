package com.plcoding.cardigan.user.domain.model

import java.time.Instant

/** A new refresh token in the only place its value exists outside the client. */
data class IssuedRefreshToken(
    val value: String,
    val expiresAt: Instant,
) {
    override fun toString(): String = "IssuedRefreshToken(expiresAt=$expiresAt)"
}

/** The tokens a client gets when a session starts or is refreshed. */
data class IssuedTokens(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
    val transport: RefreshTokenTransport,
) {
    override fun toString(): String =
        "IssuedTokens(accessTokenExpiresAt=$accessTokenExpiresAt, refreshTokenExpiresAt=$refreshTokenExpiresAt, " +
            "transport=$transport)"
}

/** The result of register and login: the user and the tokens of the session that was started. */
data class IssuedSession(
    val user: User,
    val tokens: IssuedTokens,
) {
    override fun toString(): String = "IssuedSession(user=$user, tokens=$tokens)"
}

/**
 * The result of a refresh. A rejected refresh is a result and not an exception, so that the
 * revocation after token reuse is committed with the transaction.
 */
sealed interface RefreshResult {
    data class Refreshed(
        val tokens: IssuedTokens,
    ) : RefreshResult

    data object Rejected : RefreshResult
}
