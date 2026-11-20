package com.plcoding.cardigan.common.api

import com.plcoding.cardigan.common.api.validation.RequestValidationException
import com.plcoding.cardigan.common.api.validation.fieldErrorsOf
import com.plcoding.cardigan.common.api.web.PayloadTooLargeException
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import com.plcoding.cardigan.common.domain.exception.FieldOutOfRangeException
import com.plcoding.cardigan.common.domain.exception.ResourceNotFoundException
import com.plcoding.cardigan.common.domain.exception.UnauthenticatedException
import com.plcoding.cardigan.common.domain.exception.UniquenessConflictException
import jakarta.validation.ConstraintViolation
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.ObjectError
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingPathVariableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * Maps every exception that reaches Spring MVC to the shared error model. Feature modules don't add
 * handlers; they throw subclasses of the base exceptions in `common.domain.exception`.
 */
@RestControllerAdvice
class CommonExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val response = when (ex) {
            is NoResourceFoundException,
            is NoHandlerFoundException,
            is HttpRequestMethodNotSupportedException,
            -> problem(ErrorCode.ROUTE_NOT_FOUND)
            is HttpMediaTypeNotSupportedException -> problem(ErrorCode.UNSUPPORTED_MEDIA_TYPE)
            is HttpMessageNotReadableException ->
                if (ex.hasCause<PayloadTooLargeException>()) {
                    problem(ErrorCode.PAYLOAD_TOO_LARGE)
                } else {
                    problem(ErrorCode.MALFORMED_REQUEST)
                }
            is MethodArgumentTypeMismatchException ->
                if (ex.parameter.hasParameterAnnotation(PathVariable::class.java)) {
                    problem(ErrorCode.NOT_FOUND)
                } else {
                    problem(ErrorCode.MALFORMED_REQUEST)
                }
            // A blank path value converts to null, so it arrives here and not as a type mismatch.
            is MissingPathVariableException ->
                if (ex.isMissingAfterConversion) problem(ErrorCode.NOT_FOUND) else problemForStatus(ex, statusCode)
            is MethodArgumentNotValidException ->
                validationFailed(ex.bindingResult.allErrors.mapNotNull { it.constraintViolation() })
            is HandlerMethodValidationException ->
                validationFailed(
                    ex.parameterValidationResults
                        .flatMap { it.resolvableErrors }
                        .mapNotNull { (it as? ObjectError)?.constraintViolation() },
                )
            else -> problemForStatus(ex, statusCode)
        }
        @Suppress("UNCHECKED_CAST")
        return response as ResponseEntity<Any>
    }

    @ExceptionHandler(RequestValidationException::class)
    fun handleRequestValidation(ex: RequestValidationException): ResponseEntity<ProblemDetail> =
        problem(ErrorCode.VALIDATION_FAILED, ex.fieldErrors)

    @ExceptionHandler(FieldOutOfRangeException::class)
    fun handleFieldOutOfRange(ex: FieldOutOfRangeException): ResponseEntity<ProblemDetail> =
        problem(
            ErrorCode.VALIDATION_FAILED,
            listOf(ProblemFieldError(ex.field, FieldErrorCode.OUT_OF_RANGE, min = ex.min, max = ex.max)),
        )

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleNotFound(ex: ResourceNotFoundException): ResponseEntity<ProblemDetail> =
        problem(ErrorCode.NOT_FOUND)

    @ExceptionHandler(UniquenessConflictException::class)
    fun handleConflict(ex: UniquenessConflictException): ResponseEntity<ProblemDetail> =
        problem(ErrorCode.CONFLICT, listOf(ProblemFieldError(ex.field, FieldErrorCode.ALREADY_USED)))

    @ExceptionHandler(UnauthenticatedException::class)
    fun handleUnauthenticated(ex: UnauthenticatedException): ResponseEntity<ProblemDetail> =
        problem(ErrorCode.UNAUTHENTICATED)

    @ExceptionHandler(PayloadTooLargeException::class)
    fun handlePayloadTooLarge(ex: PayloadTooLargeException): ResponseEntity<ProblemDetail> =
        problem(ErrorCode.PAYLOAD_TOO_LARGE)

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ProblemDetail> {
        if (ex.hasCause<PayloadTooLargeException>()) {
            return problem(ErrorCode.PAYLOAD_TOO_LARGE)
        }
        log.error("Unhandled exception", ex)
        return problem(ErrorCode.INTERNAL_ERROR)
    }

    private fun problemForStatus(ex: Exception, status: HttpStatusCode): ResponseEntity<ProblemDetail> {
        val code = when (status.value()) {
            400 -> ErrorCode.MALFORMED_REQUEST
            404 -> ErrorCode.NOT_FOUND
            405 -> ErrorCode.ROUTE_NOT_FOUND
            413 -> ErrorCode.PAYLOAD_TOO_LARGE
            415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE
            else -> {
                log.error("Unhandled exception", ex)
                ErrorCode.INTERNAL_ERROR
            }
        }
        return problem(code)
    }

    private fun validationFailed(violations: List<ConstraintViolation<*>>): ResponseEntity<ProblemDetail> {
        val fieldErrors = fieldErrorsOf(violations)
        if (fieldErrors.isEmpty()) {
            log.error("Validation failed without constraint violations")
            return problem(ErrorCode.INTERNAL_ERROR)
        }
        return problem(ErrorCode.VALIDATION_FAILED, fieldErrors)
    }

    private fun ObjectError.constraintViolation(): ConstraintViolation<*>? =
        if (contains(ConstraintViolation::class.java)) unwrap(ConstraintViolation::class.java) else null
}

internal fun problem(
    code: ErrorCode,
    fieldErrors: List<ProblemFieldError> = emptyList(),
): ResponseEntity<ProblemDetail> {
    val builder = ResponseEntity.status(code.status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
    if (code == ErrorCode.UNAUTHENTICATED) {
        builder.header(HttpHeaders.WWW_AUTHENTICATE, ProblemResponseWriter.BEARER_CHALLENGE)
    }
    return builder.body(problemDetail(code, fieldErrors))
}

private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean =
    generateSequence(this) { cause -> cause.cause?.takeIf { it !== cause } }.any { it is T }
