package com.plcoding.cardigan.user.infra.database.repository

import com.plcoding.cardigan.user.domain.model.RefreshToken
import com.plcoding.cardigan.user.domain.repository.RefreshTokenRepository
import com.plcoding.cardigan.user.infra.database.mapper.toRefreshToken
import com.plcoding.cardigan.user.infra.database.mapper.toRefreshTokenEntity
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class JpaRefreshTokenRepository(
    private val springDataRefreshTokenRepository: SpringDataRefreshTokenRepository,
    private val springDataSessionRepository: SpringDataSessionRepository,
) : RefreshTokenRepository {
    override fun save(refreshToken: RefreshToken): RefreshToken {
        val session = springDataSessionRepository.getReferenceById(refreshToken.sessionId)
        springDataRefreshTokenRepository.save(refreshToken.toRefreshTokenEntity(session))
        return refreshToken
    }

    override fun findByTokenHash(tokenHash: String): RefreshToken? =
        springDataRefreshTokenRepository.findByTokenHash(tokenHash)?.toRefreshToken()

    override fun markRotated(
        id: UUID,
        at: Instant,
    ): Boolean = springDataRefreshTokenRepository.markRotated(id, at) == 1
}
