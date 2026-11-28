package com.plcoding.cardigan.user.infra.database.repository

import com.plcoding.cardigan.user.domain.model.Session
import com.plcoding.cardigan.user.domain.repository.SessionRepository
import com.plcoding.cardigan.user.infra.database.mapper.toSession
import com.plcoding.cardigan.user.infra.database.mapper.toSessionEntity
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class JpaSessionRepository(
    private val springDataSessionRepository: SpringDataSessionRepository,
    private val springDataUserRepository: SpringDataUserRepository,
) : SessionRepository {
    override fun save(session: Session): Session {
        val user = springDataUserRepository.getReferenceById(session.userId)
        springDataSessionRepository.save(session.toSessionEntity(user))
        return session
    }

    override fun findById(id: UUID): Session? = springDataSessionRepository.findByIdOrNull(id)?.toSession()

    override fun revoke(
        id: UUID,
        at: Instant,
    ): Boolean = springDataSessionRepository.revoke(id, at) == 1

    override fun deleteEnded(now: Instant): Int = springDataSessionRepository.deleteEnded(now)
}
