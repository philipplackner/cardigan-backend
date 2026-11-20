package com.plcoding.cardigan.user

import com.plcoding.cardigan.common.DatabaseCleaner
import com.plcoding.cardigan.common.MutableClock
import com.plcoding.cardigan.common.PostgresTestConfig
import com.plcoding.cardigan.user.fake.FakeAccessTokenIssuer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate

/** What `:user` module integration tests need from outside the module. */
@TestConfiguration(proxyBeanMethods = false)
@Import(PostgresTestConfig::class)
class UserTestConfig {

    @Bean
    fun clock(): MutableClock = MutableClock()

    @Bean
    fun databaseCleaner(jdbcTemplate: JdbcTemplate): DatabaseCleaner = DatabaseCleaner(jdbcTemplate)

    @Bean
    fun accessTokenIssuer(clock: MutableClock): FakeAccessTokenIssuer = FakeAccessTokenIssuer(clock)
}
