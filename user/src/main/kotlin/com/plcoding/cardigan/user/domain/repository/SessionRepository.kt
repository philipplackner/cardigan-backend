package com.plcoding.cardigan.user.domain.repository

import com.plcoding.cardigan.user.domain.model.Session
import java.time.Instant
import java.util.UUID

interface SessionRepository {

    fun save(session: Session): Session

    fun findById(id: UUID): Session?

    /** Revokes the session unless it already is revoked. Returns whether this call revoked it. */
    fun revoke(id: UUID, at: Instant): Boolean
}
