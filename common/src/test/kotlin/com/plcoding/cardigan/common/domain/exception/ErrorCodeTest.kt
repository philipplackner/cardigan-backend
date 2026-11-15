package com.plcoding.cardigan.common.domain.exception

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class ErrorCodeTest {

    @Test
    fun `catalog matches the shared spec with its statuses`() {
        assertThat(ErrorCode.entries.map { it.name to it.status }).containsExactly(
            "VALIDATION_FAILED" to 400,
            "MALFORMED_REQUEST" to 400,
            "UNAUTHENTICATED" to 401,
            "INVALID_CREDENTIALS" to 401,
            "INVALID_REFRESH_TOKEN" to 401,
            "NOT_FOUND" to 404,
            "ROUTE_NOT_FOUND" to 404,
            "CONFLICT" to 409,
            "PAYLOAD_TOO_LARGE" to 413,
            "UNSUPPORTED_MEDIA_TYPE" to 415,
            "INVALID_EXPORT_FILE" to 422,
            "RATE_LIMITED" to 429,
            "INTERNAL_ERROR" to 500,
        )
    }

    @Test
    fun `type is a kebab-case urn for every code`() {
        assertThat(ErrorCode.entries.map { it.type }).containsExactly(
            "urn:cardigan:problem:validation-failed",
            "urn:cardigan:problem:malformed-request",
            "urn:cardigan:problem:unauthenticated",
            "urn:cardigan:problem:invalid-credentials",
            "urn:cardigan:problem:invalid-refresh-token",
            "urn:cardigan:problem:not-found",
            "urn:cardigan:problem:route-not-found",
            "urn:cardigan:problem:conflict",
            "urn:cardigan:problem:payload-too-large",
            "urn:cardigan:problem:unsupported-media-type",
            "urn:cardigan:problem:invalid-export-file",
            "urn:cardigan:problem:rate-limited",
            "urn:cardigan:problem:internal-error",
        )
    }

    @Test
    fun `titles and details are distinct per code`() {
        assertThat(ErrorCode.entries.map { it.title }.toSet().size).isEqualTo(ErrorCode.entries.size)
        assertThat(ErrorCode.entries.map { it.detail }.toSet().size).isEqualTo(ErrorCode.entries.size)
    }
}
