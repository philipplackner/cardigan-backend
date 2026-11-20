package com.plcoding.cardigan.board

import com.plcoding.cardigan.common.DatabaseCleaner
import com.plcoding.cardigan.common.MutableClock
import com.plcoding.cardigan.common.api.validation.RequestValidator
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate

/**
 * What module integration tests need on top of [BoardTestApplication], which scans only the
 * `:board` package: the clock, the database cleaner and the request validator from `:common`.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(RequestValidator::class)
class BoardTestConfig {

    @Bean
    fun testClock(): MutableClock = MutableClock()

    @Bean
    fun databaseCleaner(jdbcTemplate: JdbcTemplate) = DatabaseCleaner(jdbcTemplate)
}
