package com.plcoding.cardigan.common.domain.exception

/** A referenced resource doesn't exist or belongs to another user. Answered with 404 `NOT_FOUND`. */
open class ResourceNotFoundException(message: String? = null) : RuntimeException(message)

/**
 * A request would break a uniqueness rule on [field]. Answered with 409 `CONFLICT` and the field
 * error `ALREADY_USED` on [field], given as the JSON path in the request body.
 */
open class UniquenessConflictException(
    val field: String,
    message: String? = null,
) : RuntimeException(message)

/** The request has no valid identity. Answered with 401 `UNAUTHENTICATED`. */
open class UnauthenticatedException(message: String? = null) : RuntimeException(message)

/** The email or password of a login is wrong. Answered with 401 `INVALID_CREDENTIALS`, with no bearer challenge. */
open class InvalidCredentialsException(message: String? = null) : RuntimeException(message)

/** A refresh token is missing, unknown, expired or revoked. Answered with 401 `INVALID_REFRESH_TOKEN`, with no bearer challenge. */
open class InvalidRefreshTokenException(message: String? = null) : RuntimeException(message)

/**
 * A rate limit is used up. Answered with 429 `RATE_LIMITED` and a `Retry-After` header of
 * [retryAfterSeconds], which is at least 1.
 */
open class RateLimitedException(
    val retryAfterSeconds: Long,
    message: String? = null,
) : RuntimeException(message) {

    init {
        require(retryAfterSeconds >= 1) { "retryAfterSeconds must be at least 1" }
    }
}
