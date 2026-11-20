package com.plcoding.cardigan.user.infra.database.repository

import com.plcoding.cardigan.user.infra.database.entity.SessionEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface SpringDataSessionRepository : JpaRepository<SessionEntity, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SessionEntity s set s.revokedAt = :at where s.id = :id and s.revokedAt is null")
    fun revoke(@Param("id") id: UUID, @Param("at") at: Instant): Int
}
