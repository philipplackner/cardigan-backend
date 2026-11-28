package com.plcoding.cardigan.board.infra.database.repository

import com.plcoding.cardigan.board.domain.exception.LabelNameAlreadyUsedException
import com.plcoding.cardigan.board.domain.model.Label
import com.plcoding.cardigan.board.domain.repository.LabelRepository
import com.plcoding.cardigan.board.infra.database.entity.LabelEntity
import com.plcoding.cardigan.board.infra.database.mapper.toLabel
import com.plcoding.cardigan.board.infra.database.mapper.toLabelEntity
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class JpaLabelRepository(
    private val springDataLabelRepository: SpringDataLabelRepository,
) : LabelRepository {
    override fun findByIdAndOwnerId(
        id: UUID,
        ownerId: UUID,
    ): Label? = springDataLabelRepository.findByIdAndOwnerId(id, ownerId)?.toLabel()

    override fun findByIdAndOwnerIdForUpdate(
        id: UUID,
        ownerId: UUID,
    ): Label? = springDataLabelRepository.findWithLockByIdAndOwnerId(id, ownerId)?.toLabel()

    override fun findAllByOwnerId(ownerId: UUID): List<Label> =
        springDataLabelRepository.findAllByOwnerId(ownerId).map { it.toLabel() }

    override fun existsByOwnerAndNameKey(
        ownerId: UUID,
        nameKey: String,
    ): Boolean = springDataLabelRepository.existsByOwnerIdAndNameKey(ownerId, nameKey)

    // Flushes right away, so a name clash that only the database sees surfaces here and not at commit.
    override fun save(label: Label): Label =
        try {
            springDataLabelRepository.saveAndFlush(label.toLabelEntity()).toLabel()
        } catch (e: DataIntegrityViolationException) {
            if (e.violatesNameUniqueness()) throw LabelNameAlreadyUsedException() else throw e
        }

    override fun deleteByIdAndOwnerId(
        id: UUID,
        ownerId: UUID,
    ): Int = springDataLabelRepository.deleteByIdAndOwnerId(id, ownerId)

    private fun DataIntegrityViolationException.violatesNameUniqueness(): Boolean =
        generateSequence<Throwable>(this) { it.cause?.takeIf { cause -> cause !== it } }
            .filterIsInstance<ConstraintViolationException>()
            .any { it.constraintName == LabelEntity.OWNER_NAME_KEY_UNIQUE }
}
