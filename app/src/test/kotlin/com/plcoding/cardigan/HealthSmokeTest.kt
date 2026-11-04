package com.plcoding.cardigan

import com.plcoding.cardigan.common.PostgresTestConfig
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.client.RestTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(PostgresTestConfig::class)
class HealthSmokeTest {

    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `health returns only status UP`() {
        val client = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()

        client.get().uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .json("""{"status":"UP"}""", JsonCompareMode.STRICT)
    }
}
