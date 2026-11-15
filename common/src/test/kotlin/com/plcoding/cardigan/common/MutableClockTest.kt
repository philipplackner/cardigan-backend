package com.plcoding.cardigan.common

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class MutableClockTest {

    @Test
    fun `start time is truncated to milliseconds`() {
        val clock = MutableClock(Instant.parse("2026-11-15T09:15:30.123456Z"))

        assertThat(clock.instant()).isEqualTo(Instant.parse("2026-11-15T09:15:30.123Z"))
    }

    @Test
    fun `set truncates to milliseconds`() {
        val clock = MutableClock()

        clock.set(Instant.parse("2026-11-15T09:15:30.999999999Z"))

        assertThat(clock.instant()).isEqualTo(Instant.parse("2026-11-15T09:15:30.999Z"))
    }

    @Test
    fun `time stays put until moved`() {
        val clock = MutableClock()
        val first = clock.instant()

        assertThat(clock.instant()).isEqualTo(first)
    }

    @Test
    fun `advanceBy moves forward and truncates`() {
        val clock = MutableClock(Instant.parse("2026-11-15T09:15:30.123Z"))

        clock.advanceBy(Duration.ofMinutes(16).plusNanos(500))

        assertThat(clock.instant()).isEqualTo(Instant.parse("2026-11-15T09:31:30.123Z"))
    }

    @Test
    fun `zone is UTC`() {
        assertThat(MutableClock().zone).isEqualTo(ZoneOffset.UTC)
    }
}
