package com.plcoding.cardigan.user.api.validation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isFalse
import assertk.assertions.isLessThan
import assertk.assertions.isTrue
import com.plcoding.cardigan.common.api.ProblemFieldError
import com.plcoding.cardigan.common.api.validation.fieldErrorsOf
import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import com.plcoding.cardigan.user.api.dto.RegisterRequest
import jakarta.validation.Validation
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegisterRequestValidationTest {

    private val factory = Validation.buildDefaultValidatorFactory()
    private val validator = factory.validator

    @AfterAll
    fun close() = factory.close()

    private fun request(
        email: String? = "ann@example.com",
        password: String? = "Correct horse 7",
        transport: String? = "BODY",
    ) = RegisterRequest(email, password, transport)

    private fun errorsOf(request: RegisterRequest) = fieldErrorsOf(validator.validate(request))

    private fun emailErrors(email: String?) = errorsOf(request(email = email))

    private fun passwordErrors(password: String?) = errorsOf(request(password = password))

    private val emailOf254Characters =
        "a".repeat(64) + "@" + "a".repeat(63) + "." + "a".repeat(63) + "." + "a".repeat(57) + ".com"

    private val invalidFormat = ProblemFieldError("email", FieldErrorCode.INVALID_FORMAT)
    private val tooWeak = ProblemFieldError("password", FieldErrorCode.PASSWORD_TOO_WEAK)
    private val tooShort = ProblemFieldError("password", FieldErrorCode.TOO_SHORT, min = 12)
    private val tooLong = ProblemFieldError("password", FieldErrorCode.TOO_LONG, max = 128)

    @Test
    fun `valid request has no errors`() {
        assertThat(errorsOf(request())).isEmpty()
    }

    @Test
    fun `all fields missing reports REQUIRED for each`() {
        assertThat(errorsOf(RegisterRequest(null, null, null))).containsExactly(
            ProblemFieldError("email", FieldErrorCode.REQUIRED),
            ProblemFieldError("password", FieldErrorCode.REQUIRED),
            ProblemFieldError("refreshTokenTransport", FieldErrorCode.REQUIRED),
        )
    }

    @Test
    fun `missing fields break no rule other than REQUIRED`() {
        assertThat(validator.validate(RegisterRequest(null, null, null))).hasSize(3)
    }

    @Test
    fun `bad email and weak password are reported together`() {
        assertThat(errorsOf(request(email = "ann@", password = "alllowercase12"))).containsExactly(invalidFormat, tooWeak)
    }

    @ParameterizedTest
    @ValueSource(strings = ["HEADER", "cookie", "", " BODY"])
    fun `unknown transport is INVALID_VALUE`(transport: String) {
        assertThat(errorsOf(request(transport = transport)))
            .containsExactly(ProblemFieldError("refreshTokenTransport", FieldErrorCode.INVALID_VALUE))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "ann.lee+cards@example.com",
            "  Ann@Example.com ",
            "!#$%&'*+/=?^_`{|}~-@example.com",
            "a@b.co",
            "ann@mail.example.com",
            "ann@a.b.example.co.uk",
            "ann@xn--mnchen-3ya.de",
            "ann@my-example.com",
            "ann@123.example.com",
            "ANN@EXAMPLE.COM",
        ],
    )
    fun `valid addresses are accepted`(email: String) {
        assertThat(emailErrors(email)).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "ann@",
            "@example.com",
            "mail@pl-coding",
            "ann@localhost",
            "a@b",
            "ann@1.2.3",
            "ann@example.c",
            "ann@example.123",
            "ann@example.xn--p1ai",
            "ann@-example.com",
            "ann@example-.com",
            "ann@[192.168.0.1]",
            "ann@[IPv6:2001:db8::1]",
            "ann@[]",
            "\"ann.lee\"@example.com",
            "\"\"@example.com",
            "an..n@example.com",
            "ann",
            "ann@@example.com",
            "ann@example@com",
            "jürgen@münchen.de",
            "ann@münchen.de",
            "ann(work)@example.com",
            "ann@example.com(work)",
            ".ann@example.com",
            "ann.@example.com",
            "ann..lee@example.com",
            "ann@.example.com",
            "ann@example.com.",
            "ann@example..com",
            "ann lee@example.com",
            "ann@exam ple.com",
            "ann @example.com",
            "\"ann lee\"@example.com",
            "\"ann@example.com",
            "ann\"lee\"@example.com",
            "\"ann\"lee@example.com",
            "\"ann\\\"@example.com",
            "ann@[192.168.0.1",
            "ann@[a[b]",
            "ann@[a\\b]",
            "ann@[a b]",
            "<ann@example.com>",
            "Ann Lee <ann@example.com>",
            "ann@example.com,bob@example.com",
            "ann＠example.com",
        ],
    )
    fun `invalid addresses are INVALID_FORMAT`(email: String) {
        assertThat(emailErrors(email)).containsExactly(invalidFormat)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "  ", "\n"])
    fun `empty and whitespace-only email is REQUIRED`(email: String) {
        assertThat(emailErrors(email)).containsExactly(ProblemFieldError("email", FieldErrorCode.REQUIRED))
    }

    @Test
    fun `local part of 64 characters is accepted and 65 is INVALID_FORMAT`() {
        assertThat(emailErrors("a".repeat(64) + "@example.com")).isEmpty()
        assertThat(emailErrors("a".repeat(65) + "@example.com")).containsExactly(invalidFormat)
    }

    @Test
    fun `domain label of 63 characters is accepted and 64 is INVALID_FORMAT`() {
        assertThat(emailErrors("ann@" + "a".repeat(63) + ".com")).isEmpty()
        assertThat(emailErrors("ann@" + "a".repeat(64) + ".com")).containsExactly(invalidFormat)
    }

    @Test
    fun `top-level label of 63 letters is accepted and 64 is INVALID_FORMAT`() {
        assertThat(emailErrors("ann@example." + "a".repeat(63))).isEmpty()
        assertThat(emailErrors("ann@example." + "a".repeat(64))).containsExactly(invalidFormat)
    }

    @Test
    fun `email of 254 characters is accepted`() {
        assertThat(emailErrors(emailOf254Characters)).isEmpty()
    }

    @Test
    fun `email of 254 characters after trimming is accepted`() {
        assertThat(emailErrors("  $emailOf254Characters  ")).isEmpty()
    }

    @Test
    fun `email of 255 characters reports only TOO_LONG`() {
        val request = request(email = emailOf254Characters + "m")

        assertThat(validator.validate(request)).hasSize(1)
        assertThat(errorsOf(request)).containsExactly(ProblemFieldError("email", FieldErrorCode.TOO_LONG, max = 254))
    }

    @Test
    fun `malformed email of 255 characters reports only TOO_LONG`() {
        val request = request(email = "a".repeat(255))

        assertThat(validator.validate(request)).hasSize(1)
        assertThat(errorsOf(request)).containsExactly(ProblemFieldError("email", FieldErrorCode.TOO_LONG, max = 254))
    }

    @Test
    fun `email with a control character reports only INVALID_CHARACTER`() {
        val request = request(email = "ann\u0000@example.com")

        assertThat(validator.validate(request)).hasSize(1)
        assertThat(errorsOf(request)).containsExactly(ProblemFieldError("email", FieldErrorCode.INVALID_CHARACTER))
    }

    @Test
    fun `non-ASCII email is INVALID_FORMAT, not INVALID_CHARACTER`() {
        assertThat(emailErrors("jürgen@münchen.de")).containsExactly(invalidFormat)
    }

    @Test
    fun `email with a line break inside is INVALID_CHARACTER`() {
        assertThat(emailErrors("ann@example.com\nbob@example.com"))
            .containsExactly(ProblemFieldError("email", FieldErrorCode.INVALID_CHARACTER))
    }

    @Test
    fun `250KB near-matching email is validated within 100 ms`() {
        val nearMatches = listOf(
            "a".repeat(250_000) + "@",
            "a.".repeat(125_000) + "@example.com.",
            "\"" + "\\a".repeat(125_000) + "@example.com",
            "ann@" + "a.".repeat(125_000),
        )
        nearMatches.forEach { emailErrors(it) }

        nearMatches.forEach { email ->
            val fastest = (1..5).minOf {
                val start = System.nanoTime()
                val errors = emailErrors(email)
                val elapsed = System.nanoTime() - start
                assertThat(errors).containsExactly(ProblemFieldError("email", FieldErrorCode.TOO_LONG, max = 254))
                elapsed
            }
            assertThat(fastest / 1_000_000).isLessThan(100)
        }
    }

    @Test
    fun `250KB near-match is not an email format within 100 ms`() {
        val nearMatch = "a@" + "a.".repeat(125_000) + "1"
        hasEmailFormat(nearMatch)

        val fastest = (1..5).minOf {
            val start = System.nanoTime()
            assertThat(hasEmailFormat(nearMatch)).isFalse()
            System.nanoTime() - start
        }
        assertThat(fastest / 1_000_000).isLessThan(100)
    }

    @Test
    fun `near-matches at the length limit are checked within 100 ms`() {
        val nearMatches = listOf(
            "a".repeat(64) + "@" + "a.".repeat(94) + "1",
            "a.".repeat(32) + "@example.com",
            "a".repeat(64) + "@" + "a-".repeat(94) + "a",
            "a".repeat(64) + "@" + "a".repeat(189),
        )
        nearMatches.forEach { hasEmailFormat(it) }

        nearMatches.forEach { email ->
            val start = System.nanoTime()
            val matches = hasEmailFormat(email)
            val elapsed = System.nanoTime() - start

            assertThat(matches).isFalse()
            assertThat(elapsed / 1_000_000).isLessThan(100)
        }
        assertThat(hasEmailFormat("a".repeat(64) + "@" + "a.".repeat(92) + "com")).isTrue()
    }

    @Test
    fun `password of 11 code points is TOO_SHORT with min 12`() {
        assertThat(passwordErrors("Correct hrs7")).isEmpty()
        assertThat(passwordErrors("Correct hs7")).containsExactly(tooShort)
    }

    @Test
    fun `password of 128 code points is accepted and 129 is TOO_LONG with max 128`() {
        assertThat(passwordErrors("Aa1" + "x".repeat(125))).isEmpty()
        assertThat(passwordErrors("Aa1" + "x".repeat(126))).containsExactly(tooLong)
    }

    @Test
    fun `each missing character class is PASSWORD_TOO_WEAK`() {
        assertThat(passwordErrors("alllowercase12")).containsExactly(tooWeak)
        assertThat(passwordErrors("ALLUPPERCASE12")).containsExactly(tooWeak)
        assertThat(passwordErrors("NoDigitsInHere")).containsExactly(tooWeak)
        assertThat(passwordErrors("123456789012")).containsExactly(tooWeak)
    }

    @Test
    fun `non-ASCII letters and digits count`() {
        assertThat(passwordErrors("äöüÄÖÜ١٢٣ßßßß")).isEmpty()
    }

    @Test
    fun `titlecase letters, letter numbers and superscripts are not the required classes`() {
        assertThat(passwordErrors("ǅlowercase123")).containsExactly(tooWeak)
        assertThat(passwordErrors("Upper lower Ⅷ")).containsExactly(tooWeak)
        assertThat(passwordErrors("Upper lower ²")).containsExactly(tooWeak)
    }

    @Test
    fun `control characters, tabs and line breaks are INVALID_CHARACTER`() {
        val invalidCharacter = ProblemFieldError("password", FieldErrorCode.INVALID_CHARACTER)

        assertThat(passwordErrors("Correct\u0007horse 7")).containsExactly(invalidCharacter)
        assertThat(passwordErrors("Correct\thorse 7")).containsExactly(invalidCharacter)
        assertThat(passwordErrors("Correct\nhorse 7")).containsExactly(invalidCharacter)
        assertThat(passwordErrors("Correct horse 7\uD83D")).containsExactly(invalidCharacter)
    }

    @Test
    fun `INVALID_CHARACTER wins over the length`() {
        assertThat(passwordErrors("a\u0000")).containsExactly(ProblemFieldError("password", FieldErrorCode.INVALID_CHARACTER))
    }

    @Test
    fun `astral-plane character counts once`() {
        val emoji = "😀"

        assertThat(passwordErrors("Aa1xxxxxxxx$emoji")).isEmpty()
        assertThat(passwordErrors("Aa1xxxxxxx$emoji")).containsExactly(tooShort)
        assertThat(passwordErrors("Aa1" + emoji.repeat(125))).isEmpty()
        assertThat(passwordErrors("Aa1" + emoji.repeat(126))).containsExactly(tooLong)
    }

    @Test
    fun `length is counted after NFC`() {
        assertThat(passwordErrors("A1" + "é".repeat(10))).isEmpty()
        assertThat(passwordErrors("A1" + "é".repeat(9))).containsExactly(tooShort)
        assertThat(passwordErrors("A1" + "é".repeat(126))).isEmpty()
    }

    @Test
    fun `password of only spaces is weak, not required`() {
        assertThat(passwordErrors(" ".repeat(12))).containsExactly(tooWeak)
        assertThat(passwordErrors("   ")).containsExactly(tooShort)
    }

    @Test
    fun `surrounding spaces count toward the length`() {
        assertThat(passwordErrors(" Aa1xxxxxxx ")).isEmpty()
    }

    @Test
    fun `empty password is REQUIRED`() {
        assertThat(passwordErrors("")).containsExactly(ProblemFieldError("password", FieldErrorCode.REQUIRED))
    }

    @Test
    fun `validation doesn't change the request`() {
        val request = request(email = "  Ann@Example.com ", password = " Correct horse 7 ")

        errorsOf(request)

        assertThat(request.email == "  Ann@Example.com " && request.password == " Correct horse 7 ").isTrue()
    }
}
