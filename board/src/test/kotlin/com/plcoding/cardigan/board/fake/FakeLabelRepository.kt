package com.plcoding.cardigan.board.fake

import com.plcoding.cardigan.board.domain.exception.LabelNameAlreadyUsedException
import com.plcoding.cardigan.board.domain.model.Label
import com.plcoding.cardigan.board.domain.repository.LabelRepository
import java.util.UUID

/** Keeps labels in memory and, like the database, refuses a second name key for one owner. */
class FakeLabelRepository : LabelRepository {

    private val labels = linkedMapOf<UUID, Label>()

    var saveCount = 0
        private set

    val all: List<Label> get() = labels.values.toList()

    /** When set, the name check answers false, as it does for the loser of a race. */
    var nameCheckSeesNothing = false

    override fun findByIdAndOwnerId(id: UUID, ownerId: UUID): Label? = labels[id]?.takeIf { it.ownerId == ownerId }

    override fun findByIdAndOwnerIdForUpdate(id: UUID, ownerId: UUID): Label? = findByIdAndOwnerId(id, ownerId)

    override fun findAllByOwnerId(ownerId: UUID): List<Label> = labels.values.filter { it.ownerId == ownerId }

    override fun existsByOwnerAndNameKey(ownerId: UUID, nameKey: String): Boolean =
        !nameCheckSeesNothing && labels.values.any { it.ownerId == ownerId && it.nameKey == nameKey }

    override fun save(label: Label): Label {
        saveCount++
        val clash = labels.values.any { it.id != label.id && it.ownerId == label.ownerId && it.nameKey == label.nameKey }
        if (clash) throw LabelNameAlreadyUsedException()
        labels[label.id] = label
        return label
    }

    override fun deleteByIdAndOwnerId(id: UUID, ownerId: UUID): Int {
        if (findByIdAndOwnerId(id, ownerId) == null) return 0
        labels.remove(id)
        return 1
    }
}
