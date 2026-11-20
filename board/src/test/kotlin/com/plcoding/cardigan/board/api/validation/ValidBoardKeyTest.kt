package com.plcoding.cardigan.board.api.validation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import com.plcoding.cardigan.board.api.dto.CreateBoardRequest
import com.plcoding.cardigan.common.api.ProblemFieldError
import com.plcoding.cardigan.common.api.validation.fieldErrorsOf
import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import jakarta.validation.Validation
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValidBoardKeyTest {

    private val factory = Validation.buildDefaultValidatorFactory()
    private val validator = factory.validator

    @AfterAll
    fun close() = factory.close()

    private fun errorsOf(key: String?) = fieldErrorsOf(validator.validate(CreateBoardRequest(name = "Roadmap", key = key)))

    @Test
    fun `valid keys pass, lowercase and surrounding whitespace included`() {
        listOf("CRD", "crd", " crd ", "AB", "abcde").forEach { assertThat(errorsOf(it), it).isEmpty() }
    }

    @Test
    fun `missing key is one REQUIRED error`() {
        assertThat(errorsOf(null)).containsExactly(ProblemFieldError("key", FieldErrorCode.REQUIRED))
    }

    @Test
    fun `whitespace-only key is REQUIRED without limits`() {
        assertThat(errorsOf("   ")).containsExactly(ProblemFieldError("key", FieldErrorCode.REQUIRED))
    }

    @Test
    fun `short key is TOO_SHORT with min 2 only`() {
        assertThat(errorsOf("1")).containsExactly(ProblemFieldError("key", FieldErrorCode.TOO_SHORT, min = 2))
    }

    @Test
    fun `long key is TOO_LONG with max 5 only`() {
        assertThat(errorsOf("ÄBCDEF")).containsExactly(ProblemFieldError("key", FieldErrorCode.TOO_LONG, max = 5))
    }

    @Test
    fun `wrong character is INVALID_FORMAT without limits`() {
        listOf("C D", "C\tD", "CR1", "C\ud800D", "äb").forEach {
            assertThat(errorsOf(it), it).containsExactly(ProblemFieldError("key", FieldErrorCode.INVALID_FORMAT))
        }
    }

    @Test
    fun `broken name and broken key are reported together`() {
        val errors = fieldErrorsOf(validator.validate(CreateBoardRequest(name = "a\nb", key = "C")))

        assertThat(errors).containsExactly(
            ProblemFieldError("key", FieldErrorCode.TOO_SHORT, min = 2),
            ProblemFieldError("name", FieldErrorCode.INVALID_CHARACTER),
        )
    }
}
