package com.plcoding.cardigan.user.service

import com.plcoding.cardigan.user.domain.repository.SessionRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class SessionCleanupService(
    private val sessionRepository: SessionRepository,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Deletes sessions that were logged out, revoked after token reuse, or whose newest refresh
     * token has expired. Active sessions keep every rotated token. The delete is idempotent, so it
     * may run on several instances at once.
     */
    @Scheduled(cron = "\${cardigan.user.auth.session-cleanup-cron:$DEFAULT_CRON}", zone = "UTC")
    @Transactional
    fun deleteEndedSessions() {
        val deleted = sessionRepository.deleteEnded(clock.instant())
        log.info("Deleted {} ended sessions", deleted)
    }

    private companion object {
        const val DEFAULT_CRON = "0 0 3 * * *"
    }
}
