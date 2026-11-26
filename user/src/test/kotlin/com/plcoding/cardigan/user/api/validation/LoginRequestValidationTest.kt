package com.plcoding.cardigan.user.api.validation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import com.plcoding.cardigan.common.api.ProblemFieldError
import com.plcoding.cardigan.common.api.validation.fieldErrorsOf
import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import com.plcoding.cardigan.user.api.dto.LoginRequest
import com.plcoding.cardigan.user.api.dto.LogoutRequest
import com.plcoding.cardigan.user.api.dto.RefreshRequest
import jakarta.validation.Validation
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoginRequestValidationTest {

    private val factory = Validation.buildDefaultValidatorFactory()
    private val validator = factory.validator

    @AfterAll
    fun close() = factory.close()

    private fun errorsOf(request: Any) = fieldErrorsOf(validator.validate(request))

    private fun login(email: String? = "ann@example.com", password: String? = "pw", transport: String? = "COOKIE") =
        LoginRequest(email, password, transport)

    @Test
    fun `all fields missing reports REQUIRED for each`() {
        assertThat(errorsOf(LoginRequest(null, null, null))).containsExactly(
            ProblemFieldError("email", FieldErrorCode.REQUIRED),
            ProblemFieldError("password", FieldErrorCode.REQUIRED),
            ProblemFieldError("refreshTokenTransport", FieldErrorCode.REQUIRED),
        )
    }

    @Test
    fun `missing fields break no rule other than REQUIRED`() {
        assertThat(validator.validate(LoginRequest(null, null, null))).hasSize(3)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "  ", " ", "\r\n"])
    fun `blank email is REQUIRED`(email: String) {
        assertThat(errorsOf(login(email = email))).containsExactly(ProblemFieldError("email", FieldErrorCode.REQUIRED))
    }

    @Test
    fun `empty password is REQUIRED`() {
        assertThat(errorsOf(login(password = ""))).containsExactly(ProblemFieldError("password", FieldErrorCode.REQUIRED))
    }

    @Test
    fun `password of only spaces is a value`() {
        assertThat(errorsOf(login(password = "   "))).isEmpty()
        assertThat(errorsOf(login(password = " "))).isEmpty()
    }

    @Test
    fun `no email format or password rule applies`() {
        assertThat(errorsOf(login(email = "not an email", password = "abc"))).isEmpty()
        assertThat(errorsOf(login(email = "ann@localhost"))).isEmpty()
        assertThat(errorsOf(login(email = "jürgen@münchen.de"))).isEmpty()
        assertThat(errorsOf(login(password = "x".repeat(5000)))).isEmpty()
        assertThat(errorsOf(login(password = "a\u0000b"))).isEmpty()
    }

    @Test
    fun `email with a control character is INVALID_CHARACTER`() {
        assertThat(errorsOf(login(email = "ann\u0000@example.com")))
            .containsExactly(ProblemFieldError("email", FieldErrorCode.INVALID_CHARACTER))
    }

    @Test
    fun `email of 254 characters passes and 255 is TOO_LONG with max 254`() {
        assertThat(errorsOf(login(email = "a".repeat(254)))).isEmpty()
        assertThat(errorsOf(login(email = "  " + "a".repeat(254) + "  "))).isEmpty()
        assertThat(errorsOf(login(email = "a".repeat(255))))
            .containsExactly(ProblemFieldError("email", FieldErrorCode.TOO_LONG, max = 254))
    }

    @Test
    fun `INVALID_CHARACTER wins over TOO_LONG`() {
        assertThat(errorsOf(login(email = "\u0000" + "a".repeat(300))))
            .containsExactly(ProblemFieldError("email", FieldErrorCode.INVALID_CHARACTER))
    }

    @Test
    fun `unknown transport is INVALID_VALUE`() {
        assertThat(errorsOf(login(transport = "HEADER")))
            .containsExactly(ProblemFieldError("refreshTokenTransport", FieldErrorCode.INVALID_VALUE))
    }

    @Test
    fun `refresh and logout need only the transport`() {
        val required = ProblemFieldError("refreshTokenTransport", FieldErrorCode.REQUIRED)

        assertThat(errorsOf(RefreshRequest(null, null))).containsExactly(required)
        assertThat(errorsOf(LogoutRequest(null, "token"))).containsExactly(required)
        assertThat(errorsOf(RefreshRequest("BODY", null))).isEmpty()
        assertThat(errorsOf(LogoutRequest("COOKIE", null))).isEmpty()
        assertThat(errorsOf(RefreshRequest("BODY", ""))).isEmpty()
    }

    @Test
    fun `unknown transport on refresh and logout is INVALID_VALUE`() {
        val invalid = ProblemFieldError("refreshTokenTransport", FieldErrorCode.INVALID_VALUE)

        assertThat(errorsOf(RefreshRequest("HEADER", "token"))).containsExactly(invalid)
        assertThat(errorsOf(LogoutRequest("body", "token"))).containsExactly(invalid)
    }
}
