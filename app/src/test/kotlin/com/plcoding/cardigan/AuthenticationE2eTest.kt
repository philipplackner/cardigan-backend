package com.plcoding.cardigan

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.plcoding.cardigan.common.api.ProblemErrorController
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpMethod
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.time.Duration
import java.util.UUID

class AuthenticationE2eTest : E2eTest() {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private lateinit var handlerMapping: RequestMappingHandlerMapping

    private fun HttpResult.assertUnauthenticated() {
        assertProblem(ErrorCode.UNAUTHENTICATED)
        assertThat(headers[("WWW-Authenticate")]).isEqualTo(listOf("Bearer"))
    }

    @Test
    fun `no token is 401 with a bare Bearer challenge`() {
        call(HttpMethod.GET, "/api/v1/users/me").assertUnauthenticated()
    }

    @Test
    fun `Basic scheme is 401`() {
        call(HttpMethod.GET, "/api/v1/users/me", headers = mapOf("Authorization" to "Basic YW5uOnNlY3JldA=="))
            .assertUnauthenticated()
    }

    @Test
    fun `malformed bearer value is 401`() {
        call(HttpMethod.GET, "/api/v1/users/me", headers = mapOf("Authorization" to "Bearer not a token"))
            .assertUnauthenticated()
    }

    @Test
    fun `tampered token is 401 with no error parameters`() {
        val token = signIn().token
        val (header, payload, signature) = token.split(".")
        val tampered = "$header.$payload.${if (signature.first() == 'A') 'B' else 'A'}${signature.drop(1)}"

        call(HttpMethod.GET, "/api/v1/users/me", tampered).assertUnauthenticated()
    }

    @Test
    fun `token expires after its lifetime`() {
        val token = signIn().token

        clock.advanceBy(Duration.ofMinutes(16))

        call(HttpMethod.GET, "/api/v1/users/me", token).assertUnauthenticated()
    }

    @Test
    fun `token is valid until 1 ms before its expiry and invalid at it`() {
        val user = userFixtures.createUser()
        val issued = accessTokenIssuer.issue(user.id)

        clock.set(issued.expiresAt.minusMillis(1))
        assertThat(call(HttpMethod.GET, "/api/v1/users/me", issued.token).status).isEqualTo(200)

        clock.set(issued.expiresAt)
        call(HttpMethod.GET, "/api/v1/users/me", issued.token).assertUnauthenticated()
    }

    @Test
    fun `unknown API path without a token is 401`() {
        call(HttpMethod.GET, "/api/v1/does-not-exist").assertUnauthenticated()
    }

    @Test
    fun `unknown API path with a token is ROUTE_NOT_FOUND`() {
        call(HttpMethod.GET, "/api/v1/does-not-exist", signIn().token).assertProblem(ErrorCode.ROUTE_NOT_FOUND)
    }

    @Test
    fun `operational path without a token is 404, also with a broken Authorization header`() {
        call(HttpMethod.GET, "/actuator/env").assertProblem(ErrorCode.ROUTE_NOT_FOUND)
        call(HttpMethod.GET, "/actuator/env", "broken").assertProblem(ErrorCode.ROUTE_NOT_FOUND)
    }

    @Test
    fun `health with an expired token is 200`() {
        val token = signIn().token
        clock.advanceBy(Duration.ofMinutes(16))

        val result = call(HttpMethod.GET, "/actuator/health", token)

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json()).isEqualTo(mapOf("status" to "UP"))
    }

    @Test
    fun `public route ignores an invalid token`() {
        val result = call(HttpMethod.GET, "/api/v1/test/public", "broken")

        assertThat(result.status).isEqualTo(200)
    }

    @Test
    fun `public route is public only for its method`() {
        call(HttpMethod.POST, "/api/v1/test/public").assertUnauthenticated()
    }

    @Test
    fun `request user is the token subject, even without a user record`() {
        val userId = UUID.randomUUID()

        val result = call(HttpMethod.GET, "/api/v1/test/whoami", tokenWithoutUser(userId))

        assertThat(result.status).isEqualTo(200)
        assertThat(result.json()).isEqualTo(mapOf("userId" to userId.toString()))
    }

    @Test
    fun `authenticated request sets no cookie`() {
        val result = call(HttpMethod.GET, "/api/v1/users/me", signIn().token)

        assertThat(result.status).isEqualTo(200)
        assertThat(result.headers.getFirst("Set-Cookie")).isNull()
    }

    @Test
    fun `every controller mapping is under the API base path`() {
        val outside = handlerMapping.handlerMethods
            .filterValues { it.beanType != ProblemErrorController::class.java }
            .keys
            .flatMap { it.patternValues }
            .filterNot { it.startsWith("/api/v1/") }

        assertThat(outside).isEmpty()
    }
}
