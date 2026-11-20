package com.plcoding.cardigan.board.service

import com.plcoding.cardigan.board.domain.exception.LabelNameAlreadyUsedException
import com.plcoding.cardigan.board.domain.exception.LabelNotFoundException
import com.plcoding.cardigan.board.domain.model.Label
import com.plcoding.cardigan.board.domain.model.LabelChanges
import com.plcoding.cardigan.board.domain.model.LabelColor
import com.plcoding.cardigan.board.domain.repository.LabelRepository
import com.plcoding.cardigan.board.domain.text.codePointOrder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
class LabelService(
    private val labelRepository: LabelRepository,
    private val clock: Clock,
) {

    /** Creates a label from an already normalized [name]. */
    @Transactional
    fun create(ownerId: UUID, name: String, color: LabelColor): Label {
        val now = clock.instant()
        val label = Label(
            id = UUID.randomUUID(),
            ownerId = ownerId,
            name = name,
            color = color,
            createdAt = now,
            updatedAt = now,
        )
        if (labelRepository.existsByOwnerAndNameKey(ownerId, label.nameKey)) {
            throw LabelNameAlreadyUsedException()
        }
        return labelRepository.save(label)
    }

    /** All labels of the owner, ordered by the code points of the case-folded name. */
    @Transactional(readOnly = true)
    fun list(ownerId: UUID): List<Label> =
        labelRepository.findAllByOwnerId(ownerId).sortedWith(compareBy(codePointOrder) { it.nameKey })

    @Transactional(readOnly = true)
    fun get(ownerId: UUID, labelId: UUID): Label =
        labelRepository.findByIdAndOwnerId(labelId, ownerId) ?: throw LabelNotFoundException(labelId)

    /**
     * Applies the sent fields. `updatedAt` moves only when the name or the color really differs; a
     * name that differs only in letter case is a change.
     */
    @Transactional
    fun update(ownerId: UUID, labelId: UUID, changes: LabelChanges): Label {
        val stored = labelRepository.findByIdAndOwnerIdForUpdate(labelId, ownerId)
            ?: throw LabelNotFoundException(labelId)
        val changed = stored.copy(
            name = changes.name ?: stored.name,
            color = changes.color ?: stored.color,
        )
        if (changed == stored) return stored

        if (changed.nameKey != stored.nameKey && labelRepository.existsByOwnerAndNameKey(ownerId, changed.nameKey)) {
            throw LabelNameAlreadyUsedException()
        }
        return labelRepository.save(changed.copy(updatedAt = clock.instant()))
    }

    @Transactional
    fun delete(ownerId: UUID, labelId: UUID) {
        if (labelRepository.deleteByIdAndOwnerId(labelId, ownerId) == 0) {
            throw LabelNotFoundException(labelId)
        }
    }
}
