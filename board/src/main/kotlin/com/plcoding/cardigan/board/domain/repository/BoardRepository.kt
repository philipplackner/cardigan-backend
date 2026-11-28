package com.plcoding.cardigan.board.domain.repository

import com.plcoding.cardigan.board.domain.model.Board
import java.util.UUID

/** Every lookup takes the owner, so another user's board looks exactly like a missing one. */
interface BoardRepository {
    fun findByIdAndOwnerId(
        id: UUID,
        ownerId: UUID,
    ): Board?

    /**
     * Loads the board and locks its row until the transaction ends, so writers to one board run one
     * after the other. Every write to a board or its columns starts with it.
     */
    fun lockByIdAndOwner(
        id: UUID,
        ownerId: UUID,
    ): Board?

    /** The owner's boards: newest `updatedAt` first, then newest `createdAt`, then by lowercase ID. */
    fun findAllByOwnerId(ownerId: UUID): List<Board>

    fun existsByOwnerIdAndKey(
        ownerId: UUID,
        key: String,
    ): Boolean

    /** Throws `BoardKeyAlreadyUsedException` when the owner already has a board with the key. */
    fun save(board: Board): Board

    /** Deletes the board. Its columns go with it. */
    fun delete(id: UUID)
}
