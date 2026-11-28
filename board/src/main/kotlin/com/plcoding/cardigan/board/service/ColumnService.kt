package com.plcoding.cardigan.board.service

import com.plcoding.cardigan.board.domain.exception.BoardNotFoundException
import com.plcoding.cardigan.board.domain.exception.ColumnNotFoundException
import com.plcoding.cardigan.board.domain.model.Board
import com.plcoding.cardigan.board.domain.model.Column
import com.plcoding.cardigan.board.domain.model.requireInRange
import com.plcoding.cardigan.board.domain.model.shiftForMove
import com.plcoding.cardigan.board.domain.repository.BoardRepository
import com.plcoding.cardigan.board.domain.repository.ColumnRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigInteger
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Columns of one board. Names arrive normalized. Every write first locks the board's row, so the
 * positions it reads are the ones the previous writer left, and they stay 0 to n-1.
 */
@Service
class ColumnService(
    private val boardRepository: BoardRepository,
    private val columnRepository: ColumnRepository,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun get(
        ownerId: UUID,
        boardId: UUID,
        columnId: UUID,
    ): Column {
        boardRepository.findByIdAndOwnerId(boardId, ownerId) ?: throw BoardNotFoundException(boardId)
        return columnIn(boardId, columnId)
    }

    /** Adds a column after the board's last one. */
    @Transactional
    fun create(
        ownerId: UUID,
        boardId: UUID,
        name: String,
    ): Column {
        val board = lockBoard(ownerId, boardId)
        val now = clock.instant()
        val column =
            columnRepository.save(
                Column(
                    id = UUID.randomUUID(),
                    boardId = boardId,
                    name = name,
                    position = columnRepository.countByBoardId(boardId),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        touch(board, now)
        return column
    }

    /** Renames the column. A null [name] or the stored name changes nothing, `updatedAt` included. */
    @Transactional
    fun rename(
        ownerId: UUID,
        boardId: UUID,
        columnId: UUID,
        name: String?,
    ): Column {
        val board = lockBoard(ownerId, boardId)
        val now = clock.instant()
        val column = columnIn(boardId, columnId)
        if (name == null || name == column.name) return column
        val renamed = columnRepository.save(column.copy(name = name, updatedAt = now))
        touch(board, now)
        return renamed
    }

    /**
     * Moves the column to [position], its final index among the board's current columns, and
     * returns all columns of the board in position order. A move to the current position changes
     * nothing.
     */
    @Transactional
    fun move(
        ownerId: UUID,
        boardId: UUID,
        columnId: UUID,
        position: BigInteger,
    ): List<Column> {
        val board = lockBoard(ownerId, boardId)
        val now = clock.instant()
        val column = columnIn(boardId, columnId)
        val target = requireInRange(POSITION_FIELD, position, columnRepository.countByBoardId(boardId))
        if (target != column.position) {
            val shift = shiftForMove(from = column.position, to = target)
            columnRepository.shiftPositions(boardId, shift.first, shift.last, shift.delta)
            columnRepository.save(column.copy(position = target, updatedAt = now))
            touch(board, now)
        }
        return columnRepository.findAllByBoardId(boardId)
    }

    @Transactional
    fun delete(
        ownerId: UUID,
        boardId: UUID,
        columnId: UUID,
    ) {
        val board = lockBoard(ownerId, boardId)
        val now = clock.instant()
        val column = columnIn(boardId, columnId)
        columnRepository.delete(column.id)
        columnRepository.closeGapAfter(boardId, column.position)
        touch(board, now)
    }

    private fun lockBoard(
        ownerId: UUID,
        boardId: UUID,
    ): Board = boardRepository.lockByIdAndOwner(boardId, ownerId) ?: throw BoardNotFoundException(boardId)

    private fun columnIn(
        boardId: UUID,
        columnId: UUID,
    ): Column = columnRepository.findByIdAndBoardId(columnId, boardId) ?: throw ColumnNotFoundException(columnId)

    private fun touch(
        board: Board,
        now: Instant,
    ) {
        boardRepository.save(board.copy(updatedAt = now))
    }

    private companion object {
        const val POSITION_FIELD = "position"
    }
}
