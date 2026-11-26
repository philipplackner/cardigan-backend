package com.plcoding.cardigan.common.api.validation

import com.plcoding.cardigan.common.api.ProblemFieldError
import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.ConstraintViolation
import jakarta.validation.ElementKind
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext
import org.hibernate.validator.engine.HibernateConstraintViolation

/** The field error a constraint reports, carried as the Hibernate Validator dynamic payload. */
data class FieldViolation(
    val code: FieldErrorCode,
    val min: Long? = null,
    val max: Long? = null,
)

/**
 * Reports [violation] as the field error of the constraint being checked. The validator must still
 * return false.
 */
fun ConstraintValidatorContext.reject(violation: FieldViolation) {
    unwrap(HibernateConstraintValidatorContext::class.java).withDynamicPayload(violation)
}

/**
 * Turns constraint violations into field errors: one per field, keeping the first broken rule in
 * `api-errors` precedence, ordered by field.
 */
fun fieldErrorsOf(violations: Collection<ConstraintViolation<*>>): List<ProblemFieldError> =
    violations
        .map { it.jsonPath() to it.fieldViolation() }
        .groupBy({ it.first }, { it.second })
        .map { (field, candidates) ->
            val first = candidates.minBy { it.code.ordinal }
            ProblemFieldError(field = field, code = first.code, min = first.min, max = first.max)
        }
        .sortedBy { it.field }

private fun ConstraintViolation<*>.fieldViolation(): FieldViolation =
    unwrap(HibernateConstraintViolation::class.java).getDynamicPayload(FieldViolation::class.java)
        ?: FieldViolation(FieldErrorCode.INVALID_VALUE)

private fun ConstraintViolation<*>.jsonPath(): String = buildString {
    for (node in propertyPath) {
        if (node.isInIterable && node.index != null) {
            append('[').append(node.index).append(']')
        }
        if (node.kind == ElementKind.PROPERTY) {
            if (isNotEmpty()) append('.')
            append(node.name)
        }
    }
}
