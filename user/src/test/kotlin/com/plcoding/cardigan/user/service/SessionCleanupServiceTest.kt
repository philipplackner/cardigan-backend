package com.plcoding.cardigan.user.service

import assertk.assertThat
import assertk.assertions.containsExactly
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.plcoding.cardigan.common.MutableClock
import com.plcoding.cardigan.user.domain.model.RefreshTokenTransport
import com.plcoding.cardigan.user.domain.model.Session
import com.plcoding.cardigan.user.fake.FakeRefreshTokenRepository
import com.plcoding.cardigan.user.fake.FakeSessionRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.UUID

class SessionCleanupServiceTest {

    private val clock = MutableClock()
    private val sessions = FakeSessionRepository(FakeRefreshTokenRepository())
    private val logger = LoggerFactory.getLogger(SessionCleanupService::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>().also {
        it.start()
        logger.addAppender(it)
    }

    @AfterEach
    fun detachAppender() {
        logger.detachAppender(appender)
    }

    @Test
    fun `cleanup logs how many sessions it deleted`() {
        repeat(2) {
            sessions.save(Session(UUID.randomUUID(), UUID.randomUUID(), RefreshTokenTransport.BODY, clock.instant()))
        }

        SessionCleanupService(sessions, clock).deleteEndedSessions()

        assertThat(appender.list.map { it.formattedMessage }).containsExactly("Deleted 2 ended sessions")
    }
}
