package com.plcoding.cardigan.user.api.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * The text, after `normalizeText`, matches the `domain-user` reference email pattern, or
 * `INVALID_FORMAT`. Empty, too long and invalid-character values pass here, because
 * `@ValidText` on the same field reports them.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [ValidEmailValidator::class])
annotation class ValidEmail(
    val message: String = "INVALID_FORMAT",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

/**
 * The password, after `normalizePassword`, follows the `domain-user` rule: 12 to 128 code points,
 * no invalid character, and at least one lowercase letter (Ll), one uppercase letter (Lu) and one
 * decimal digit (Nd), or `PASSWORD_TOO_WEAK`.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [ValidPasswordValidator::class])
annotation class ValidPassword(
    val message: String = "INVALID_PASSWORD",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

/** The text is not empty after `normalizeText`, or `REQUIRED`. No other rule applies. */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [NotBlankTextValidator::class])
annotation class NotBlankText(
    val message: String = "REQUIRED",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

/** The password is not the empty string, or `REQUIRED`. It isn't trimmed: spaces are a value. */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [NotEmptyPasswordValidator::class])
annotation class NotEmptyPassword(
    val message: String = "REQUIRED",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)
