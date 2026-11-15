package com.plcoding.cardigan.common

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * A UTC clock that tests set and move by hand. Like the production clock, it ticks in whole
 * milliseconds.
 */
class MutableClock(start: Instant = Instant.parse("2026-11-15T09:15:30.123Z")) : Clock() {

    @Volatile
    private var current: Instant = start.truncatedTo(ChronoUnit.MILLIS)

    override fun instant(): Instant = current

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock =
        throw UnsupportedOperationException("MutableClock is always UTC")

    fun set(instant: Instant) {
        current = instant.truncatedTo(ChronoUnit.MILLIS)
    }

    fun advanceBy(duration: Duration) {
        set(current.plus(duration))
    }
}
