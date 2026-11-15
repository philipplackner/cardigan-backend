package com.plcoding.cardigan.common.api

import com.plcoding.cardigan.common.domain.exception.ErrorCode
import jakarta.servlet.RequestDispatcher
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.boot.webmvc.error.ErrorController
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping

/**
 * Replaces Boot's error controller, so failures outside Spring MVC (for example an exception thrown
 * in a filter) also get a problem-details body. A direct call without an error gets 404.
 */
@Controller
class ProblemErrorController : ErrorController {

    private val log = LoggerFactory.getLogger(javaClass)

    @RequestMapping("\${server.error.path:\${error.path:/error}}")
    fun error(request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        val code = when (request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) as Int?) {
            null, 404 -> ErrorCode.ROUTE_NOT_FOUND
            400 -> ErrorCode.MALFORMED_REQUEST
            413 -> ErrorCode.PAYLOAD_TOO_LARGE
            else -> {
                log.error("Request failed", request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) as Throwable?)
                ErrorCode.INTERNAL_ERROR
            }
        }
        return problem(code)
    }
}
