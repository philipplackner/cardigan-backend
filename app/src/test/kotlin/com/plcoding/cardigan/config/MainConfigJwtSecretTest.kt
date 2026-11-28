package com.plcoding.cardigan.config

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import com.plcoding.cardigan.common.infra.security.JwtConfig
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Import

/** The shipped `application*.yaml` files hold no default secret. */
@ExtendWith(OutputCaptureExtension::class)
class MainConfigJwtSecretTest {
    @TestConfiguration(proxyBeanMethods = false)
    @Import(ApplicationConfig::class, JwtConfig::class)
    class JwtOnlyConfig

    @Test
    fun `main config without JWT_SECRET fails startup`(output: CapturedOutput) {
        assumeTrue(System.getenv("JWT_SECRET") == null)

        assertFailure {
            SpringApplicationBuilder(JwtOnlyConfig::class.java).web(WebApplicationType.NONE).run().close()
        }

        assertThat(output.all).contains("JWT_SECRET is missing")
    }
}
