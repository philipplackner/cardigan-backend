package com.plcoding.cardigan

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.HttpMethod
import java.time.Duration

@ExtendWith(OutputCaptureExtension::class)
class ErrorResponsesE2eTest : E2eTest() {
    @Test
    fun `unknown path with a token is ROUTE_NOT_FOUND and the path is not echoed`() {
        val token = signIn().token

        val body = call(HttpMethod.GET, "/api/v1/zz-marker-zz", token).assertProblem(ErrorCode.ROUTE_NOT_FOUND)

        assertThat(body.fieldErrors).isEmpty()
        assertThat(body.toString()).doesNotContain("zz-marker-zz")
    }

    @Test
    fun `wrong method is ROUTE_NOT_FOUND`() {
        val token = signIn().token

        call(HttpMethod.DELETE, "/api/v1/users/me", token).assertProblem(ErrorCode.ROUTE_NOT_FOUND)
    }

    @Test
    fun `implicit OPTIONS is ROUTE_NOT_FOUND`() {
        val token = signIn().token

        call(HttpMethod.OPTIONS, "/api/v1/users/me", token).assertProblem(ErrorCode.ROUTE_NOT_FOUND)
    }

    @Test
    fun `implicit HEAD is 404`() {
        val token = signIn().token

        val result = call(HttpMethod.HEAD, "/api/v1/users/me", token)

        assertThat(result.status).isEqualTo(404)
        assertThat(result.contentType).isEqualTo("application/problem+json")
    }

    @Test
    fun `path rejected by the firewall is ROUTE_NOT_FOUND, with or without a token`() {
        val token = signIn().token

        raw("GET", "/api/v1/users/me;x=1", token).assertProblem(ErrorCode.ROUTE_NOT_FOUND)
        raw("GET", "/api/v1/users/me;x=1").assertProblem(ErrorCode.ROUTE_NOT_FOUND)
    }

    @Test
    fun `wrong content type is UNSUPPORTED_MEDIA_TYPE and the media type is not echoed`() {
        val token = signIn().token

        val result =
            call(
                HttpMethod.POST,
                "/api/v1/test/echo",
                token,
                json = """{"name":"a","key":"ab"}""",
                headers = mapOf("Content-Type" to "text/zz-marker"),
            )

        result.assertProblem(ErrorCode.UNSUPPORTED_MEDIA_TYPE)
        assertThat(result.body).doesNotContain("zz-marker")
        assertThat(result.headers.getFirst("Accept").orEmpty()).doesNotContain("json")
    }

    @Test
    fun `invalid JSON is MALFORMED_REQUEST with empty fieldErrors`() {
        val token = signIn().token

        val body =
            call(HttpMethod.POST, "/api/v1/test/echo", token, json = """{"name": """)
                .assertProblem(ErrorCode.MALFORMED_REQUEST)

        assertThat(body.fieldErrors).isEmpty()
    }

    @Test
    fun `wrong JSON type is MALFORMED_REQUEST and the detail names nothing from the request`() {
        val token = signIn().token

        val result =
            call(
                HttpMethod.POST,
                "/api/v1/test/echo",
                token,
                json = """{"name":"a","key":"ab","position":"zq9x"}""",
            )

        val detail = result.assertProblem(ErrorCode.MALFORMED_REQUEST)["detail"] as String
        listOf("position", "zq9x", "Exception", "jackson", "kotlin.", "java.").forEach {
            assertThat(detail).doesNotContain(it)
        }
    }

    @Test
    fun `invalid JSON and wrong type have the same detail`() {
        val token = signIn().token

        val invalid = call(HttpMethod.POST, "/api/v1/test/echo", token, json = "{")
        val wrongType =
            call(HttpMethod.POST, "/api/v1/test/echo", token, json = """{"name":"a","key":"ab","position":"x"}""")

        assertThat(invalid.json()["detail"]).isEqualTo(wrongType.json()["detail"])
    }

    @Test
    fun `number for a text field is not coerced`() {
        val token = signIn().token

        call(HttpMethod.POST, "/api/v1/test/echo", token, json = """{"name":5,"key":"ab"}""")
            .assertProblem(ErrorCode.MALFORMED_REQUEST)
    }

    @Test
    fun `fraction for an integer field is not truncated`() {
        val token = signIn().token

        call(HttpMethod.POST, "/api/v1/test/echo", token, json = """{"name":"a","key":"ab","position":1.5}""")
            .assertProblem(ErrorCode.MALFORMED_REQUEST)
    }

    @Test
    fun `malformed path UUID is NOT_FOUND`() {
        val token = signIn().token

        call(HttpMethod.GET, "/api/v1/test/things/abc", token).assertProblem(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `uniqueness conflict carries ALREADY_USED on its field`() {
        val token = signIn().token

        val body = call(HttpMethod.POST, "/api/v1/test/conflict", token).assertProblem(ErrorCode.CONFLICT)

        assertThat(body.fieldErrors).containsExactly(mapOf("field" to "key", "code" to "ALREADY_USED"))
    }

    @Test
    fun `out-of-range field from a service is VALIDATION_FAILED with OUT_OF_RANGE, min and max`() {
        val token = signIn().token

        val result = call(HttpMethod.POST, "/api/v1/test/out-of-range", token)

        assertThat(result.assertProblem(ErrorCode.VALIDATION_FAILED).fieldErrors).containsExactly(
            mapOf("field" to "position", "code" to "OUT_OF_RANGE", "min" to 0, "max" to 2),
        )
        assertThat(result.body).doesNotContain("range-marker")
    }

    @Test
    fun `short-form path UUID is NOT_FOUND`() {
        val token = signIn().token

        call(HttpMethod.GET, "/api/v1/test/things/b6a1f43-58c2-4d0e-9a35-3f1c9e2b7d10", token)
            .assertProblem(ErrorCode.NOT_FOUND)
        call(HttpMethod.GET, "/api/v1/test/things/1-2-3-4-5", token).assertProblem(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `invalid credentials are 401 without a bearer challenge`() {
        val result = call(HttpMethod.POST, "/api/v1/test/invalid-credentials", signIn().token)

        val body = result.assertProblem(ErrorCode.INVALID_CREDENTIALS)
        assertThat(body.fieldErrors).isEmpty()
        assertThat(result.headers["WWW-Authenticate"]).isNull()
        assertThat(result.body).doesNotContain("credentials-marker")
    }

    @Test
    fun `invalid refresh token is 401 without a bearer challenge`() {
        val result = call(HttpMethod.POST, "/api/v1/test/invalid-refresh-token", signIn().token)

        val body = result.assertProblem(ErrorCode.INVALID_REFRESH_TOKEN)
        assertThat(body.fieldErrors).isEmpty()
        assertThat(result.headers["WWW-Authenticate"]).isNull()
        assertThat(result.body).doesNotContain("refresh-marker")
    }

    @Test
    fun `rate limit is 429 with Retry-After in whole seconds`() {
        val token = signIn().token

        val result = call(HttpMethod.POST, "/api/v1/test/rate-limited/12", token)

        val body = result.assertProblem(ErrorCode.RATE_LIMITED)
        assertThat(body.fieldErrors).isEmpty()
        assertThat(result.headers["Retry-After"]).isEqualTo(listOf("12"))
        assertThat(result.headers["WWW-Authenticate"]).isNull()
        assertThat(result.body).doesNotContain("limit-marker")
        assertThat(call(HttpMethod.POST, "/api/v1/test/rate-limited/1", token).headers["Retry-After"])
            .isEqualTo(listOf("1"))
    }

    @Test
    fun `endpoint failure is INTERNAL_ERROR with a fixed detail and a logged stack trace`(output: CapturedOutput) {
        val token = signIn().token

        val result = call(HttpMethod.GET, "/api/v1/test/boom", token)

        result.assertProblem(ErrorCode.INTERNAL_ERROR)
        assertThat(result.body).doesNotContain("boom-marker")
        assertThat(output.all).contains("java.lang.IllegalStateException: boom-marker from the endpoint")
        assertThat(output.all).contains("\tat com.plcoding.cardigan.testapi.TestApiController.boom")
    }

    @Test
    fun `filter failure is INTERNAL_ERROR with a fixed detail and a logged stack trace`(output: CapturedOutput) {
        val token = signIn().token

        val result = call(HttpMethod.GET, "/api/v1/test/boom-filter", token)

        result.assertProblem(ErrorCode.INTERNAL_ERROR)
        assertThat(result.body).doesNotContain("boom-marker")
        assertThat(output.all).contains("java.lang.IllegalStateException: boom-marker from the filter")
        assertThat(output.all).contains("\tat com.plcoding.cardigan.testapi.FailingTestFilter")
    }

    @Test
    fun `missing token, expired token and missing user share one title`() {
        val missingToken = call(HttpMethod.GET, "/api/v1/users/me")
        val missingUser = call(HttpMethod.GET, "/api/v1/users/me", tokenWithoutUser())
        val expiredToken = signIn().token
        clock.advanceBy(Duration.ofMinutes(16))
        val expired = call(HttpMethod.GET, "/api/v1/users/me", expiredToken)

        val titles =
            listOf(
                missingToken,
                missingUser,
                expired,
            ).map { it.assertProblem(ErrorCode.UNAUTHENTICATED)["title"] }

        assertThat(titles.toSet()).isEqualTo(setOf(ErrorCode.UNAUTHENTICATED.title))
    }

    @Test
    fun `direct call to the error path is ROUTE_NOT_FOUND`() {
        call(HttpMethod.GET, "/error").assertProblem(ErrorCode.ROUTE_NOT_FOUND)
    }
}
