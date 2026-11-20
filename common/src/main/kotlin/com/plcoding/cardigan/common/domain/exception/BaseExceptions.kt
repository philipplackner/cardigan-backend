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

/**
 * The integer in [field] lies outside a range that depends on stored data, so a request DTO
 * constraint can't check it. Answered with 400 `VALIDATION_FAILED` and the field error
 * `OUT_OF_RANGE` with [min] and [max] on [field], given as the JSON path in the request body.
 */
open class FieldOutOfRangeException(
    val field: String,
    val min: Long,
    val max: Long,
    message: String? = null,
) : RuntimeException(message)
