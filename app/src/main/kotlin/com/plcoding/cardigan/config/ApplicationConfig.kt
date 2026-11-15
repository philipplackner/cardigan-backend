package com.plcoding.cardigan.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.Duration

@Configuration(proxyBeanMethods = false)
class ApplicationConfig {

    /**
     * The only source of the current time. Ticks in whole milliseconds, so stored timestamps
     * equal the values sent to clients.
     */
    @Bean
    fun clock(): Clock = Clock.tick(Clock.systemUTC(), Duration.ofMillis(1))
}
