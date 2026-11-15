package com.plcoding.cardigan.common.api.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

@Configuration(proxyBeanMethods = false)
class WebConfig : WebMvcConfigurer {

    /** Responses are always JSON, whatever the client accepts, so there is never a 406. */
    override fun configureContentNegotiation(configurer: ContentNegotiationConfigurer) {
        configurer.ignoreAcceptHeader(true).defaultContentType(MediaType.APPLICATION_JSON)
    }

    @Bean
    fun explicitMethodsRegistrations(): WebMvcRegistrations = object : WebMvcRegistrations {
        override fun getRequestMappingHandlerMapping(): RequestMappingHandlerMapping =
            ExplicitMethodsHandlerMapping()
    }
}

/**
 * Spring MVC answers HEAD for every GET mapping and OPTIONS for every path. Here both count as
 * wrong methods (404 `ROUTE_NOT_FOUND`) unless a mapping declares them.
 */
private class ExplicitMethodsHandlerMapping : RequestMappingHandlerMapping() {

    override fun getMatchingMapping(info: RequestMappingInfo, request: HttpServletRequest): RequestMappingInfo? {
        val match = super.getMatchingMapping(info, request) ?: return null
        val declared = info.methodsCondition.methods
        if (request.method == RequestMethod.HEAD.name && declared.isNotEmpty() && RequestMethod.HEAD !in declared) {
            return null
        }
        return match
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
