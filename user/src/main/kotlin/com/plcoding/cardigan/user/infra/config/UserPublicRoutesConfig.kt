package com.plcoding.cardigan.user.infra.config

import com.plcoding.cardigan.common.api.web.PublicRoute
import com.plcoding.cardigan.common.api.web.PublicRoutes
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod

@Configuration(proxyBeanMethods = false)
class UserPublicRoutesConfig {

    /** Logout is public too, so it works with an expired access token. */
    @Bean
    fun userPublicRoutes() = PublicRoutes(
        PublicRoute(HttpMethod.POST, "/api/v1/auth/register"),
        PublicRoute(HttpMethod.POST, "/api/v1/auth/login"),
        PublicRoute(HttpMethod.POST, "/api/v1/auth/refresh"),
        PublicRoute(HttpMethod.POST, "/api/v1/auth/logout"),
    )
}
