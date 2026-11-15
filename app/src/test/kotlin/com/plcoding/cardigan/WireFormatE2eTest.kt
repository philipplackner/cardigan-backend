package com.plcoding.cardigan

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.startsWith
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import com.plcoding.cardigan.testapi.TestApiController
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod

class WireFormatE2eTest : E2eTest() {

    private val thingId = TestApiController.EXISTING_THING_ID

    @Test
    fun `whole-second timestamp is written with milliseconds`() {
        val token = signIn().token

        val body = call(HttpMethod.GET, "/api/v1/test/things/$thingId", token).json()

        assertThat(body["createdAt"]).isEqualTo("2026-11-15T09:15:30.000Z")
    }

    @Test
    fun `uppercase UUID in the path is accepted and returned lowercase`() {
        val token = signIn().token

        val result = call(HttpMethod.GET, "/api/v1/test/things/${thingId.toString().uppercase()}", token)

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json()["id"]).isEqualTo(thingId.toString())
    }

    @Test
    fun `unknown request field is ignored`() {
        val token = signIn().token

        val result = call(HttpMethod.POST, "/api/v1/test/echo", token, json = """{"name":"a","key":"ab","surprise":{"x":1}}""")

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json().containsKey("surprise")).isEqualTo(false)
    }

    @Test
    fun `cross-origin request gets no CORS headers`() {
        val token = signIn().token

        val result = call(HttpMethod.GET, "/api/v1/users/me", token, headers = mapOf("Origin" to "https://evil.example"))

        assertThat(result.status).isEqualTo(200)
        assertThat(result.headers.headerNames().filter { it.startsWith("Access-Control-Allow", ignoreCase = true) }).isEmpty()
    }

    @Test
    fun `preflight gets no CORS headers`() {
        val token = signIn().token

        listOf(token, null).forEach { t ->
            val result = call(
                HttpMethod.OPTIONS,
                "/api/v1/users/me",
                t,
                headers = mapOf(
                    "Origin" to "https://evil.example",
                    "Access-Control-Request-Method" to "GET",
                    "Access-Control-Request-Headers" to "Authorization",
                ),
            )

            assertThat(result.headers.headerNames().filter { it.startsWith("Access-Control-Allow", ignoreCase = true) }).isEmpty()
        }
    }

    @Test
    fun `Accept text-plain still gets JSON`() {
        val token = signIn().token

        val result = call(HttpMethod.GET, "/api/v1/users/me", token, headers = mapOf("Accept" to "text/plain"))

        assertThat(result.status).isEqualTo(200)
        assertThat(result.contentType).isNotNull().startsWith("application/json")
    }

    @Test
    fun `Accept text-html on an error still gets problem JSON`() {
        call(HttpMethod.GET, "/api/v1/users/me", headers = mapOf("Accept" to "text/html"))
            .assertProblem(ErrorCode.UNAUTHENTICATED)
    }

    @Test
    fun `Accept text-html on a validation error still gets problem JSON`() {
        val token = signIn().token

        call(HttpMethod.POST, "/api/v1/test/echo", token, json = "{}", headers = mapOf("Accept" to "text/html"))
            .assertProblem(ErrorCode.VALIDATION_FAILED)
    }
}
