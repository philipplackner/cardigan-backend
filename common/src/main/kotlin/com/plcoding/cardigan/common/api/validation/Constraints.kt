package com.plcoding.cardigan.common.api.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * The field must be present and not null, reported as `REQUIRED`. Request DTO properties are
 * nullable, so every missing field is reported at once. The OpenAPI document lists only these
 * properties as required.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [RequiredValidator::class])
annotation class Required(
    val message: String = "REQUIRED",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

/**
 * The text, after `normalizeText`, follows the `text-length` rules: no invalid characters and a
 * length in code points between [min] and [max]. Line breaks and tabs are allowed only when
 * [multiline] is set. The value itself isn't changed; mappers normalize it again.
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [ValidTextValidator::class])
annotation class ValidText(
    val min: Int = 0,
    val max: Int,
    val multiline: Boolean = false,
    val message: String = "INVALID_TEXT",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

/** The string is a UUID in canonical form, in any letter case, or `INVALID_FORMAT`. */
@Target(AnnotationTarget.FIELD, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [ValidUuidValidator::class])
annotation class ValidUuid(
    val message: String = "INVALID_FORMAT",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

/** The string is the name of a constant of [value], or `INVALID_VALUE`. */
@Target(AnnotationTarget.FIELD, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [ValidEnumValidator::class])
annotation class ValidEnum(
    val value: KClass<out Enum<*>>,
    val message: String = "INVALID_VALUE",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

/** The integer lies between [min] and [max], both included, or `OUT_OF_RANGE` with both limits. */
@Target(AnnotationTarget.FIELD, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [ValidRangeValidator::class])
annotation class ValidRange(
    val min: Long,
    val max: Long,
    val message: String = "OUT_OF_RANGE",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)
