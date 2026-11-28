package com.plcoding.cardigan.common.infra.security

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isNotNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import java.time.Clock
import java.util.Base64
import javax.crypto.SecretKey

@ExtendWith(OutputCaptureExtension::class)
class JwtSecretStartupTest {
    @TestConfiguration(proxyBeanMethods = false)
    @Import(JwtConfig::class)
    class JwtOnlyConfig {
        @Bean
        fun clock(): Clock = Clock.systemUTC()
    }

    private fun start(secret: String?): ConfigurableApplicationContext {
        val properties =
            buildMap<String, Any> {
                put("cardigan.security.jwt.access-token-ttl", "15m")
                secret?.let { put("cardigan.security.jwt.secret", it) }
            }
        return SpringApplicationBuilder(JwtOnlyConfig::class.java)
            .web(WebApplicationType.NONE)
            .properties(properties)
            .run()
    }

    private fun secretOf(bytes: Int): String =
        Base64.getEncoder().encodeToString(
            ByteArray(bytes) {
                (it * 31 + 17).toByte()
            },
        )

    private fun assertStartupFails(
        secret: String?,
        problem: String,
        output: CapturedOutput,
    ) {
        assertFailure { start(secret).close() }
        assertThat(output.all).contains(problem)
        if (!secret.isNullOrBlank()) {
            assertThat(output.all).doesNotContain(secret.trim())
        }
    }

    @Test
    fun `missing secret fails startup`(output: CapturedOutput) {
        assumeTrue(System.getenv("JWT_SECRET") == null)

        assertStartupFails(null, "JWT_SECRET is missing", output)
    }

    @Test
    fun `unresolved placeholder counts as missing`(output: CapturedOutput) {
        assumeTrue(System.getenv("JWT_SECRET") == null)

        assertStartupFails("\${JWT_SECRET}", "JWT_SECRET is missing", output)
    }

    @Test
    fun `blank secret fails startup`(output: CapturedOutput) {
        assertStartupFails("   ", "JWT_SECRET is missing", output)
    }

    @Test
    fun `non-base64 secret fails startup without printing it`(output: CapturedOutput) {
        assertStartupFails("not*base64!", "JWT_SECRET is not valid base64", output)
    }

    @Test
    fun `url-safe alphabet is not base64`(output: CapturedOutput) {
        val urlSafe = Base64.getUrlEncoder().encodeToString(ByteArray(32) { -1 })

        assertStartupFails(urlSafe, "JWT_SECRET is not valid base64", output)
    }

    @Test
    fun `inner whitespace is not base64`(output: CapturedOutput) {
        val secret = secretOf(32)

        assertStartupFails(
            secret.substring(0, 10) + " " + secret.substring(10),
            "JWT_SECRET is not valid base64",
            output,
        )
    }

    @Test
    fun `31-byte secret fails startup without printing it`(output: CapturedOutput) {
        assertStartupFails(secretOf(31), "JWT_SECRET is too short", output)
    }

    @Test
    fun `32-byte secret starts`() {
        start(secretOf(32)).use { assertThat(it.getBean(SecretKey::class.java)).isNotNull() }
    }

    @Test
    fun `32-byte secret without padding starts`() {
        start(secretOf(32).trimEnd('=')).use { assertThat(it.getBean(SecretKey::class.java)).isNotNull() }
    }

    @Test
    fun `32-byte secret with a trailing newline starts`() {
        start(secretOf(32) + "\n").use { assertThat(it.getBean(SecretKey::class.java)).isNotNull() }
    }
}
