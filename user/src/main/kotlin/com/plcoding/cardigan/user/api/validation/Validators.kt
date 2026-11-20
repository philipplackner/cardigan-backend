package com.plcoding.cardigan.user.api.validation

import com.plcoding.cardigan.common.api.validation.FieldViolation
import com.plcoding.cardigan.common.api.validation.reject
import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import com.plcoding.cardigan.common.domain.text.checkText
import com.plcoding.cardigan.common.domain.text.normalizePassword
import com.plcoding.cardigan.common.domain.text.normalizeText
import com.plcoding.cardigan.user.domain.model.EMAIL_MAX_LENGTH
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

private const val ATEXT = "[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]"
private const val DOT_ATOM = "$ATEXT++(?:\\.$ATEXT++)*+"
private const val QUOTED_STRING = "\"(?:[\\x21\\x23-\\x5B\\x5D-\\x7E]|\\\\[\\x20-\\x7E])*+\""
private const val DOMAIN_LITERAL = "\\[[\\x21-\\x5A\\x5E-\\x7E]*+\\]"

// Possessive quantifiers only, so a near-match can't make the regex backtrack.
private val addrSpec = Regex("(?:$DOT_ATOM|$QUOTED_STRING)@(?:$DOT_ATOM|$DOMAIN_LITERAL)")

/** True when [text] is an RFC 5322 `addr-spec` in ASCII, without comments or folding whitespace. */
internal fun isAddrSpec(text: String): Boolean = addrSpec.matches(text)

class ValidEmailValidator : ConstraintValidator<ValidEmail, String> {

    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true
        val email = normalizeText(value)
        if (checkText(email, min = 1, max = EMAIL_MAX_LENGTH, multiline = false) != null) return true
        return isAddrSpec(email) || context.reject(FieldViolation(FieldErrorCode.INVALID_FORMAT))
    }
}

class ValidPasswordValidator : ConstraintValidator<ValidPassword, String> {

    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true
        val password = normalizePassword(value)
        val code = checkText(password, PASSWORD_MIN_LENGTH, PASSWORD_MAX_LENGTH, multiline = false)
            ?: FieldErrorCode.PASSWORD_TOO_WEAK.takeUnless { hasEveryCharacterClass(password) }
            ?: return true
        return context.reject(
            FieldViolation(
                code = code,
                min = PASSWORD_MIN_LENGTH.toLong().takeIf { code == FieldErrorCode.TOO_SHORT },
                max = PASSWORD_MAX_LENGTH.toLong().takeIf { code == FieldErrorCode.TOO_LONG },
            ),
        )
    }

    private fun hasEveryCharacterClass(password: String): Boolean {
        val types = password.codePoints().map(Character::getType).toArray().toSet()
        return REQUIRED_CLASSES.all { it in types }
    }

    private companion object {
        const val PASSWORD_MIN_LENGTH = 12
        const val PASSWORD_MAX_LENGTH = 128
        val REQUIRED_CLASSES = listOf(
            Character.LOWERCASE_LETTER.toInt(),
            Character.UPPERCASE_LETTER.toInt(),
            Character.DECIMAL_DIGIT_NUMBER.toInt(),
        )
    }
}

class NotBlankTextValidator : ConstraintValidator<NotBlankText, String> {

    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean =
        value == null || normalizeText(value).isNotEmpty() || context.reject(FieldViolation(FieldErrorCode.REQUIRED))
}

class NotEmptyPasswordValidator : ConstraintValidator<NotEmptyPassword, String> {

    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean =
        value == null || value.isNotEmpty() || context.reject(FieldViolation(FieldErrorCode.REQUIRED))
}
