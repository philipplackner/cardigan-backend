package com.plcoding.cardigan.api.security

import com.plcoding.cardigan.common.api.web.API_BASE_PATH
import com.plcoding.cardigan.common.api.web.PublicRouteRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.savedrequest.NullRequestCache
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher

/**
 * Every route under `/api/v1` needs a valid access token except the public routes modules register.
 * Everything outside `/api/v1` is open, which is why every controller mapping must use the prefix.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        publicRoutes: PublicRouteRegistry,
        authenticationEntryPoint: AuthenticationEntryPoint,
        bearerTokenResolver: BearerTokenResolver,
    ): SecurityFilterChain {
        http {
            csrf { disable() }
            formLogin { disable() }
            httpBasic { disable() }
            logout { disable() }
            requestCache { requestCache = NullRequestCache() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            authorizeHttpRequests {
                publicRoutes.routes.forEach { route ->
                    authorize(PathPatternRequestMatcher.withDefaults().matcher(route.method, route.path), permitAll)
                }
                authorize("$API_BASE_PATH/**", authenticated)
                authorize(anyRequest, permitAll)
            }
            oauth2ResourceServer {
                this.authenticationEntryPoint = authenticationEntryPoint
                this.bearerTokenResolver = bearerTokenResolver
                jwt { }
            }
            exceptionHandling {
                this.authenticationEntryPoint = authenticationEntryPoint
            }
        }
        return http.build()
    }
}
