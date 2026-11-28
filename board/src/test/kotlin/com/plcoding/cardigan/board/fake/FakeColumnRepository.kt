package com.plcoding.cardigan.board.fake

import com.plcoding.cardigan.board.domain.model.Column
import com.plcoding.cardigan.board.domain.repository.ColumnRepository
import java.util.UUID

class FakeColumnRepository : ColumnRepository {
    private val columns = linkedMapOf<UUID, Column>()

    var saveCount = 0
        private set

    val all: List<Column> get() = columns.values.toList()

    override fun findByIdAndBoardId(
        id: UUID,
        boardId: UUID,
    ): Column? = columns[id]?.takeIf { it.boardId == boardId }

    override fun findAllByBoardId(boardId: UUID): List<Column> =
        columns.values.filter { it.boardId == boardId }.sortedBy { it.position }

    override fun countByBoardId(boardId: UUID): Int = columns.values.count { it.boardId == boardId }

    override fun save(column: Column): Column {
        saveCount++
        columns[column.id] = column
        return column
    }

    override fun delete(id: UUID) {
        columns.remove(id)
    }

    override fun shiftPositions(
        boardId: UUID,
        first: Int,
        last: Int,
        delta: Int,
    ) {
        columns.replaceAll { _, column ->
            if (column.boardId == boardId && column.position in first..last) {
                column.copy(position = column.position + delta)
            } else {
                column
            }
        }
    }

    override fun closeGapAfter(
        boardId: UUID,
        position: Int,
    ) {
        shiftPositions(boardId, position + 1, Int.MAX_VALUE, -1)
    }

    fun deleteAllOfBoard(boardId: UUID) {
        columns.values.removeIf { it.boardId == boardId }
    }
}
