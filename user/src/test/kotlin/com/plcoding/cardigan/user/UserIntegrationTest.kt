package com.plcoding.cardigan.user

import com.plcoding.cardigan.common.DatabaseCleaner
import com.plcoding.cardigan.common.MutableClock
import com.plcoding.cardigan.user.infra.ratelimit.Bucket4jAuthRateLimiter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

/** Base class of `:user` module integration tests: the module's beans against a real Postgres. */
@Tag("integration")
@SpringBootTest(classes = [UserTestApplication::class], webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(UserTestConfig::class)
abstract class UserIntegrationTest {
    @Autowired
    protected lateinit var clock: MutableClock

    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var databaseCleaner: DatabaseCleaner

    @Autowired
    private lateinit var rateLimiter: Bucket4jAuthRateLimiter

    @BeforeEach
    fun setUpIntegrationTest() {
        databaseCleaner.clean("user_service")
        clock.set(START)
        // The clock was just set back, and a bucket never refills while the clock is behind it.
        rateLimiter.clear()
    }

    companion object {
        val START: Instant = Instant.parse("2026-11-15T09:15:30.123Z")
    }
}
