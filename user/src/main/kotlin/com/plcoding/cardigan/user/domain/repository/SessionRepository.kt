package com.plcoding.cardigan.user.domain.repository

import com.plcoding.cardigan.user.domain.model.Session
import java.time.Instant
import java.util.UUID

interface SessionRepository {
    fun save(session: Session): Session

    fun findById(id: UUID): Session?

    /** Revokes the session unless it already is revoked. Returns whether this call revoked it. */
    fun revoke(
        id: UUID,
        at: Instant,
    ): Boolean

    /**
     * Deletes every ended session with all its refresh tokens and returns how many sessions went. A
     * session is ended when it is revoked or has no unrotated token that is still valid at [now].
     */
    fun deleteEnded(now: Instant): Int
}
