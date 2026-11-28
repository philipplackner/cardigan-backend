package com.plcoding.cardigan.board.service

import com.plcoding.cardigan.board.domain.exception.BoardKeyAlreadyUsedException
import com.plcoding.cardigan.board.domain.exception.BoardNotFoundException
import com.plcoding.cardigan.board.domain.model.Board
import com.plcoding.cardigan.board.domain.model.BoardWithColumns
import com.plcoding.cardigan.board.domain.model.Column
import com.plcoding.cardigan.board.domain.repository.BoardRepository
import com.plcoding.cardigan.board.domain.repository.ColumnRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * Boards of one user. Names and keys arrive normalized. Every write to an existing board starts by
 * locking its row, so writes to one board and its columns run one after the other.
 */
@Service
class BoardService(
    private val boardRepository: BoardRepository,
    private val columnRepository: ColumnRepository,
    private val clock: Clock,
) {
    /** Creates the board together with its default columns, or nothing at all. */
    @Transactional
    fun create(
        ownerId: UUID,
        name: String,
        key: String,
    ): BoardWithColumns {
        val now = clock.instant()
        if (boardRepository.existsByOwnerIdAndKey(ownerId, key)) throw BoardKeyAlreadyUsedException(key)
        val board =
            boardRepository.save(
                Board(
                    id = UUID.randomUUID(),
                    ownerId = ownerId,
                    name = name,
                    key = key,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        val columns =
            DEFAULT_COLUMN_NAMES.mapIndexed { position, columnName ->
                columnRepository.save(
                    Column(
                        id = UUID.randomUUID(),
                        boardId = board.id,
                        name = columnName,
                        position = position,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        return BoardWithColumns(board, columns)
    }

    @Transactional(readOnly = true)
    fun list(ownerId: UUID): List<Board> = boardRepository.findAllByOwnerId(ownerId)

    // One snapshot for both reads, so a board deleted in between never comes back without columns.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun get(
        ownerId: UUID,
        boardId: UUID,
    ): BoardWithColumns {
        val board = boardRepository.findByIdAndOwnerId(boardId, ownerId) ?: throw BoardNotFoundException(boardId)
        return BoardWithColumns(board, columnRepository.findAllByBoardId(boardId))
    }

    /** Renames the board. A null [name] or the stored name changes nothing, `updatedAt` included. */
    @Transactional
    fun rename(
        ownerId: UUID,
        boardId: UUID,
        name: String?,
    ): BoardWithColumns {
        val board = boardRepository.lockByIdAndOwner(boardId, ownerId) ?: throw BoardNotFoundException(boardId)
        val now = clock.instant()
        val renamed =
            if (name == null || name == board.name) {
                board
            } else {
                boardRepository.save(board.copy(name = name, updatedAt = now))
            }
        return BoardWithColumns(renamed, columnRepository.findAllByBoardId(boardId))
    }

    @Transactional
    fun delete(
        ownerId: UUID,
        boardId: UUID,
    ) {
        val board = boardRepository.lockByIdAndOwner(boardId, ownerId) ?: throw BoardNotFoundException(boardId)
        boardRepository.delete(board.id)
    }

    companion object {
        private val DEFAULT_COLUMN_NAMES: List<String> = listOf("To do", "In progress", "Done")
    }
}
