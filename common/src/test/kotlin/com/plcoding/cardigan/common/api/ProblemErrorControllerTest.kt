package com.plcoding.cardigan.common.api

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import com.plcoding.cardigan.common.fake.LogCapture
import jakarta.servlet.RequestDispatcher
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

class ProblemErrorControllerTest {
    private val controller = ProblemErrorController()

    private fun errorRequest(
        status: Int?,
        exception: Throwable? = null,
    ) = MockHttpServletRequest().apply {
        status?.let { setAttribute(RequestDispatcher.ERROR_STATUS_CODE, it) }
        exception?.let { setAttribute(RequestDispatcher.ERROR_EXCEPTION, it) }
    }

    private fun codeFor(request: MockHttpServletRequest): String? =
        controller.error(request).body!!.properties!!["code"] as String?

    @Test
    fun `direct call without an error is ROUTE_NOT_FOUND`() {
        assertThat(codeFor(errorRequest(null))).isEqualTo(ErrorCode.ROUTE_NOT_FOUND.name)
    }

    @Test
    fun `client errors keep their meaning and are not logged`() {
        LogCapture(ProblemErrorController::class.java).use { logs ->
            assertThat(codeFor(errorRequest(404))).isEqualTo(ErrorCode.ROUTE_NOT_FOUND.name)
            assertThat(codeFor(errorRequest(400))).isEqualTo(ErrorCode.MALFORMED_REQUEST.name)
            assertThat(codeFor(errorRequest(413))).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE.name)
            assertThat(logs.errors).isEmpty()
        }
    }

    @Test
    fun `any other status is INTERNAL_ERROR and logs the exception`() {
        LogCapture(ProblemErrorController::class.java).use { logs ->
            val response = controller.error(errorRequest(500, IllegalStateException("filter failed")))

            assertThat(response.statusCode.value()).isEqualTo(500)
            assertThat(response.body!!.properties!!["code"]).isEqualTo(ErrorCode.INTERNAL_ERROR.name)
            assertThat(
                logs.errors
                    .single()
                    .throwableProxy.message,
            ).isEqualTo("filter failed")
        }
    }
}
