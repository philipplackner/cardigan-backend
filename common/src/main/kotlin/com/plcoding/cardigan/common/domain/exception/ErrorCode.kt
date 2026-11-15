package com.plcoding.cardigan.common.domain.exception

/**
 * The closed catalog of top-level error codes from the shared `api-errors` spec. Every code has a
 * fixed status, title and detail, so no response text depends on the failure or the request.
 */
enum class ErrorCode(
    val status: Int,
    val title: String,
    val detail: String,
) {
    VALIDATION_FAILED(400, "Validation failed", "One or more request fields are invalid. See fieldErrors."),
    MALFORMED_REQUEST(400, "Malformed request", "The request body could not be read."),
    UNAUTHENTICATED(401, "Unauthenticated", "A valid access token is required."),
    INVALID_CREDENTIALS(401, "Invalid credentials", "The email or password is wrong."),
    INVALID_REFRESH_TOKEN(401, "Invalid refresh token", "The refresh token is invalid or expired."),
    NOT_FOUND(404, "Not found", "The requested resource does not exist."),
    ROUTE_NOT_FOUND(404, "Route not found", "No endpoint matches the request method and path."),
    CONFLICT(409, "Conflict", "The request conflicts with existing data. See fieldErrors."),
    PAYLOAD_TOO_LARGE(413, "Payload too large", "The request body exceeds the size limit."),
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported media type", "The request content type is not supported."),
    INVALID_EXPORT_FILE(422, "Invalid export file", "The uploaded file is not a valid export file."),
    RATE_LIMITED(429, "Rate limited", "Too many requests. Try again later."),
    INTERNAL_ERROR(500, "Internal error", "An unexpected error occurred."),
    ;

    val type: String = "urn:cardigan:problem:" + name.lowercase().replace('_', '-')
}
