package com.plcoding.cardigan

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.plcoding.cardigan.user.service.SessionCleanupService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor
import org.springframework.scheduling.config.CronTask
import org.springframework.scheduling.support.SimpleTriggerContext
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SessionCleanupE2eTest : AuthE2eTestBase() {

    @Autowired
    private lateinit var cleanupService: SessionCleanupService

    @Autowired
    private lateinit var scheduledTasks: ScheduledAnnotationBeanPostProcessor

    @Autowired
    private lateinit var environment: Environment

    @Test
    fun `cleanup is scheduled with the configured cron, next at 03_00 UTC`() {
        val task = scheduledTasks.scheduledTasks.map { it.task }.filterIsInstance<CronTask>()
            .single { it.toString().contains("SessionCleanupService.deleteEndedSessions") }

        val nextRun = task.trigger.nextExecution(
            SimpleTriggerContext(Clock.fixed(Instant.parse("2026-11-15T09:15:30Z"), ZoneOffset.UTC)),
        )

        assertThat(task.expression).isEqualTo(environment.getRequiredProperty("cardigan.user.auth.session-cleanup-cron"))
        assertThat(task.expression).isEqualTo("0 0 3 * * *")
        assertThat(nextRun).isEqualTo(Instant.parse("2026-11-16T03:00:00Z"))
    }

    @Test
    fun `refresh token of a deleted session answers 401`() {
        val token = register().assertStatus(201).refreshToken
        logoutWithBody(token).assertStatus(204)

        cleanupService.deleteEndedSessions()

        assertThat(count("sessions")).isEqualTo(0)
        assertThat(count("refresh_tokens")).isEqualTo(0)
        refreshWithBody(token).assertInvalidRefreshToken()
        logoutWithBody(token).assertStatus(204)
    }

    @Test
    fun `cleanup keeps a session that is still active`() {
        val token = register().assertStatus(201).refreshToken

        cleanupService.deleteEndedSessions()

        refreshWithBody(token).assertStatus(200)
    }
}
