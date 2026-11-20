package com.plcoding.cardigan.common.api

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import com.plcoding.cardigan.common.domain.exception.FieldOutOfRangeException
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

class CommonExceptionHandlerTest {

    private val handler = CommonExceptionHandler()

    @Test
    fun `out-of-range field is VALIDATION_FAILED with one OUT_OF_RANGE error carrying min and max`() {
        val response = handler.handleFieldOutOfRange(FieldOutOfRangeException("position", min = 0, max = 2))

        assertThat(response.statusCode.value()).isEqualTo(400)
        assertThat(response.headers.contentType).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON)
        val properties = response.body!!.properties!!
        assertThat(properties["code"]).isEqualTo(ErrorCode.VALIDATION_FAILED.name)
        assertThat(properties["fieldErrors"]).isEqualTo(
            listOf(ProblemFieldError("position", FieldErrorCode.OUT_OF_RANGE, min = 0, max = 2)),
        )
    }

    @Test
    fun `exception message never reaches the response`() {
        val response = handler.handleFieldOutOfRange(FieldOutOfRangeException("position", 0, 0, "secret-marker"))

        assertThat(response.body!!.detail).isEqualTo(ErrorCode.VALIDATION_FAILED.detail)
    }
}
