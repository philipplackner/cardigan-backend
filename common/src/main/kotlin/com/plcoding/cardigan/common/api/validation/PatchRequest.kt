package com.plcoding.cardigan.common.api.validation

import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext
import kotlin.reflect.KClass

/**
 * Base class of PATCH request DTOs. A PATCH accepts an absent property but rejects one sent as
 * `null`, which a nullable constructor property can't tell apart. A subclass is a plain class that
 * declares each body property as
 *
 * ```
 * @ValidText(min = 1, max = 100)
 * var name: String? = null
 *     set(value) { field = sent("name", value) }
 * ```
 *
 * Jackson calls a setter only for a property that is present in the JSON, also for an explicit
 * `null`. This class has no public property, so nothing of it shows up in JSON or in the OpenAPI
 * document.
 */
@NotNullWhenPresent
abstract class PatchRequest {

    private val nullProperties = mutableSetOf<String>()

    /** Records [name] as sent when [value] is null. Returns [value]. */
    protected fun <T> sent(name: String, value: T?): T? {
        if (value == null) nullProperties += name
        return value
    }

    /** The names of the properties that were sent as JSON `null`. */
    fun nullProperties(): Set<String> = nullProperties
}

/** Every property of a [PatchRequest] that was sent as `null` is `REQUIRED` at that property's path. */
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

    override fun isValid(value: PatchRequest?, context: ConstraintValidatorContext): Boolean {
        val names = value?.nullProperties().orEmpty()
        if (names.isEmpty()) return true
        context.disableDefaultConstraintViolation()
        context.unwrap(HibernateConstraintValidatorContext::class.java)
            .withDynamicPayload(FieldViolation(FieldErrorCode.REQUIRED))
        names.forEach { name ->
            context.buildConstraintViolationWithTemplate(context.defaultConstraintMessageTemplate)
                .addPropertyNode(name)
                .addConstraintViolation()
        }
        return false
    }
}
