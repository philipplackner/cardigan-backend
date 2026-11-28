package com.plcoding.cardigan.user.service

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.plcoding.cardigan.common.MutableClock
import com.plcoding.cardigan.user.fake.FakeRefreshTokenRepository
import com.plcoding.cardigan.user.fake.FakeSessionRepository
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor
import org.springframework.scheduling.config.CronTask
import org.springframework.scheduling.support.SimpleTriggerContext
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SessionCleanupScheduleTest {
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    class SchedulingConfig {
        @Bean
        fun sessionRepository() = FakeSessionRepository(FakeRefreshTokenRepository())

        @Bean
        fun clock() = MutableClock()

        @Bean
        fun sessionCleanupService(
            sessionRepository: FakeSessionRepository,
            clock: MutableClock,
        ) = SessionCleanupService(sessionRepository, clock)
    }

    private val runner = ApplicationContextRunner().withUserConfiguration(SchedulingConfig::class.java)

    private fun cronTaskOf(processor: ScheduledAnnotationBeanPostProcessor): CronTask =
        processor.scheduledTasks
            .map { it.task }
            .filterIsInstance<CronTask>()
            .single()

    private fun nextRunAfter(
        task: CronTask,
        instant: String,
    ): Instant? = task.trigger.nextExecution(SimpleTriggerContext(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)))

    @Test
    fun `default schedule runs daily at 03_00 UTC`() {
        runner.run { context ->
            assertThat(context.startupFailure).isNull()
            val task = cronTaskOf(context.getBean(ScheduledAnnotationBeanPostProcessor::class.java))

            assertThat(task.expression).isEqualTo("0 0 3 * * *")
            assertThat(nextRunAfter(task, "2026-11-15T09:15:30Z")).isEqualTo(Instant.parse("2026-11-16T03:00:00Z"))
            assertThat(nextRunAfter(task, "2026-11-15T02:59:59Z")).isEqualTo(Instant.parse("2026-11-15T03:00:00Z"))
            assertThat(nextRunAfter(task, "2026-11-15T03:00:00Z")).isEqualTo(Instant.parse("2026-11-16T03:00:00Z"))
        }
    }

    @Test
    fun `schedule is in UTC also across a daylight saving change elsewhere`() {
        runner.run { context ->
            val task = cronTaskOf(context.getBean(ScheduledAnnotationBeanPostProcessor::class.java))

            assertThat(nextRunAfter(task, "2026-03-29T00:30:00Z")).isEqualTo(Instant.parse("2026-03-29T03:00:00Z"))
            assertThat(nextRunAfter(task, "2026-07-01T12:00:00Z")).isEqualTo(Instant.parse("2026-07-02T03:00:00Z"))
        }
    }

    @Test
    fun `schedule comes from the configuration`() {
        runner.withPropertyValues("cardigan.user.auth.session-cleanup-cron=0 30 4 * * *").run { context ->
            val task = cronTaskOf(context.getBean(ScheduledAnnotationBeanPostProcessor::class.java))

            assertThat(task.expression).isEqualTo("0 30 4 * * *")
            assertThat(nextRunAfter(task, "2026-11-15T09:15:30Z")).isEqualTo(Instant.parse("2026-11-16T04:30:00Z"))
        }
    }

    @Test
    fun `job deletes ended sessions as of the current time`() {
        val clock = MutableClock()
        val sessions = FakeSessionRepository(FakeRefreshTokenRepository())

        SessionCleanupService(sessions, clock).deleteEndedSessions()

        assertThat(sessions.deleteEndedCalls).isEqualTo(listOf(clock.instant()))
    }
}
