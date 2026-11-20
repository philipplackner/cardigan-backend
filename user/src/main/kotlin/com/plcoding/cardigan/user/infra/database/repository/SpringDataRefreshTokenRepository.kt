package com.plcoding.cardigan.user.infra.database.repository

import com.plcoding.cardigan.user.infra.database.entity.RefreshTokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface SpringDataRefreshTokenRepository : JpaRepository<RefreshTokenEntity, UUID> {

    fun findByTokenHash(tokenHash: String): RefreshTokenEntity?

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshTokenEntity t set t.rotatedAt = :at where t.id = :id and t.rotatedAt is null")
    fun markRotated(@Param("id") id: UUID, @Param("at") at: Instant): Int
}
