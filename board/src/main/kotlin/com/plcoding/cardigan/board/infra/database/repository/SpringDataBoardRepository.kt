package com.plcoding.cardigan.board.infra.database.repository

import com.plcoding.cardigan.board.infra.database.entity.BoardEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface SpringDataBoardRepository : JpaRepository<BoardEntity, UUID> {
    fun findByIdAndOwnerId(
        id: UUID,
        ownerId: UUID,
    ): BoardEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BoardEntity b WHERE b.id = :id AND b.ownerId = :ownerId")
    fun lockByIdAndOwnerId(
        id: UUID,
        ownerId: UUID,
    ): BoardEntity?

    fun findAllByOwnerIdOrderByUpdatedAtDescCreatedAtDescIdAsc(ownerId: UUID): List<BoardEntity>

    fun existsByOwnerIdAndKey(
        ownerId: UUID,
        key: String,
    ): Boolean
}
