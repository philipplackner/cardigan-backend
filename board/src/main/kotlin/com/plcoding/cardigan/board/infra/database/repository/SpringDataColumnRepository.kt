package com.plcoding.cardigan.board.infra.database.repository

import com.plcoding.cardigan.board.infra.database.entity.ColumnEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface SpringDataColumnRepository : JpaRepository<ColumnEntity, UUID> {

    fun findByIdAndBoardId(id: UUID, boardId: UUID): ColumnEntity?

    fun findAllByBoardIdOrderByPositionAsc(boardId: UUID): List<ColumnEntity>

    fun countByBoardId(boardId: UUID): Long

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE ColumnEntity c
        SET c.position = c.position + :delta
        WHERE c.board.id = :boardId
        AND c.position BETWEEN :first AND :last
        """,
    )
    fun shiftPositions(boardId: UUID, first: Int, last: Int, delta: Int): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE ColumnEntity c
        SET c.position = c.position - 1
        WHERE c.board.id = :boardId
        AND c.position > :position
        """,
    )
    fun closeGapAfter(boardId: UUID, position: Int): Int
}
