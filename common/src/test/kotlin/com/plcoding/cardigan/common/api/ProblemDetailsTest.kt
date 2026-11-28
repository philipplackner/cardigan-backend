package com.plcoding.cardigan.common.api

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.http.ProblemDetail
import java.net.URI

class ProblemDetailsTest {
    @ParameterizedTest
    @EnumSource(ErrorCode::class)
    fun `fields come from the error code`(code: ErrorCode) {
        val problem = problemDetail(code)

        assertThat(problem).all {
            prop(ProblemDetail::getType).isEqualTo(URI.create(code.type))
            prop(ProblemDetail::getTitle).isEqualTo(code.title)
            prop(ProblemDetail::getDetail).isEqualTo(code.detail)
            prop(ProblemDetail::getStatus).isEqualTo(code.status)
            prop(ProblemDetail::getInstance).isNull()
        }
        assertThat(problem.properties).isNotNull().containsOnly(
            "code" to code.name,
            "fieldErrors" to emptyList<ProblemFieldError>(),
        )
    }

    @Test
    fun `field errors are kept`() {
        val errors = listOf(ProblemFieldError("name", FieldErrorCode.TOO_LONG, max = 100))

        val problem = problemDetail(ErrorCode.VALIDATION_FAILED, errors)

        assertThat(problem.properties?.get("fieldErrors")).isEqualTo(errors)
    }

    @Test
    fun `fieldErrors is present and empty without field errors`() {
        assertThat(problemDetail(ErrorCode.NOT_FOUND).properties?.get("fieldErrors"))
            .isNotNull()
            .isInstanceOf<List<*>>()
            .isEmpty()
    }
}
