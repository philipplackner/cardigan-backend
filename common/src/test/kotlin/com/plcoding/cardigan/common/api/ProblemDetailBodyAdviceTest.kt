package com.plcoding.cardigan.common.api

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.http.server.ServletServerHttpResponse
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.net.URI

class ProblemDetailBodyAdviceTest {
    private val advice = ProblemDetailBodyAdvice()
    private val returnType = MethodParameter(Any::class.java.getMethod("toString"), -1)
    private val converter = JacksonJsonHttpMessageConverter::class.java

    private fun write(body: Any?): Any? =
        advice.beforeBodyWrite(
            body,
            returnType,
            MediaType.APPLICATION_PROBLEM_JSON,
            converter,
            ServletServerHttpRequest(MockHttpServletRequest()),
            ServletServerHttpResponse(MockHttpServletResponse()),
        )

    @Test
    fun `applies to every response`() {
        assertThat(advice.supports(returnType, converter)).isTrue()
    }

    @Test
    fun `clears the request path Spring MVC put into instance`() {
        val problem = problemDetail(ErrorCode.NOT_FOUND).apply { instance = URI.create("/api/v1/boards/secret") }

        val written = write(problem)

        assertThat(written).isSameInstanceAs(problem)
        assertThat(problem.instance).isNull()
    }

    @Test
    fun `leaves other bodies unchanged`() {
        val body = mapOf("name" to "board")

        assertThat(write(body)).isEqualTo(body)
    }
}
