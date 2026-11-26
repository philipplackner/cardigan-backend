package com.plcoding.cardigan.common.api.web

import com.plcoding.cardigan.common.domain.text.parseCanonicalUuid
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.format.FormatterRegistry
import org.springframework.http.MediaType
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.WebDataBinder
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.InitBinder
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.beans.PropertyEditorSupport
import java.util.UUID

@Configuration(proxyBeanMethods = false)
class WebConfig : WebMvcConfigurer {

    /** Responses are always JSON, whatever the client accepts, so there is never a 406. */
    override fun configureContentNegotiation(configurer: ContentNegotiationConfigurer) {
        configurer.ignoreAcceptHeader(true).defaultContentType(MediaType.APPLICATION_JSON)
    }

    override fun addFormatters(registry: FormatterRegistry) {
        registry.addConverter(CanonicalUuidConverter())
    }

    @Bean
    fun explicitMethodsRegistrations(): WebMvcRegistrations = object : WebMvcRegistrations {
        override fun getRequestMappingHandlerMapping(): RequestMappingHandlerMapping =
            ExplicitMethodsHandlerMapping()
    }
}

/**
 * Converts path and query values to UUIDs. Replaces Spring's converter, which uses
 * `UUID.fromString` and so accepts short forms such as `1-2-3-4-5`.
 */
class CanonicalUuidConverter : Converter<String, UUID> {

    override fun convert(source: String): UUID =
        parseCanonicalUuid(source) ?: throw IllegalArgumentException("Not a canonical UUID")
}

/**
 * Applies [CanonicalUuidConverter] in every data binder as well. Without it, a binder whose
 * conversion failed falls back to Spring's default `UUIDEditor`, which accepts the short forms again.
 */
@ControllerAdvice
class CanonicalUuidBinderAdvice {

    @InitBinder
    fun registerCanonicalUuidEditor(binder: WebDataBinder) {
        binder.registerCustomEditor(UUID::class.java, CanonicalUuidEditor())
    }
}

private class CanonicalUuidEditor : PropertyEditorSupport() {

    private val converter = CanonicalUuidConverter()

    override fun setAsText(text: String) {
        value = converter.convert(text)
    }
}

/**
 * Spring MVC answers HEAD for every GET mapping and OPTIONS for every path. Here both count as
 * wrong methods (404 `ROUTE_NOT_FOUND`) unless a mapping declares them.
 */
private class ExplicitMethodsHandlerMapping : RequestMappingHandlerMapping() {

    override fun getMatchingMapping(info: RequestMappingInfo, request: HttpServletRequest): RequestMappingInfo? {
        val declared = info.methodsCondition.methods
        if (request.method == RequestMethod.HEAD.name && declared.isNotEmpty() && RequestMethod.HEAD !in declared) {
            return null
        }
        return super.getMatchingMapping(info, request)
    }

    override fun handleNoMatch(
        infos: Set<RequestMappingInfo>,
        lookupPath: String,
        request: HttpServletRequest,
    ): HandlerMethod? {
        val handler = super.handleNoMatch(infos, lookupPath, request)
        if (handler != null && request.method == RequestMethod.OPTIONS.name) {
            throw HttpRequestMethodNotSupportedException(request.method)
        }
        return handler
    }
}
