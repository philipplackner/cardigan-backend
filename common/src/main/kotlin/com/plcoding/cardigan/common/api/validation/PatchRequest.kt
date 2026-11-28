package com.plcoding.cardigan.common.api.validation

import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext
import kotlin.reflect.KClass

/**
 * Base type of PATCH request DTOs, which must tell an absent property from one sent as `null`.
 *
 * A subclass is a plain class, not a data class, and declares each field in its body, with the usual
 * constraints on the field:
 * ```
 * @ValidText(min = 1, max = 30)
 * var name: String? = null
 *     set(value) { field = sent("name", value) }
 * ```
 * Jackson calls a setter only for a property that is present, also for an explicit `null`. This
 * class has no public property, so it adds nothing to the JSON or to the OpenAPI document.
 */
@NotNullWhenPresent
abstract class PatchRequest {
    private val nullPropertyNames = linkedSetOf<String>()

    /** Called by every property setter. Remembers [name] when it was sent as `null`. */
    protected fun <T> sent(
        name: String,
        value: T?,
    ): T? {
        if (value == null) nullPropertyNames += name else nullPropertyNames -= name
        return value
    }

    /** The names of the properties that were sent as `null`. */
    fun nullProperties(): Set<String> = nullPropertyNames.toSet()
}

/**
 * Every property of a [PatchRequest] that was sent as `null` is reported as `REQUIRED` at that
 * property's path. An absent property passes.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [NotNullWhenPresentValidator::class])
annotation class NotNullWhenPresent(
    val message: String = "REQUIRED",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class NotNullWhenPresentValidator : ConstraintValidator<NotNullWhenPresent, PatchRequest> {
    override fun isValid(
        value: PatchRequest?,
        context: ConstraintValidatorContext,
    ): Boolean {
        val names = value?.nullProperties().orEmpty()
        if (names.isEmpty()) return true

        context.disableDefaultConstraintViolation()
        context
            .unwrap(HibernateConstraintValidatorContext::class.java)
            .withDynamicPayload(FieldViolation(FieldErrorCode.REQUIRED))
        names.forEach { name ->
            context
                .buildConstraintViolationWithTemplate(context.defaultConstraintMessageTemplate)
                .addPropertyNode(name)
                .addConstraintViolation()
        }
        return false
    }
}
