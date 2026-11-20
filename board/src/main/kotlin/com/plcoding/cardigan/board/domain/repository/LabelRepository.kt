package com.plcoding.cardigan.board.domain.repository

import com.plcoding.cardigan.board.domain.model.Label
import java.util.UUID

/** Every query is scoped to an owner, so another user's label looks like a missing one. */
interface LabelRepository {

    fun findByIdAndOwnerId(id: UUID, ownerId: UUID): Label?

    /** Like [findByIdAndOwnerId], and locks the label until the transaction ends. */
    fun findByIdAndOwnerIdForUpdate(id: UUID, ownerId: UUID): Label?

    fun findAllByOwnerId(ownerId: UUID): List<Label>

    fun existsByOwnerAndNameKey(ownerId: UUID, nameKey: String): Boolean

    /**
     * Stores the label right away.
     *
     * @throws com.plcoding.cardigan.board.domain.exception.LabelNameAlreadyUsedException when another
     * label of the owner has the same name key, also when a concurrent request stored it first.
     */
    fun save(label: Label): Label

    /** Returns the number of deleted labels, 0 or 1. */
    fun deleteByIdAndOwnerId(id: UUID, ownerId: UUID): Int
}
