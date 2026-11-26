package com.plcoding.cardigan.common.api.web

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.plcoding.cardigan.common.fake.publicRouteRegistryOf
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockHttpServletRequest

class PublicRoutesTest {

    private val login = PublicRoute(HttpMethod.POST, "/api/v1/auth/login")
    private val register = PublicRoute(HttpMethod.POST, "/api/v1/auth/register")
    private val health = PublicRoute(HttpMethod.GET, "/api/v1/health")
    private val registry = publicRouteRegistryOf(PublicRoutes(login, register), PublicRoutes(health))

    private fun request(method: String, uri: String, contextPath: String = "") =
        MockHttpServletRequest(method, uri).apply { this.contextPath = contextPath }

    @Test
    fun `routes of every module are merged in bean order`() {
        assertThat(registry.routes).containsExactly(login, register, health)
    }

    @Test
    fun `a route exposes its method and path to the security chain`() {
        assertThat(registry.routes.map { it.method to it.path }).containsExactly(
            HttpMethod.POST to "/api/v1/auth/login",
            HttpMethod.POST to "/api/v1/auth/register",
            HttpMethod.GET to "/api/v1/health",
        )
    }

    @Test
    fun `no module routes means no public route`() {
        assertThat(publicRouteRegistryOf().routes).isEmpty()
    }

    @Test
    fun `route list keeps the routes it was built from`() {
        assertThat(PublicRoutes(listOf(login, health)).routes).containsExactly(login, health)
    }

    @Test
    fun `exact method and path is public`() {
        assertThat(registry.isPublic(request("POST", "/api/v1/auth/login"))).isTrue()
        assertThat(registry.isPublic(request("GET", "/api/v1/health"))).isTrue()
    }

    @Test
    fun `same path with another method is not public`() {
        assertThat(registry.isPublic(request("GET", "/api/v1/auth/login"))).isFalse()
    }

    @Test
    fun `another path with the same method is not public`() {
        assertThat(registry.isPublic(request("POST", "/api/v1/auth/logout"))).isFalse()
        assertThat(registry.isPublic(request("POST", "/api/v1/auth/login/"))).isFalse()
    }

    @Test
    fun `the context path is not part of the route`() {
        assertThat(registry.isPublic(request("POST", "/ctx/api/v1/auth/login", contextPath = "/ctx"))).isTrue()
    }

    @Test
    fun `only the API base path and paths below it are API requests`() {
        assertThat(request("GET", "/api/v1").isApiRequest()).isTrue()
        assertThat(request("GET", "/api/v1/boards").isApiRequest()).isTrue()
        assertThat(request("GET", "/ctx/api/v1/boards", contextPath = "/ctx").isApiRequest()).isTrue()
        assertThat(request("GET", "/api/v10").isApiRequest()).isFalse()
        assertThat(request("GET", "/api").isApiRequest()).isFalse()
        assertThat(request("GET", "/error").isApiRequest()).isFalse()
    }
}
