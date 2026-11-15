package com.plcoding.cardigan.common.api.web

import com.plcoding.cardigan.common.api.ProblemResponseWriter
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.unit.DataSize
import org.springframework.web.filter.OncePerRequestFilter
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.Charset

/**
 * @property maxRequestBodySize the body limit for every request.
 * @property maxRequestBodySizePerPath higher limits for exact request paths.
 */
@ConfigurationProperties("cardigan.web")
data class WebProperties(
    val maxRequestBodySize: DataSize,
    val maxRequestBodySizePerPath: Map<String, DataSize> = emptyMap(),
)

/**
 * Thrown while reading a body past the limit. It's an [IOException], so JSON parsing passes it on
 * as a cause of the read failure instead of reporting a malformed body.
 */
class PayloadTooLargeException : IOException("Request body exceeds the size limit")

/**
 * Enforces the body size limit. A `Content-Length` above the limit is answered with 413 at once.
 * Otherwise the body is counted while it's read, which also covers chunked bodies.
 */
class RequestSizeLimitFilter(
    private val properties: WebProperties,
    private val problemResponseWriter: ProblemResponseWriter,
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val path = request.requestURI.removePrefix(request.contextPath)
        val limit = (properties.maxRequestBodySizePerPath[path] ?: properties.maxRequestBodySize).toBytes()
        if (request.contentLengthLong > limit) {
            problemResponseWriter.write(response, ErrorCode.PAYLOAD_TOO_LARGE)
            return
        }
        chain.doFilter(LimitedRequest(request, limit), response)
    }

    private class LimitedRequest(request: HttpServletRequest, private val limit: Long) :
        HttpServletRequestWrapper(request) {

        private val stream by lazy { LimitedInputStream(request.inputStream, limit) }

        override fun getInputStream(): ServletInputStream = stream

        override fun getReader(): BufferedReader =
            BufferedReader(InputStreamReader(stream, characterEncoding?.let(Charset::forName) ?: Charsets.UTF_8))
    }

    private class LimitedInputStream(
        private val delegate: ServletInputStream,
        private val limit: Long,
    ) : ServletInputStream() {

        private var count = 0L

        override fun read(): Int {
            val b = delegate.read()
            if (b != -1) count(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = delegate.read(b, off, len)
            if (n > 0) count(n)
            return n
        }

        private fun count(n: Int) {
            count += n
            if (count > limit) throw PayloadTooLargeException()
        }

        override fun isFinished(): Boolean = delegate.isFinished

        override fun isReady(): Boolean = delegate.isReady

        override fun setReadListener(readListener: ReadListener) = delegate.setReadListener(readListener)
    }
}

@Configuration(proxyBeanMethods = false)
class RequestSizeLimitConfig {

    /** Runs right after the security chain, so a missing token (401) wins over a large body (413). */
    @Bean
    fun requestSizeLimitFilter(
        properties: WebProperties,
        problemResponseWriter: ProblemResponseWriter,
    ): FilterRegistrationBean<RequestSizeLimitFilter> =
        FilterRegistrationBean(RequestSizeLimitFilter(properties, problemResponseWriter)).apply {
            order = SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1
        }
}
