package com.plcoding.cardigan.user.api.validation

import com.plcoding.cardigan.common.api.validation.FieldViolation
import com.plcoding.cardigan.common.api.validation.reject
import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import com.plcoding.cardigan.common.domain.text.checkText
import com.plcoding.cardigan.common.domain.text.codePointLength
import com.plcoding.cardigan.common.domain.text.normalizePassword
import com.plcoding.cardigan.common.domain.text.normalizeText
import com.plcoding.cardigan.user.domain.model.EMAIL_MAX_LENGTH
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

/** The shared `domain-user` reference pattern, character for character. */
private val emailFormat =
    Regex(
        """^(?=[^@]{1,64}@)[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}$""",
    )

/** True when [text] has the `domain-user` email format. Text over [EMAIL_MAX_LENGTH] code points never has it. */
internal fun hasEmailFormat(text: String): Boolean =
    // The JVM regex recurses once per domain label, so long input would overflow the stack.
    codePointLength(text) <= EMAIL_MAX_LENGTH && emailFormat.matches(text)

class ValidEmailValidator : ConstraintValidator<ValidEmail, String> {
    override fun isValid(
        value: String?,
        context: ConstraintValidatorContext,
    ): Boolean {
        if (value == null) return true
        val email = normalizeText(value)
        if (checkText(email, min = 1, max = EMAIL_MAX_LENGTH, multiline = false) != null) return true
        if (hasEmailFormat(email)) return true
        context.reject(FieldViolation(FieldErrorCode.INVALID_FORMAT))
        return false
    }
}

class ValidPasswordValidator : ConstraintValidator<ValidPassword, String> {
    override fun isValid(
        value: String?,
        context: ConstraintValidatorContext,
    ): Boolean {
        if (value == null) return true
        val password = normalizePassword(value)
        val code =
            checkText(password, PASSWORD_MIN_LENGTH, PASSWORD_MAX_LENGTH, multiline = false)
                ?: FieldErrorCode.PASSWORD_TOO_WEAK.takeUnless { hasEveryCharacterClass(password) }
                ?: return true
        context.reject(
            FieldViolation(
                code = code,
                min = PASSWORD_MIN_LENGTH.toLong().takeIf { code == FieldErrorCode.TOO_SHORT },
                max = PASSWORD_MAX_LENGTH.toLong().takeIf { code == FieldErrorCode.TOO_LONG },
            ),
        )
        return false
    }

    private fun hasEveryCharacterClass(password: String): Boolean =
        password
            .codePoints()
            .map(Character::getType)
            .toArray()
            .toSet()
            .containsAll(requiredCharacterClasses)

    private companion object {
        const val PASSWORD_MIN_LENGTH = 12
        const val PASSWORD_MAX_LENGTH = 128
    }
}

private val requiredCharacterClasses =
    setOf(
        Character.LOWERCASE_LETTER.toInt(),
        Character.UPPERCASE_LETTER.toInt(),
        Character.DECIMAL_DIGIT_NUMBER.toInt(),
    )

class NotEmptyPasswordValidator : ConstraintValidator<NotEmptyPassword, String> {
    override fun isValid(
        value: String?,
        context: ConstraintValidatorContext,
    ): Boolean {
        if (value == null || value.isNotEmpty()) return true
        context.reject(FieldViolation(FieldErrorCode.REQUIRED))
        return false
    }
}
