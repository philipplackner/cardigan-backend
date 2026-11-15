package com.plcoding.cardigan.common.api.security

import com.plcoding.cardigan.common.api.ProblemResponseWriter
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.firewall.RequestRejectedHandler

@Configuration(proxyBeanMethods = false)
class SecurityWebConfig {

    /** Answers every authentication failure with 401 `UNAUTHENTICATED` and `WWW-Authenticate: Bearer`. */
    @Bean
    fun bearerAuthenticationEntryPoint(writer: ProblemResponseWriter): AuthenticationEntryPoint =
        AuthenticationEntryPoint { _, response, _ -> writer.write(response, ErrorCode.UNAUTHENTICATED) }

    /** Paths the firewall rejects (`;`, `//`, encoded slashes, ...) never reach a route. */
    @Bean
    fun requestRejectedHandler(writer: ProblemResponseWriter): RequestRejectedHandler =
        RequestRejectedHandler { _, response, _ -> writer.write(response, ErrorCode.ROUTE_NOT_FOUND) }
}
