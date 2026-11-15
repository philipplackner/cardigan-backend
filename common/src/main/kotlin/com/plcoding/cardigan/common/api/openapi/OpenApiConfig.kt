package com.plcoding.cardigan.common.api.openapi

import com.plcoding.cardigan.common.api.web.PublicRouteRegistry
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    /** One relative server, so nothing in the document depends on the host or port. */
    @Bean
    fun cardiganOpenApi(): OpenAPI = OpenAPI()
        .info(Info().title("Cardigan API").version("v1"))
        .servers(listOf(Server().url("/")))
        .components(
            Components().addSecuritySchemes(
                BEARER_AUTH,
                SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"),
            ),
        )

    @Bean
    fun cardiganOpenApiCustomizer(
        @Qualifier("requestMappingHandlerMapping") handlerMapping: ObjectProvider<RequestMappingHandlerMapping>,
        publicRoutes: PublicRouteRegistry,
    ) = CardiganOpenApiCustomizer(handlerMapping, publicRoutes)

    companion object {
        const val BEARER_AUTH = "bearerAuth"
    }
}
