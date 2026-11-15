package com.plcoding.cardigan

import com.plcoding.cardigan.common.domain.exception.ErrorCode
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.test.json.JsonCompareMode

class HealthSmokeTest : E2eTest() {

    @Test
    fun `health returns only status UP`() {
        client.get().uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .json("""{"status":"UP"}""", JsonCompareMode.STRICT)
    }

    @Test
    fun `health under the API base path without a token is 401`() {
        call(HttpMethod.GET, "/api/v1/actuator/health").assertProblem(ErrorCode.UNAUTHENTICATED)
    }

    @Test
    fun `health under the API base path with a token is ROUTE_NOT_FOUND`() {
        call(HttpMethod.GET, "/api/v1/actuator/health", signIn().token).assertProblem(ErrorCode.ROUTE_NOT_FOUND)
    }
}
