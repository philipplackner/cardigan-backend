package com.plcoding.cardigan.board.infra.database.repository

import com.plcoding.cardigan.board.infra.database.entity.LabelEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface SpringDataLabelRepository : JpaRepository<LabelEntity, UUID> {
    fun findByIdAndOwnerId(
        id: UUID,
        ownerId: UUID,
    ): LabelEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockByIdAndOwnerId(
        id: UUID,
        ownerId: UUID,
    ): LabelEntity?

    fun findAllByOwnerId(ownerId: UUID): List<LabelEntity>

    fun existsByOwnerIdAndNameKey(
        ownerId: UUID,
        nameKey: String,
    ): Boolean

    // A derived delete would load the label first. The row count is the only "found" signal.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from LabelEntity l where l.id = :id and l.ownerId = :ownerId")
    fun deleteByIdAndOwnerId(
        @Param("id") id: UUID,
        @Param("ownerId") ownerId: UUID,
    ): Int
}
