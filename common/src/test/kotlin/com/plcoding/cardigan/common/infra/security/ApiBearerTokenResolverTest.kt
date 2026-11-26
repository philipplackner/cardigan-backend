package com.plcoding.cardigan.common.infra.security

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.plcoding.cardigan.common.api.web.PublicRoute
import com.plcoding.cardigan.common.api.web.PublicRoutes
import com.plcoding.cardigan.common.fake.publicRouteRegistryOf
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockHttpServletRequest

class ApiBearerTokenResolverTest {

    private val resolver = ApiBearerTokenResolver(
        publicRouteRegistryOf(PublicRoutes(PublicRoute(HttpMethod.POST, "/api/v1/auth/login"))),
    )

    private fun requestWithToken(method: String, uri: String) = MockHttpServletRequest(method, uri).apply {
        addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-123")
    }

    @Test
    fun `protected API route resolves the bearer token`() {
        assertThat(resolver.resolve(requestWithToken("GET", "/api/v1/boards"))).isEqualTo("token-123")
    }

    @Test
    fun `public route ignores the bearer token`() {
        assertThat(resolver.resolve(requestWithToken("POST", "/api/v1/auth/login"))).isNull()
    }

    @Test
    fun `path outside the API ignores the bearer token`() {
        assertThat(resolver.resolve(requestWithToken("GET", "/error"))).isNull()
    }

    @Test
    fun `protected route without a token resolves nothing`() {
        assertThat(resolver.resolve(MockHttpServletRequest("GET", "/api/v1/boards"))).isNull()
    }
}
