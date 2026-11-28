package com.plcoding.cardigan.common.api.validation

import com.plcoding.cardigan.common.api.ProblemFieldError
import jakarta.validation.Validator
import org.springframework.stereotype.Component

/** Field validation failed. Answered with 400 `VALIDATION_FAILED` and [fieldErrors]. */
class RequestValidationException(
    val fieldErrors: List<ProblemFieldError>,
) : RuntimeException("Request validation failed")

/**
 * Validates a request DTO on demand. Endpoints that reference resources call it after the service
 * resolved those references, so a missing reference (404) wins over field errors.
 */
@Component
class RequestValidator(
    private val validator: Validator,
) {
    fun validate(request: Any) {
        val fieldErrors = fieldErrorsOf(validator.validate(request))
        if (fieldErrors.isNotEmpty()) {
            throw RequestValidationException(fieldErrors)
        }
    }
}
