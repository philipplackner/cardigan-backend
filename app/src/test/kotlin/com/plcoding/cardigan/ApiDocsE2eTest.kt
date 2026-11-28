package com.plcoding.cardigan

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod

class ApiDocsE2eTest : E2eTest() {
    @Test
    fun `no API docs are served at runtime`() {
        listOf("/v3/api-docs", "/swagger-ui/index.html", "/v3/api-docs.yaml").forEach { path ->
            assertThat(call(HttpMethod.GET, path).status, path).isEqualTo(404)
        }
    }
}
