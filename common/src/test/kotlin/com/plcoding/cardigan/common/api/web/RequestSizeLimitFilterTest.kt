package com.plcoding.cardigan.common.api.web

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import com.plcoding.cardigan.common.api.ProblemDetailMixin
import com.plcoding.cardigan.common.api.ProblemResponseWriter
import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.ServletRequest
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Test
import org.springframework.http.ProblemDetail
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.util.unit.DataSize
import tools.jackson.databind.json.JsonMapper
import java.io.ByteArrayInputStream

class RequestSizeLimitFilterTest {

    private val jsonMapper = JsonMapper.builder().addMixIn(ProblemDetail::class.java, ProblemDetailMixin::class.java).build()
    private val filter = RequestSizeLimitFilter(
        WebProperties(
            maxRequestBodySize = DataSize.ofBytes(LIMIT.toLong()),
            maxRequestBodySizePerPath = mapOf(IMPORT_PATH to DataSize.ofBytes(IMPORT_LIMIT.toLong())),
        ),
        ProblemResponseWriter(jsonMapper),
    )

    /** A stream body without `Content-Length`, as a chunked request sends it. */
    private class StreamedRequest(
        body: ByteArray,
        private val ready: Boolean = true,
    ) : MockHttpServletRequest("POST", "/api/v1/boards") {

        private val input = FakeServletInputStream(body, ready)

        override fun getContentLengthLong(): Long = -1

        override fun getContentLength(): Int = -1

        override fun getInputStream(): ServletInputStream = input
    }

    private class FakeServletInputStream(body: ByteArray, private val ready: Boolean) : ServletInputStream() {

        private val bytes = ByteArrayInputStream(body)
        var listener: ReadListener? = null

        override fun read(): Int = bytes.read()

        override fun read(b: ByteArray, off: Int, len: Int): Int = bytes.read(b, off, len)

        override fun isFinished(): Boolean = bytes.available() == 0

        override fun isReady(): Boolean = ready

        override fun setReadListener(readListener: ReadListener) {
            listener = readListener
        }
    }

    private class CapturingChain : FilterChain {
        var request: ServletRequest? = null

        override fun doFilter(request: ServletRequest, response: jakarta.servlet.ServletResponse) {
            this.request = request
        }
    }

    private fun declaredRequest(size: Int, path: String = "/api/v1/boards") = MockHttpServletRequest("POST", path).apply {
        setContent(ByteArray(size) { 'a'.code.toByte() })
    }

    private fun passThrough(request: HttpServletRequest): HttpServletRequest {
        val chain = CapturingChain()
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, chain)
        assertThat(response.status).isEqualTo(200)
        return chain.request as HttpServletRequest
    }

    @Test
    fun `declared length at the limit passes and the whole body can be read`() {
        val request = passThrough(declaredRequest(LIMIT))

        assertThat(request.inputStream.readAllBytes().size).isEqualTo(LIMIT)
    }

    @Test
    fun `declared length one byte over the limit is answered with 413 without calling the chain`() {
        val chain = CapturingChain()
        val response = MockHttpServletResponse()

        filter.doFilter(declaredRequest(LIMIT + 1), response, chain)

        assertThat(response.status).isEqualTo(413)
        assertThat(jsonMapper.readTree(response.contentAsByteArray)["code"].asString()).isEqualTo("PAYLOAD_TOO_LARGE")
        assertThat(chain.request).isNull()
    }

    @Test
    fun `a path with its own limit uses it`() {
        val request = passThrough(declaredRequest(IMPORT_LIMIT, IMPORT_PATH))

        assertThat(request.inputStream.readAllBytes().size).isEqualTo(IMPORT_LIMIT)
    }

    @Test
    fun `the path limit is looked up without the context path`() {
        val request = declaredRequest(IMPORT_LIMIT, "/ctx$IMPORT_PATH").apply { contextPath = "/ctx" }

        assertThat(passThrough(request).inputStream.readAllBytes().size).isEqualTo(IMPORT_LIMIT)
    }

    @Test
    fun `other paths keep the default limit`() {
        val response = MockHttpServletResponse()

        filter.doFilter(declaredRequest(IMPORT_LIMIT, "/api/v1/boards"), response, CapturingChain())

        assertThat(response.status).isEqualTo(413)
    }

    @Test
    fun `streamed body at the limit reads fully byte by byte`() {
        val input = passThrough(StreamedRequest(ByteArray(LIMIT))).inputStream

        repeat(LIMIT) { assertThat(input.read()).isEqualTo(0) }
        assertThat(input.read()).isEqualTo(-1)
        assertThat(input.read()).isEqualTo(-1)
    }

    @Test
    fun `streamed body one byte over the limit fails on the byte that crosses it`() {
        val input = passThrough(StreamedRequest(ByteArray(LIMIT + 1))).inputStream
        repeat(LIMIT) { input.read() }

        assertFailure { input.read() }.isInstanceOf<PayloadTooLargeException>()
    }

    @Test
    fun `streamed body at the limit reads fully in chunks`() {
        val input = passThrough(StreamedRequest(ByteArray(LIMIT))).inputStream
        val buffer = ByteArray(4)

        var total = 0
        while (true) {
            val n = input.read(buffer, 0, buffer.size)
            if (n == -1) break
            total += n
        }

        assertThat(total).isEqualTo(LIMIT)
    }

    @Test
    fun `streamed body over the limit fails in a chunked read`() {
        val input = passThrough(StreamedRequest(ByteArray(LIMIT + 1))).inputStream

        assertFailure { input.readAllBytes() }.isInstanceOf<PayloadTooLargeException>()
    }

    @Test
    fun `the reader counts too`() {
        val reader = passThrough(StreamedRequest(ByteArray(LIMIT + 1) { 'a'.code.toByte() })).reader

        assertFailure { reader.readText() }.isInstanceOf<PayloadTooLargeException>()
    }

    @Test
    fun `the reader decodes UTF-8 when the request names no encoding`() {
        val request = StreamedRequest("é".toByteArray(Charsets.UTF_8)).apply { characterEncoding = null }

        assertThat(passThrough(request).reader.readText()).isEqualTo("é")
    }

    @Test
    fun `the reader decodes the request's character encoding`() {
        val request = StreamedRequest("é".toByteArray(Charsets.ISO_8859_1)).apply { characterEncoding = "ISO-8859-1" }

        assertThat(passThrough(request).reader.readText()).isEqualTo("é")
    }

    @Test
    fun `the limited stream is created once per request`() {
        val request = passThrough(StreamedRequest(ByteArray(LIMIT)))

        assertThat(request.inputStream).isSameInstanceAs(request.inputStream)
    }

    @Test
    fun `async state comes from the original stream`() {
        val notReady = passThrough(StreamedRequest(ByteArray(1), ready = false)).inputStream
        assertThat(notReady.isReady).isFalse()
        assertThat(notReady.isFinished).isFalse()

        val ready = passThrough(StreamedRequest(ByteArray(1), ready = true)).inputStream
        assertThat(ready.isReady).isTrue()
        ready.read()
        assertThat(ready.isFinished).isTrue()
    }

    @Test
    fun `read listener is handed to the original stream`() {
        val original = StreamedRequest(ByteArray(1))
        val listener = object : ReadListener {
            override fun onDataAvailable() = Unit
            override fun onAllDataRead() = Unit
            override fun onError(t: Throwable) = Unit
        }

        passThrough(original).inputStream.setReadListener(listener)

        assertThat((original.inputStream as FakeServletInputStream).listener).isNotNull().isSameInstanceAs(listener)
    }

    private companion object {
        const val LIMIT = 10
        const val IMPORT_LIMIT = 20
        const val IMPORT_PATH = "/api/v1/boards/import"
    }
}
