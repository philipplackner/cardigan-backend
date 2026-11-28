package com.plcoding.cardigan.common.api.web

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.WebDataBinder
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import org.springframework.web.util.ServletRequestPathUtils
import java.util.UUID

class WebConfigTest {
    @Suppress("unused")
    class Endpoints {
        fun handle() = Unit
    }

    private val mapping = WebConfig().explicitMethodsRegistrations().requestMappingHandlerMapping!!
    private val config = RequestMappingInfo.BuilderConfiguration().apply { patternParser = mapping.patternParser }
    private val handle = Endpoints::class.java.getMethod("handle")

    private fun register(
        path: String,
        vararg methods: RequestMethod,
    ) = mapping.registerMapping(
        RequestMappingInfo
            .paths(path)
            .methods(*methods)
            .options(config)
            .build(),
        Endpoints(),
        handle,
    )

    private fun handlerFor(
        method: String,
        path: String,
    ): HandlerMethod? {
        val request = MockHttpServletRequest(method, path)
        ServletRequestPathUtils.parseAndCache(request)
        return mapping.getHandler(request)?.handler as HandlerMethod?
    }

    @Test
    fun `declared method matches`() {
        register("/items", RequestMethod.GET)

        assertThat(handlerFor("GET", "/items")?.method).isEqualTo(handle)
    }

    @Test
    fun `HEAD on a GET mapping finds no handler`() {
        register("/items", RequestMethod.GET)

        assertThat(handlerFor("HEAD", "/items")).isNull()
    }

    @Test
    fun `HEAD matches when the mapping declares it`() {
        register("/items", RequestMethod.GET, RequestMethod.HEAD)

        assertThat(handlerFor("HEAD", "/items")?.method).isEqualTo(handle)
    }

    @Test
    fun `HEAD matches a mapping without declared methods`() {
        register("/items")

        assertThat(handlerFor("HEAD", "/items")?.method).isEqualTo(handle)
    }

    @Test
    fun `OPTIONS on a mapped path is a wrong method`() {
        register("/items", RequestMethod.GET)

        assertFailure { handlerFor("OPTIONS", "/items") }.isInstanceOf<HttpRequestMethodNotSupportedException>()
    }

    @Test
    fun `unmapped path has no handler`() {
        register("/items", RequestMethod.GET)

        assertThat(handlerFor("OPTIONS", "/other")).isNull()
        assertThat(handlerFor("GET", "/other")).isNull()
    }

    @Test
    fun `data binders convert only canonical UUIDs`() {
        val binder = WebDataBinder(null)
        CanonicalUuidBinderAdvice().registerCanonicalUuidEditor(binder)
        val editor = binder.findCustomEditor(UUID::class.java, null)

        assertThat(editor).isNotNull()
        editor!!.asText = "0B6A1F43-58C2-4D0E-9A35-3F1C9E2B7D10"
        assertThat(editor.value).isEqualTo(UUID.fromString("0b6a1f43-58c2-4d0e-9a35-3f1c9e2b7d10"))
        assertFailure { editor.asText = "1-2-3-4-5" }.isInstanceOf<IllegalArgumentException>()
    }
}
