package com.plcoding.cardigan.common.api

import com.plcoding.cardigan.common.domain.exception.ErrorCode
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/**
 * Writes the error body for code that runs outside Spring MVC, such as filters, the security entry
 * point and the firewall rejection handler. The body equals what [CommonExceptionHandler] returns.
 */
@Component
class ProblemResponseWriter(
    private val jsonMapper: JsonMapper,
) {

    fun write(response: HttpServletResponse, code: ErrorCode) {
        response.resetBuffer()
        response.status = code.status
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        if (code == ErrorCode.UNAUTHENTICATED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BEARER_CHALLENGE)
        }
        jsonMapper.writeValue(response.outputStream, problemDetail(code))
    }

    companion object {
        const val BEARER_CHALLENGE = "Bearer"
    }
}
