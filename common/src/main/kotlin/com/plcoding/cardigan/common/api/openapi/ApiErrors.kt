package com.plcoding.cardigan.common.api.openapi

import com.plcoding.cardigan.common.domain.exception.ErrorCode

/**
 * Documents error responses an endpoint can return on top of the defaults (500 everywhere, 401 on
 * protected routes, and 400, 413 and 415 on endpoints with a request body). Every code must have
 * [status] as its status.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
@MustBeDocumented
annotation class ApiErrors(
    val status: Int,
    vararg val codes: ErrorCode,
)
