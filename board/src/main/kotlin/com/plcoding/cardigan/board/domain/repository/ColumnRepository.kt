package com.plcoding.cardigan.board.domain.repository

import com.plcoding.cardigan.board.domain.model.Column
import java.util.UUID

/** Every lookup takes the board, so a column under the wrong board looks exactly like a missing one. */
interface ColumnRepository {
    fun findByIdAndBoardId(
        id: UUID,
        boardId: UUID,
    ): Column?

    /** The board's columns in ascending position order. */
    fun findAllByBoardId(boardId: UUID): List<Column>

    fun countByBoardId(boardId: UUID): Int

    fun save(column: Column): Column

    fun delete(id: UUID)

    /**
     * Adds [delta] to the position of every column of the board from [first] to [last], both
     * included. `updatedAt` of the shifted columns stays as it is.
     */
    fun shiftPositions(
        boardId: UUID,
        first: Int,
        last: Int,
        delta: Int,
    )

    /** Moves every column of the board after [position] one position down, keeping `updatedAt`. */
    fun closeGapAfter(
        boardId: UUID,
        position: Int,
    )
}
