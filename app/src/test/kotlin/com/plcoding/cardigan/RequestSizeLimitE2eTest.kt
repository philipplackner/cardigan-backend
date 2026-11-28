package com.plcoding.cardigan

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotIn
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import com.plcoding.cardigan.testapi.TestApiController
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.web.filter.FormContentFilter
import org.springframework.web.multipart.MultipartResolver

class RequestSizeLimitE2eTest : E2eTest() {
    @Autowired
    private lateinit var context: ApplicationContext

    private val json = mapOf("Content-Type" to "application/json")

    /** A valid echo request of exactly [size] bytes, padded with an ignored field. */
    private fun echoBody(size: Int): ByteArray {
        val prefix = """{"name":"a","key":"ab","pad":""""
        val suffix = "\"}"
        return (prefix + "x".repeat(size - prefix.length - suffix.length) + suffix).toByteArray()
    }

    @Test
    fun `body of exactly 256 KB is accepted`() {
        val token = signIn().token

        val result = raw("POST", "/api/v1/test/echo", token, echoBody(262_144), headers = json)

        assertThat(result.status).isEqualTo(200)
    }

    @Test
    fun `body one byte over 256 KB is 413`() {
        val token = signIn().token

        raw("POST", "/api/v1/test/echo", token, echoBody(262_145), headers = json)
            .assertProblem(ErrorCode.PAYLOAD_TOO_LARGE)
    }

    @Test
    fun `chunked body of exactly 256 KB is accepted`() {
        val token = signIn().token

        val result = raw("POST", "/api/v1/test/echo", token, echoBody(262_144), chunked = true, headers = json)

        assertThat(result.status).isEqualTo(200)
    }

    @Test
    fun `chunked body one byte over 256 KB is 413`() {
        val token = signIn().token

        raw("POST", "/api/v1/test/echo", token, echoBody(262_145), chunked = true, headers = json)
            .assertProblem(ErrorCode.PAYLOAD_TOO_LARGE)
    }

    @Test
    fun `Content-Length 307200 is 413 on any API path`() {
        val token = signIn().token

        listOf("/api/v1/test/echo", "/api/v1/users/me", "/api/v1/does-not-exist").forEach { path ->
            val body =
                raw("POST", path, token, ByteArray(307_200), headers = json)
                    .assertProblem(ErrorCode.PAYLOAD_TOO_LARGE)
            assertThat(body.fieldErrors).isEmpty()
        }
    }

    @Test
    fun `300 KB chunked JSON to an endpoint that reads it is 413`() {
        val token = signIn().token

        raw("POST", "/api/v1/test/echo", token, echoBody(307_200), chunked = true, headers = json)
            .assertProblem(ErrorCode.PAYLOAD_TOO_LARGE)
    }

    @Test
    fun `missing token wins over a large body`() {
        raw("POST", "/api/v1/test/echo", body = ByteArray(307_200), headers = json)
            .assertProblem(ErrorCode.UNAUTHENTICATED)
        raw("POST", "/api/v1/test/echo", body = echoBody(307_200), chunked = true, headers = json)
            .assertProblem(ErrorCode.UNAUTHENTICATED)
    }

    @Test
    fun `pinned - chunked oversized body with a wrong media type is 415, not 413`() {
        val token = signIn().token

        raw(
            "POST",
            "/api/v1/test/echo",
            token,
            ByteArray(307_200),
            chunked = true,
            headers =
                mapOf("Content-Type" to "text/plain"),
        ).assertProblem(ErrorCode.UNSUPPORTED_MEDIA_TYPE)
    }

    @Test
    fun `pinned - chunked oversized body to an endpoint that reads no body gets the normal response`() {
        val token = signIn().token

        val result = raw("POST", "/api/v1/test/no-body", token, ByteArray(307_200), chunked = true, headers = json)

        assertThat(result.status).isEqualTo(200)
    }

    @Test
    fun `chunked form PUT without a token is 401`() {
        raw(
            "PUT",
            "/api/v1/test/things/${TestApiController.EXISTING_THING_ID}",
            body = "a=".plus("x".repeat(307_200)).toByteArray(),
            chunked = true,
            headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
        ).assertProblem(ErrorCode.UNAUTHENTICATED)
    }

    @Test
    fun `form PUT with a token is never parsed as a form`() {
        val token = signIn().token
        val path = "/api/v1/test/things/${TestApiController.EXISTING_THING_ID}"
        val form = "a=".plus("x".repeat(307_200)).toByteArray()
        val headers = mapOf("Content-Type" to "application/x-www-form-urlencoded")

        raw("PUT", path, token, form, headers = headers).assertProblem(ErrorCode.PAYLOAD_TOO_LARGE)
        raw("PUT", path, token, form, chunked = true, headers = headers).assertProblem(ErrorCode.UNSUPPORTED_MEDIA_TYPE)
    }

    @Test
    fun `no form content filter and no multipart resolver exist`() {
        assertThat(context.getBeansOfType(FormContentFilter::class.java)).isEmpty()
        assertThat(context.getBeansOfType(MultipartResolver::class.java)).isEmpty()
    }

    @Test
    fun `chunked 300 KB multipart POST is not parsed and never succeeds`() {
        val token = signIn().token
        val boundary = "zzboundary"
        val multipart =
            (
                "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.json\"\r\n" +
                    "Content-Type: application/json\r\n\r\n" + "x".repeat(307_200) + "\r\n--$boundary--\r\n"
            ).toByteArray()

        listOf("/api/v1/test/echo", "/api/v1/users/me", "/anything").forEach { path ->
            val result =
                raw(
                    "POST",
                    path,
                    token,
                    multipart,
                    chunked = true,
                    headers = mapOf("Content-Type" to "multipart/form-data; boundary=$boundary"),
                )
            assertThat(result.status / 100, path).isNotEqualTo(2)
            assertThat(result.status, path).isNotIn(500)
        }
    }
}
