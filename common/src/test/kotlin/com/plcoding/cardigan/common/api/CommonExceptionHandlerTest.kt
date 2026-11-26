package com.plcoding.cardigan.common.api

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.plcoding.cardigan.common.api.validation.RequestValidationException
import com.plcoding.cardigan.common.api.validation.Required
import com.plcoding.cardigan.common.api.validation.ValidText
import com.plcoding.cardigan.common.api.web.PayloadTooLargeException
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import com.plcoding.cardigan.common.domain.exception.FieldOutOfRangeException
import com.plcoding.cardigan.common.domain.exception.InvalidCredentialsException
import com.plcoding.cardigan.common.domain.exception.InvalidRefreshTokenException
import com.plcoding.cardigan.common.domain.exception.RateLimitedException
import com.plcoding.cardigan.common.domain.exception.ResourceNotFoundException
import com.plcoding.cardigan.common.domain.exception.UnauthenticatedException
import com.plcoding.cardigan.common.domain.exception.UniquenessConflictException
import com.plcoding.cardigan.common.fake.LogCapture
import jakarta.validation.ConstraintViolation
import jakarta.validation.Validation
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.core.MethodParameter
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mock.http.MockHttpInputMessage
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import org.springframework.validation.method.MethodValidationResult
import org.springframework.validation.method.ParameterValidationResult
import org.springframework.web.ErrorResponseException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingPathVariableException
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.net.URI
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommonExceptionHandlerTest {

    private val handler = CommonExceptionHandler()
    private val request = ServletWebRequest(MockHttpServletRequest())
    private val validatorFactory = Validation.buildDefaultValidatorFactory()

    @AfterAll
    fun close() = validatorFactory.close()

    data class Body(
        @Required
        val name: String?,
        @ValidText(max = 3)
        val title: String?,
    )

    @Suppress("unused", "UNUSED_PARAMETER")
    class Endpoints {
        fun handle(@PathVariable id: UUID, @RequestParam limit: Int, @RequestBody body: Body) = Unit
    }

    private val handle = Endpoints::class.java.declaredMethods.single { it.name == "handle" }
    private val pathParameter = MethodParameter(handle, 0)
    private val queryParameter = MethodParameter(handle, 1)
    private val bodyParameter = MethodParameter(handle, 2)

    private val brokenBody = Body(name = null, title = "toolong")
    private val brokenBodyFieldErrors = listOf(
        ProblemFieldError("name", FieldErrorCode.REQUIRED),
        ProblemFieldError("title", FieldErrorCode.TOO_LONG, max = 3),
    )

    private fun violationsOf(body: Body): Set<ConstraintViolation<Body>> = validatorFactory.validator.validate(body)

    private fun bindingErrorsOf(body: Body): List<ObjectError> = violationsOf(body).map { violation ->
        FieldError("body", violation.propertyPath.toString(), "invalid").apply { wrap(violation) }
    }

    private fun handle(ex: Exception): ResponseEntity<Any> = handler.handleException(ex, request)!!

    private fun assertProblem(response: ResponseEntity<*>, code: ErrorCode, fieldErrors: List<ProblemFieldError> = emptyList()) {
        assertThat(response.statusCode.value()).isEqualTo(code.status)
        assertThat(response.headers.contentType).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON)
        val body = response.body as ProblemDetail
        assertThat(body.status).isEqualTo(code.status)
        assertThat(body.type).isEqualTo(URI.create(code.type))
        assertThat(body.title).isEqualTo(code.title)
        assertThat(body.detail).isEqualTo(code.detail)
        assertThat(body.instance).isNull()
        assertThat(body.properties!!["code"]).isEqualTo(code.name)
        assertThat(body.properties!!["fieldErrors"]).isEqualTo(fieldErrors)
    }

    @Test
    fun `out-of-range field is VALIDATION_FAILED with one OUT_OF_RANGE error carrying min and max`() {
        val response = handler.handleFieldOutOfRange(FieldOutOfRangeException("position", min = 1, max = 2))

        assertProblem(
            response,
            ErrorCode.VALIDATION_FAILED,
            listOf(ProblemFieldError("position", FieldErrorCode.OUT_OF_RANGE, min = 1, max = 2)),
        )
    }

    @Test
    fun `exception message never reaches the response`() {
        val response = handler.handleFieldOutOfRange(FieldOutOfRangeException("position", 0, 0, "secret-marker"))

        assertThat(response.body!!.detail).isEqualTo(ErrorCode.VALIDATION_FAILED.detail)
    }

    @Test
    fun `request validation failure carries its field errors`() {
        val response = handler.handleRequestValidation(RequestValidationException(brokenBodyFieldErrors))

        assertProblem(response, ErrorCode.VALIDATION_FAILED, brokenBodyFieldErrors)
    }

    @Test
    fun `missing resource is NOT_FOUND`() {
        assertProblem(handler.handleNotFound(ResourceNotFoundException()), ErrorCode.NOT_FOUND)
    }

    @Test
    fun `uniqueness conflict is CONFLICT with ALREADY_USED on the field`() {
        val response = handler.handleConflict(UniquenessConflictException("email"))

        assertProblem(response, ErrorCode.CONFLICT, listOf(ProblemFieldError("email", FieldErrorCode.ALREADY_USED)))
    }

    @Test
    fun `unauthenticated request is UNAUTHENTICATED with a bearer challenge`() {
        val response = handler.handleUnauthenticated(UnauthenticatedException())

        assertProblem(response, ErrorCode.UNAUTHENTICATED)
        assertThat(response.headers[HttpHeaders.WWW_AUTHENTICATE]).isEqualTo(listOf("Bearer"))
    }

    @Test
    fun `invalid credentials are INVALID_CREDENTIALS without a bearer challenge`() {
        val response = handler.handleInvalidCredentials(InvalidCredentialsException())

        assertProblem(response, ErrorCode.INVALID_CREDENTIALS)
        assertThat(response.headers[HttpHeaders.WWW_AUTHENTICATE]).isNull()
    }

    @Test
    fun `invalid refresh token is INVALID_REFRESH_TOKEN without a bearer challenge`() {
        val response = handler.handleInvalidRefreshToken(InvalidRefreshTokenException())

        assertProblem(response, ErrorCode.INVALID_REFRESH_TOKEN)
        assertThat(response.headers[HttpHeaders.WWW_AUTHENTICATE]).isNull()
    }

    @Test
    fun `rate limit is RATE_LIMITED with Retry-After in seconds`() {
        val response = handler.handleRateLimited(RateLimitedException(retryAfterSeconds = 17))

        assertProblem(response, ErrorCode.RATE_LIMITED)
        assertThat(response.headers[HttpHeaders.RETRY_AFTER]).isEqualTo(listOf("17"))
    }

    @Test
    fun `body over the limit is PAYLOAD_TOO_LARGE`() {
        assertProblem(handler.handlePayloadTooLarge(PayloadTooLargeException()), ErrorCode.PAYLOAD_TOO_LARGE)
    }

    @Test
    fun `unexpected exception is INTERNAL_ERROR and logged with its stack trace`() {
        val ex = IllegalStateException("boom")

        val response = LogCapture(CommonExceptionHandler::class.java).use { logs ->
            handler.handleUnexpected(ex).also {
                assertThat(logs.errors).hasSize(1)
                assertThat(logs.errors.single().throwableProxy.message).isEqualTo("boom")
            }
        }

        assertProblem(response, ErrorCode.INTERNAL_ERROR)
    }

    @Test
    fun `unexpected exception caused by a body over the limit is PAYLOAD_TOO_LARGE and not logged`() {
        val ex = RuntimeException(IllegalStateException(PayloadTooLargeException()))

        val response = LogCapture(CommonExceptionHandler::class.java).use { logs ->
            handler.handleUnexpected(ex).also { assertThat(logs.errors).isEmpty() }
        }

        assertProblem(response, ErrorCode.PAYLOAD_TOO_LARGE)
    }

    @Test
    fun `exception that reports itself as its own cause ends the cause search`() {
        val selfCaused = object : RuntimeException() {
            override val cause: Throwable get() = this
        }

        assertProblem(handler.handleUnexpected(selfCaused), ErrorCode.INTERNAL_ERROR)
    }

    @Test
    fun `unknown route is ROUTE_NOT_FOUND`() {
        assertProblem(handle(NoResourceFoundException(HttpMethod.GET, "/nope", "nope")), ErrorCode.ROUTE_NOT_FOUND)
        assertProblem(handle(NoHandlerFoundException("GET", "/nope", HttpHeaders())), ErrorCode.ROUTE_NOT_FOUND)
    }

    @Test
    fun `wrong method is ROUTE_NOT_FOUND, not 405`() {
        assertProblem(handle(HttpRequestMethodNotSupportedException("PATCH")), ErrorCode.ROUTE_NOT_FOUND)
    }

    @Test
    fun `unsupported content type is UNSUPPORTED_MEDIA_TYPE`() {
        assertProblem(handle(HttpMediaTypeNotSupportedException("text/plain")), ErrorCode.UNSUPPORTED_MEDIA_TYPE)
    }

    @Test
    fun `unreadable body is MALFORMED_REQUEST`() {
        val ex = HttpMessageNotReadableException("bad json", IllegalArgumentException(), MockHttpInputMessage(ByteArray(0)))

        assertProblem(handle(ex), ErrorCode.MALFORMED_REQUEST)
    }

    @Test
    fun `unreadable body caused by the size limit is PAYLOAD_TOO_LARGE`() {
        val cause = IllegalStateException(PayloadTooLargeException())
        val ex = HttpMessageNotReadableException("too large", cause, MockHttpInputMessage(ByteArray(0)))

        assertProblem(handle(ex), ErrorCode.PAYLOAD_TOO_LARGE)
    }

    @Test
    fun `unconvertible path variable is NOT_FOUND`() {
        val ex = MethodArgumentTypeMismatchException("1-2-3", UUID::class.java, "id", pathParameter, null)

        assertProblem(handle(ex), ErrorCode.NOT_FOUND)
    }

    @Test
    fun `unconvertible query parameter is MALFORMED_REQUEST`() {
        val ex = MethodArgumentTypeMismatchException("x", Int::class.java, "limit", queryParameter, null)

        assertProblem(handle(ex), ErrorCode.MALFORMED_REQUEST)
    }

    @Test
    fun `blank path variable is NOT_FOUND`() {
        assertProblem(handle(MissingPathVariableException("id", pathParameter, true)), ErrorCode.NOT_FOUND)
    }

    @Test
    fun `path variable missing from the mapping is INTERNAL_ERROR and logged`() {
        val response = LogCapture(CommonExceptionHandler::class.java).use { logs ->
            handle(MissingPathVariableException("id", pathParameter, false)).also {
                assertThat(logs.errors).hasSize(1)
            }
        }

        assertProblem(response, ErrorCode.INTERNAL_ERROR)
    }

    @Test
    fun `invalid request body is VALIDATION_FAILED with every field error`() {
        val bindingResult = BeanPropertyBindingResult(brokenBody, "body")
        bindingErrorsOf(brokenBody).forEach(bindingResult::addError)

        val response = handle(MethodArgumentNotValidException(bodyParameter, bindingResult))

        assertProblem(response, ErrorCode.VALIDATION_FAILED, brokenBodyFieldErrors)
    }

    @Test
    fun `invalid request body without constraint violations is INTERNAL_ERROR and logged`() {
        val bindingResult = BeanPropertyBindingResult(brokenBody, "body")
        bindingResult.addError(ObjectError("body", "invalid"))

        val response = LogCapture(CommonExceptionHandler::class.java).use { logs ->
            handle(MethodArgumentNotValidException(bodyParameter, bindingResult)).also {
                assertThat(logs.errors).hasSize(1)
            }
        }

        assertProblem(response, ErrorCode.INTERNAL_ERROR)
    }

    @Test
    fun `invalid method parameter is VALIDATION_FAILED with every field error`() {
        val result = ParameterValidationResult(bodyParameter, brokenBody, bindingErrorsOf(brokenBody), null, null, null) { error, _ -> error }

        val response = handle(HandlerMethodValidationException(MethodValidationResult.create(Endpoints(), handle, listOf(result))))

        assertProblem(response, ErrorCode.VALIDATION_FAILED, brokenBodyFieldErrors)
    }

    @Test
    fun `framework error statuses map to their codes`() {
        val expected = mapOf(
            HttpStatus.BAD_REQUEST to ErrorCode.MALFORMED_REQUEST,
            HttpStatus.NOT_FOUND to ErrorCode.NOT_FOUND,
            HttpStatus.METHOD_NOT_ALLOWED to ErrorCode.ROUTE_NOT_FOUND,
            HttpStatus.CONTENT_TOO_LARGE to ErrorCode.PAYLOAD_TOO_LARGE,
            HttpStatus.UNSUPPORTED_MEDIA_TYPE to ErrorCode.UNSUPPORTED_MEDIA_TYPE,
        )

        expected.forEach { (status, code) -> assertProblem(handle(ErrorResponseException(status)), code) }
    }

    @Test
    fun `any other framework error status is INTERNAL_ERROR and logged`() {
        val ex = ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE)

        val response = LogCapture(CommonExceptionHandler::class.java).use { logs ->
            handle(ex).also { assertThat(logs.errors.single().throwableProxy).isNotNull() }
        }

        assertProblem(response, ErrorCode.INTERNAL_ERROR)
    }
}
