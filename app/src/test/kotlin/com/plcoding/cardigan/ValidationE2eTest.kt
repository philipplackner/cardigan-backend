package com.plcoding.cardigan

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import com.plcoding.cardigan.testapi.TestApiController
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import java.util.UUID

class ValidationE2eTest : E2eTest() {
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        token = signIn().token
    }

    private fun echo(json: String) = call(HttpMethod.POST, "/api/v1/test/echo", token, json = json)

    private fun fieldErrorsOf(json: String) = echo(json).assertProblem(ErrorCode.VALIDATION_FAILED).fieldErrors

    @Test
    fun `valid request passes`() {
        assertThat(echo("""{"name":"Roadmap","key":"ab"}""").status).isEqualTo(200)
    }

    @Test
    fun `empty body reports every required field`() {
        assertThat(fieldErrorsOf("{}")).containsExactly(
            mapOf("field" to "key", "code" to "REQUIRED"),
            mapOf("field" to "name", "code" to "REQUIRED"),
        )
    }

    @Test
    fun `explicit null is REQUIRED`() {
        assertThat(fieldErrorsOf("""{"name":null,"key":"ab"}""")).containsExactly(
            mapOf("field" to "name", "code" to "REQUIRED"),
        )
    }

    @Test
    fun `whitespace-only required text is REQUIRED`() {
        assertThat(fieldErrorsOf("""{"name":" 　 ","key":"ab"}""")).containsExactly(
            mapOf("field" to "name", "code" to "REQUIRED"),
        )
    }

    @Test
    fun `missing field and over-limit field are reported together, TOO_LONG with only max`() {
        assertThat(fieldErrorsOf("""{"key":"abcdef"}""")).containsExactly(
            mapOf("field" to "key", "code" to "TOO_LONG", "max" to 5),
            mapOf("field" to "name", "code" to "REQUIRED"),
        )
    }

    @Test
    fun `TOO_SHORT carries only min`() {
        assertThat(fieldErrorsOf("""{"name":"a","key":"a"}""")).containsExactly(
            mapOf("field" to "key", "code" to "TOO_SHORT", "min" to 2),
        )
    }

    @Test
    fun `one error per field, the first by precedence`() {
        assertThat(fieldErrorsOf("""{"name":"a\tbcdefghijklmnop","key":"ab"}""")).containsExactly(
            mapOf("field" to "name", "code" to "INVALID_CHARACTER"),
        )
    }

    @Test
    fun `limit reached only after trimming is accepted`() {
        assertThat(echo("""{"name":"  abcdefghij  ","key":"ab"}""").status).isEqualTo(200)
    }

    @Test
    fun `line breaks are allowed in a multi-line field only`() {
        assertThat(echo("""{"name":"a","key":"ab","note":"line\r\nline"}""").status).isEqualTo(200)
        assertThat(fieldErrorsOf("""{"name":"a\nb","key":"ab"}""")).containsExactly(
            mapOf("field" to "name", "code" to "INVALID_CHARACTER"),
        )
    }

    @Test
    fun `nested and array item paths`() {
        assertThat(
            fieldErrorsOf("""{"name":"a","key":"ab","nested":{},"items":[{"name":"ok"},{"name":null}]}"""),
        ).containsExactly(
            mapOf("field" to "items[1].name", "code" to "REQUIRED"),
            mapOf("field" to "nested.name", "code" to "REQUIRED"),
        )
    }

    @Test
    fun `non-UUID array item is INVALID_FORMAT at its index`() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()

        assertThat(fieldErrorsOf("""{"name":"a","key":"ab","labelIds":["$a","$b","nope"]}""")).containsExactly(
            mapOf("field" to "labelIds[2]", "code" to "INVALID_FORMAT"),
        )
    }

    @Test
    fun `unknown enum value is INVALID_VALUE`() {
        assertThat(fieldErrorsOf("""{"name":"a","key":"ab","color":"BLUE"}""")).containsExactly(
            mapOf("field" to "color", "code" to "INVALID_VALUE"),
        )
    }

    @Test
    fun `known enum value passes`() {
        assertThat(echo("""{"name":"a","key":"ab","color":"GREEN"}""").status).isEqualTo(200)
    }

    @Test
    fun `huge integer is OUT_OF_RANGE with both limits`() {
        assertThat(fieldErrorsOf("""{"name":"a","key":"ab","position":1099511627776}""")).containsExactly(
            mapOf("field" to "position", "code" to "OUT_OF_RANGE", "min" to 0, "max" to 100),
        )
    }

    @Test
    fun `integer beyond 64 bits is OUT_OF_RANGE, not MALFORMED_REQUEST`() {
        assertThat(fieldErrorsOf("""{"name":"a","key":"ab","position":${"9".repeat(40)}}""")).containsExactly(
            mapOf("field" to "position", "code" to "OUT_OF_RANGE", "min" to 0, "max" to 100),
        )
    }

    @Test
    fun `malformed path ID with an unparseable body is MALFORMED_REQUEST`() {
        call(HttpMethod.PUT, "/api/v1/test/things/abc", token, json = "{")
            .assertProblem(ErrorCode.MALFORMED_REQUEST)
    }

    @Test
    fun `missing reference with an invalid field is NOT_FOUND`() {
        call(HttpMethod.PUT, "/api/v1/test/things/${UUID.randomUUID()}", token, json = "{}")
            .assertProblem(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `existing reference with an invalid field is VALIDATION_FAILED`() {
        val body =
            call(
                HttpMethod.PUT,
                "/api/v1/test/things/${TestApiController.EXISTING_THING_ID}",
                token,
                json = """{"key":"ab"}""",
            ).assertProblem(ErrorCode.VALIDATION_FAILED)

        assertThat(body.fieldErrors).containsExactly(mapOf("field" to "name", "code" to "REQUIRED"))
    }
}
