package com.plcoding.cardigan.common.api.validation

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import assertk.assertions.prop
import com.plcoding.cardigan.common.api.ProblemFieldError
import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Validation
import jakarta.validation.constraints.NotBlank
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.lang.reflect.Proxy
import java.math.BigInteger

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValidatorsTest {

    private val factory = Validation.buildDefaultValidatorFactory()
    private val validator = factory.validator
    private val requestValidator = RequestValidator(validator)

    @AfterAll
    fun close() = factory.close()

    data class MoveRequest(
        @Required
        @ValidRange(min = 0, max = 5)
        val position: BigInteger?,
    )

    data class LegacyRequest(
        @field:NotBlank
        val name: String?,
    )

    data class KeyRequest(
        @ValidText(max = 5)
        @ValidUuid
        val id: String?,
    )

    private fun errorsOf(request: Any) = fieldErrorsOf(validator.validate(request))

    @Test
    fun `range limits are both valid`() {
        assertThat(errorsOf(MoveRequest(BigInteger.ZERO))).isEmpty()
        assertThat(errorsOf(MoveRequest(BigInteger.valueOf(5)))).isEmpty()
    }

    @Test
    fun `values just outside the range are OUT_OF_RANGE with both limits`() {
        val outOfRange = ProblemFieldError("position", FieldErrorCode.OUT_OF_RANGE, min = 0, max = 5)

        assertThat(errorsOf(MoveRequest(BigInteger.valueOf(-1)))).containsExactly(outOfRange)
        assertThat(errorsOf(MoveRequest(BigInteger.valueOf(6)))).containsExactly(outOfRange)
    }

    @Test
    fun `values beyond Long are checked without overflow`() {
        val huge = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)

        assertThat(errorsOf(MoveRequest(huge)))
            .containsExactly(ProblemFieldError("position", FieldErrorCode.OUT_OF_RANGE, min = 0, max = 5))
    }

    @Test
    fun `missing range value is only REQUIRED`() {
        assertThat(errorsOf(MoveRequest(null))).containsExactly(ProblemFieldError("position", FieldErrorCode.REQUIRED))
    }

    @Test
    fun `constraint without a field error code is INVALID_VALUE`() {
        assertThat(errorsOf(LegacyRequest(" "))).containsExactly(ProblemFieldError("name", FieldErrorCode.INVALID_VALUE))
    }

    @Test
    fun `precedence doesn't depend on the order of the violations`() {
        val violations = validator.validate(KeyRequest("not-a-uuid")).toList()
        val expected = ProblemFieldError("id", FieldErrorCode.TOO_LONG, max = 5)

        assertThat(fieldErrorsOf(violations)).containsExactly(expected)
        assertThat(fieldErrorsOf(violations.reversed())).containsExactly(expected)
    }

    @Test
    fun `request validator accepts a valid request`() {
        requestValidator.validate(MoveRequest(BigInteger.ONE))
    }

    @Test
    fun `request validator reports every field error`() {
        assertFailure { requestValidator.validate(MoveRequest(null)) }
            .isInstanceOf<RequestValidationException>()
            .prop(RequestValidationException::fieldErrors)
            .containsExactly(ProblemFieldError("position", FieldErrorCode.REQUIRED))
    }

    @Test
    fun `null patch request is valid`() {
        val unusedContext = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(ConstraintValidatorContext::class.java),
        ) { _, method, _ -> error("unexpected call to ${method.name}") } as ConstraintValidatorContext

        assertThat(NotNullWhenPresentValidator().isValid(null, unusedContext)).isTrue()
    }
}
