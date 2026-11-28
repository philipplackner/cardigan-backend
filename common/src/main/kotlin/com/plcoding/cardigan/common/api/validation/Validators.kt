package com.plcoding.cardigan.common.api.validation

import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import com.plcoding.cardigan.common.domain.text.checkText
import com.plcoding.cardigan.common.domain.text.normalizeText
import com.plcoding.cardigan.common.domain.text.parseCanonicalUuid
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import java.math.BigInteger

class RequiredValidator : ConstraintValidator<Required, Any> {
    override fun isValid(
        value: Any?,
        context: ConstraintValidatorContext,
    ): Boolean {
        if (value != null) return true
        context.reject(FieldViolation(FieldErrorCode.REQUIRED))
        return false
    }
}

class ValidTextValidator : ConstraintValidator<ValidText, String> {
    private lateinit var annotation: ValidText

    override fun initialize(constraintAnnotation: ValidText) {
        annotation = constraintAnnotation
    }

    override fun isValid(
        value: String?,
        context: ConstraintValidatorContext,
    ): Boolean {
        if (value == null) return true
        val code =
            checkText(normalizeText(value), annotation.min, annotation.max, annotation.multiline)
                ?: return true
        context.reject(
            FieldViolation(
                code = code,
                min = annotation.min.toLong().takeIf { code == FieldErrorCode.TOO_SHORT },
                max = annotation.max.toLong().takeIf { code == FieldErrorCode.TOO_LONG },
            ),
        )
        return false
    }
}

class ValidUuidValidator : ConstraintValidator<ValidUuid, String> {
    override fun isValid(
        value: String?,
        context: ConstraintValidatorContext,
    ): Boolean {
        if (value == null || parseCanonicalUuid(value) != null) return true
        context.reject(FieldViolation(FieldErrorCode.INVALID_FORMAT))
        return false
    }
}

class ValidEnumValidator : ConstraintValidator<ValidEnum, String> {
    private lateinit var names: Set<String>

    override fun initialize(constraintAnnotation: ValidEnum) {
        names =
            constraintAnnotation.value.java.enumConstants
                .map { it.name }
                .toSet()
    }

    override fun isValid(
        value: String?,
        context: ConstraintValidatorContext,
    ): Boolean {
        if (value == null || value in names) return true
        context.reject(FieldViolation(FieldErrorCode.INVALID_VALUE))
        return false
    }
}

class ValidRangeValidator : ConstraintValidator<ValidRange, BigInteger> {
    private lateinit var annotation: ValidRange

    override fun initialize(constraintAnnotation: ValidRange) {
        annotation = constraintAnnotation
    }

    override fun isValid(
        value: BigInteger?,
        context: ConstraintValidatorContext,
    ): Boolean {
        if (value == null) return true
        if (value >= BigInteger.valueOf(annotation.min) && value <= BigInteger.valueOf(annotation.max)) return true
        context.reject(FieldViolation(FieldErrorCode.OUT_OF_RANGE, min = annotation.min, max = annotation.max))
        return false
    }
}
