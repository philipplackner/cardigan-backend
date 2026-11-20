package com.plcoding.cardigan.user.infra.config

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.plcoding.cardigan.user.service.AuthProperties
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import java.time.Duration

class AuthPropertiesBindingTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(
        AuthProperties::class,
        PasswordHashingProperties::class,
        AuthRateLimitProperties::class,
    )
    class PropertiesConfig

    private val runner = ApplicationContextRunner().withUserConfiguration(PropertiesConfig::class.java)

    private fun assertStartupFails(vararg properties: String) {
        runner.withPropertyValues(*properties).run { context ->
            assertThat(context.startupFailure, properties.joinToString()).isNotNull()
        }
    }

    @Test
    fun `defaults apply when nothing is configured`() {
        runner.run { context ->
            assertThat(context.startupFailure).isNull()
            assertThat(context.getBean(AuthProperties::class.java))
                .isEqualTo(AuthProperties(Duration.ofDays(30), "0 0 3 * * *"))
            val hashing = context.getBean(PasswordHashingProperties::class.java)
            assertThat(hashing.bcryptCost).isEqualTo(12)
            assertThat(hashing.maxConcurrentHashes)
                .isEqualTo((Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1))
            assertThat(context.getBean(AuthRateLimitProperties::class.java)).isEqualTo(
                AuthRateLimitProperties(
                    maxTrackedEmails = 100_000,
                    login = AuthRateLimitProperties.Limit(5, Duration.ofMinutes(1)),
                    register = AuthRateLimitProperties.Limit(5, Duration.ofMinutes(1)),
                ),
            )
        }
    }

    @Test
    fun `configured values are bound from the documented keys`() {
        runner.withPropertyValues(
            "cardigan.user.auth.refresh-token-ttl=7d",
            "cardigan.user.auth.session-cleanup-cron=0 30 4 * * *",
            "cardigan.user.auth.bcrypt-cost=5",
            "cardigan.user.auth.max-concurrent-hashes=3",
            "cardigan.user.auth.rate-limit.max-tracked-emails=2",
            "cardigan.user.auth.rate-limit.login.capacity=7",
            "cardigan.user.auth.rate-limit.login.period=30s",
            "cardigan.user.auth.rate-limit.register.capacity=9",
            "cardigan.user.auth.rate-limit.register.period=2m",
        ).run { context ->
            assertThat(context.startupFailure).isNull()
            assertThat(context.getBean(AuthProperties::class.java))
                .isEqualTo(AuthProperties(Duration.ofDays(7), "0 30 4 * * *"))
            assertThat(context.getBean(PasswordHashingProperties::class.java))
                .isEqualTo(PasswordHashingProperties(5, 3))
            assertThat(context.getBean(AuthRateLimitProperties::class.java)).isEqualTo(
                AuthRateLimitProperties(
                    maxTrackedEmails = 2,
                    login = AuthRateLimitProperties.Limit(7, Duration.ofSeconds(30)),
                    register = AuthRateLimitProperties.Limit(9, Duration.ofMinutes(2)),
                ),
            )
        }
    }

    @Test
    fun `cost 4 starts and cost 3 fails startup`() {
        runner.withPropertyValues("cardigan.user.auth.bcrypt-cost=4").run { context ->
            assertThat(context.startupFailure).isNull()
        }
        assertStartupFails("cardigan.user.auth.bcrypt-cost=3")
    }

    @Test
    fun `cost above the BCrypt maximum fails startup`() {
        assertStartupFails("cardigan.user.auth.bcrypt-cost=32")
    }

    @Test
    fun `non-positive capacity fails startup`() {
        assertStartupFails("cardigan.user.auth.rate-limit.login.capacity=0")
        assertStartupFails("cardigan.user.auth.rate-limit.register.capacity=-1")
    }

    @Test
    fun `non-positive max-concurrent-hashes fails startup`() {
        assertStartupFails("cardigan.user.auth.max-concurrent-hashes=0")
        assertStartupFails("cardigan.user.auth.max-concurrent-hashes=-2")
    }

    @Test
    fun `non-positive period, tracked emails and token lifetime fail startup`() {
        assertStartupFails("cardigan.user.auth.rate-limit.login.period=0s")
        assertStartupFails("cardigan.user.auth.rate-limit.max-tracked-emails=0")
        assertStartupFails("cardigan.user.auth.refresh-token-ttl=0s")
    }

    @Test
    fun `invalid cron expression fails startup`() {
        assertStartupFails("cardigan.user.auth.session-cleanup-cron=every night")
    }
}
