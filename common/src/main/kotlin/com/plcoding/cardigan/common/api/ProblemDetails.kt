package com.plcoding.cardigan.common.api

import com.fasterxml.jackson.annotation.JsonInclude
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import org.springframework.http.ProblemDetail
import java.net.URI

/** One entry of `fieldErrors`. [min] and [max] are set only for limit codes. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ProblemFieldError(
    val field: String,
    val code: FieldErrorCode,
    val min: Long? = null,
    val max: Long? = null,
)

/**
 * Builds the error body for [code]. `type`, `title` and `detail` come only from [ErrorCode], never
 * from an exception message or the request.
 */
fun problemDetail(code: ErrorCode, fieldErrors: List<ProblemFieldError> = emptyList()): ProblemDetail =
    ProblemDetail.forStatus(code.status).apply {
        type = URI.create(code.type)
        title = code.title
        detail = code.detail
        setProperty("code", code.name)
        setProperty("fieldErrors", fieldErrors)
    }
