package com.plcoding.cardigan.common.api

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.ProblemDetail
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.json.JsonMapper

class ProblemResponseWriterTest {
    private val jsonMapper =
        JsonMapper
            .builder()
            .addMixIn(
                ProblemDetail::class.java,
                ProblemDetailMixin::class.java,
            ).build()
    private val writer = ProblemResponseWriter(jsonMapper)

    private fun expectedJson(code: ErrorCode) =
        jsonMapper.readTree(
            """
        {"type":"${code.type}","title":"${code.title}","status":${code.status},"detail":"${code.detail}",
         "code":"${code.name}","fieldErrors":[]}
        """,
        )

    @Test
    fun `writes the problem body with status and content type`() {
        val response = MockHttpServletResponse()

        writer.write(response, ErrorCode.PAYLOAD_TOO_LARGE)

        assertThat(response.status).isEqualTo(413)
        assertThat(response.contentType).isEqualTo("application/problem+json;charset=UTF-8")
        assertThat(response.characterEncoding).isEqualTo("UTF-8")
        assertThat(
            jsonMapper.readTree(response.contentAsByteArray),
        ).isEqualTo(expectedJson(ErrorCode.PAYLOAD_TOO_LARGE))
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull()
    }

    @Test
    fun `discards anything already buffered`() {
        val response = MockHttpServletResponse()
        response.outputStream.write("partial".toByteArray())

        writer.write(response, ErrorCode.MALFORMED_REQUEST)

        assertThat(
            jsonMapper.readTree(response.contentAsByteArray),
        ).isEqualTo(expectedJson(ErrorCode.MALFORMED_REQUEST))
    }

    @Test
    fun `unauthenticated gets a bearer challenge`() {
        val response = MockHttpServletResponse()

        writer.write(response, ErrorCode.UNAUTHENTICATED)

        assertThat(response.status).isEqualTo(401)
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer")
    }
}
