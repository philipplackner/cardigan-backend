package com.plcoding.cardigan

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod

class UserE2eTest : E2eTest() {

    @Test
    fun `current user gets exactly id, email and createdAt`() {
        val (user, token) = signIn("ann@example.com")

        val result = authenticatedClient(token).get().uri("/api/v1/users/me").exchange()
            .expectStatus().isOk
            .returnResult(String::class.java)
        val body = HttpResult(200, result.responseHeaders, result.responseBody!!).json()

        assertThat(body).isEqualTo(
            mapOf(
                "id" to user.id.toString().lowercase(),
                "email" to "ann@example.com",
                "createdAt" to "2026-11-15T09:15:30.123Z",
            ),
        )
    }

    @Test
    fun `token for a user with no record is 401 with a Bearer challenge`() {
        val result = call(HttpMethod.GET, "/api/v1/users/me", tokenWithoutUser())

        result.assertProblem(ErrorCode.UNAUTHENTICATED)
        assertThat(result.headers["WWW-Authenticate"]).isEqualTo(listOf("Bearer"))
    }

    @Test
    fun `no token is 401`() {
        call(HttpMethod.GET, "/api/v1/users/me").assertProblem(ErrorCode.UNAUTHENTICATED)
    }
}
