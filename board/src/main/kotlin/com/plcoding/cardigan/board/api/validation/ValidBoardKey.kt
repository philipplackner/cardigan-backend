package com.plcoding.cardigan.board.api.validation

import com.plcoding.cardigan.board.domain.model.BOARD_KEY_MAX_LENGTH
import com.plcoding.cardigan.board.domain.model.BOARD_KEY_MIN_LENGTH
import com.plcoding.cardigan.board.domain.model.checkBoardKey
import com.plcoding.cardigan.board.domain.model.normalizeBoardKey
import com.plcoding.cardigan.common.api.validation.FieldViolation
import com.plcoding.cardigan.common.api.validation.reject
import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * The text, after `normalizeBoardKey`, is a board key: 2 to 5 letters `A` to `Z`. Length is checked
 * before the characters, and every wrong character is `INVALID_FORMAT`. The value itself isn't
 * changed; mappers normalize it again.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [ValidBoardKeyValidator::class])
annotation class ValidBoardKey(
    val message: String = "INVALID_FORMAT",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class ValidBoardKeyValidator : ConstraintValidator<ValidBoardKey, String> {
    override fun isValid(
        value: String?,
        context: ConstraintValidatorContext,
    ): Boolean {
        if (value == null) return true
        val code = checkBoardKey(normalizeBoardKey(value)) ?: return true
        context.reject(
            FieldViolation(
                code = code,
                min = BOARD_KEY_MIN_LENGTH.toLong().takeIf { code == FieldErrorCode.TOO_SHORT },
                max = BOARD_KEY_MAX_LENGTH.toLong().takeIf { code == FieldErrorCode.TOO_LONG },
            ),
        )
        return false
    }
}
