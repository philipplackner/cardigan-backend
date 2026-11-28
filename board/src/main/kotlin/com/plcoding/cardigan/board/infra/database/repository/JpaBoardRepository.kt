package com.plcoding.cardigan.board.infra.database.repository

import com.plcoding.cardigan.board.domain.exception.BoardKeyAlreadyUsedException
import com.plcoding.cardigan.board.domain.model.Board
import com.plcoding.cardigan.board.domain.repository.BoardRepository
import com.plcoding.cardigan.board.infra.config.BoardProperties
import com.plcoding.cardigan.board.infra.database.entity.BoardEntity
import com.plcoding.cardigan.board.infra.database.mapper.toBoard
import com.plcoding.cardigan.board.infra.database.mapper.toBoardEntity
import jakarta.persistence.EntityManager
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class JpaBoardRepository(
    private val springDataBoardRepository: SpringDataBoardRepository,
    private val entityManager: EntityManager,
    private val boardProperties: BoardProperties,
) : BoardRepository {
    override fun findByIdAndOwnerId(
        id: UUID,
        ownerId: UUID,
    ): Board? = springDataBoardRepository.findByIdAndOwnerId(id, ownerId)?.toBoard()

    override fun lockByIdAndOwner(
        id: UUID,
        ownerId: UUID,
    ): Board? {
        // Postgres has no FOR UPDATE WAIT n, so the JPA lock timeout hint can't bound the wait.
        // SET LOCAL takes no bind parameter; the value is a number from config.
        entityManager
            .createNativeQuery("SET LOCAL lock_timeout = '${boardProperties.lockTimeout.toMillis()}ms'")
            .executeUpdate()
        return springDataBoardRepository.lockByIdAndOwnerId(id, ownerId)?.toBoard()
    }

    override fun findAllByOwnerId(ownerId: UUID): List<Board> =
        springDataBoardRepository.findAllByOwnerIdOrderByUpdatedAtDescCreatedAtDescIdAsc(ownerId).map { it.toBoard() }

    override fun existsByOwnerIdAndKey(
        ownerId: UUID,
        key: String,
    ): Boolean = springDataBoardRepository.existsByOwnerIdAndKey(ownerId, key)

    override fun save(board: Board): Board =
        try {
            // Flushes now, so a key taken by a concurrent create fails here and not at commit.
            springDataBoardRepository.saveAndFlush(board.toBoardEntity()).toBoard()
        } catch (ex: DataIntegrityViolationException) {
            if (ex.violatedConstraint() == BoardEntity.OWNER_KEY_UNIQUE) throw BoardKeyAlreadyUsedException(board.key)
            throw ex
        }

    override fun delete(id: UUID) {
        springDataBoardRepository.deleteById(id)
    }

    private fun Throwable.violatedConstraint(): String? =
        generateSequence(this) { it.cause?.takeIf { cause -> cause !== it } }
            .filterIsInstance<ConstraintViolationException>()
            .firstOrNull()
            ?.constraintName
}
