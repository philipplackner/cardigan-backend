package com.plcoding.cardigan.board.fake

import com.plcoding.cardigan.board.domain.exception.BoardKeyAlreadyUsedException
import com.plcoding.cardigan.board.domain.model.Board
import com.plcoding.cardigan.board.domain.repository.BoardRepository
import java.util.UUID

/** Deleting a board removes its columns from [columns], like the cascade in the database. */
class FakeBoardRepository(
    private val columns: FakeColumnRepository,
) : BoardRepository {
    private val boards = linkedMapOf<UUID, Board>()

    var saveCount = 0
        private set

    val all: List<Board> get() = boards.values.toList()

    override fun findByIdAndOwnerId(
        id: UUID,
        ownerId: UUID,
    ): Board? = boards[id]?.takeIf { it.ownerId == ownerId }

    override fun lockByIdAndOwner(
        id: UUID,
        ownerId: UUID,
    ): Board? = findByIdAndOwnerId(id, ownerId)

    // Postgres orders uuid values like their lowercase strings; UUID.compareTo compares signed longs.
    override fun findAllByOwnerId(ownerId: UUID): List<Board> =
        boards.values
            .filter { it.ownerId == ownerId }
            .sortedWith(
                compareByDescending<Board> { it.updatedAt }
                    .thenByDescending { it.createdAt }
                    .thenBy { it.id.toString() },
            )

    override fun existsByOwnerIdAndKey(
        ownerId: UUID,
        key: String,
    ): Boolean = boards.values.any { it.ownerId == ownerId && it.key == key }

    override fun save(board: Board): Board {
        if (boards.values.any { it.id != board.id && it.ownerId == board.ownerId && it.key == board.key }) {
            throw BoardKeyAlreadyUsedException(board.key)
        }
        saveCount++
        boards[board.id] = board
        return board
    }

    override fun delete(id: UUID) {
        boards.remove(id)
        columns.deleteAllOfBoard(id)
    }
}
