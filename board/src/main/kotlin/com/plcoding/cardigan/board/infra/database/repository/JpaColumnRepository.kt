package com.plcoding.cardigan.board.infra.database.repository

import com.plcoding.cardigan.board.domain.model.Column
import com.plcoding.cardigan.board.domain.repository.ColumnRepository
import com.plcoding.cardigan.board.infra.database.entity.BoardEntity
import com.plcoding.cardigan.board.infra.database.mapper.toColumn
import com.plcoding.cardigan.board.infra.database.mapper.toColumnEntity
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class JpaColumnRepository(
    private val springDataColumnRepository: SpringDataColumnRepository,
    private val entityManager: EntityManager,
) : ColumnRepository {

    override fun findByIdAndBoardId(id: UUID, boardId: UUID): Column? =
        springDataColumnRepository.findByIdAndBoardId(id, boardId)?.toColumn()

    override fun findAllByBoardId(boardId: UUID): List<Column> =
        springDataColumnRepository.findAllByBoardIdOrderByPositionAsc(boardId).map { it.toColumn() }

    override fun countByBoardId(boardId: UUID): Int =
        Math.toIntExact(springDataColumnRepository.countByBoardId(boardId))

    override fun save(column: Column): Column {
        val board = entityManager.getReference(BoardEntity::class.java, column.boardId)
        return springDataColumnRepository.save(column.toColumnEntity(board)).toColumn()
    }

    override fun delete(id: UUID) {
        springDataColumnRepository.deleteById(id)
    }

    override fun shiftPositions(boardId: UUID, first: Int, last: Int, delta: Int) {
        springDataColumnRepository.shiftPositions(boardId, first, last, delta)
    }

    override fun closeGapAfter(boardId: UUID, position: Int) {
        springDataColumnRepository.closeGapAfter(boardId, position)
    }
}
