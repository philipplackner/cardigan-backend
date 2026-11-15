package com.plcoding.cardigan.config

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class ApplicationConfigTest {

    private val clock = ApplicationConfig().clock()

    @Test
    fun `every instant has a zero sub-millisecond part`() {
        repeat(1000) {
            assertThat(clock.instant().nano % 1_000_000).isEqualTo(0)
        }
    }

    @Test
    fun `zone is UTC`() {
        assertThat(clock.zone).isEqualTo(ZoneOffset.UTC)
    }

    @Test
    fun `follows the system time`() {
        val drift = Duration.between(clock.instant(), Instant.now()).abs()

        assertThat(drift).isLessThan(Duration.ofSeconds(1))
    }
}
