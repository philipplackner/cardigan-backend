package com.plcoding.cardigan.board.infra.database.repository

import com.plcoding.cardigan.board.domain.model.Board
import com.plcoding.cardigan.board.domain.model.Column
import com.plcoding.cardigan.board.infra.config.BoardConfig
import com.plcoding.cardigan.common.PostgresTestConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

/** Shared setup of the board and column persistence tests. */
@DataJpaTest(properties = ["cardigan.board.lock-timeout=5s"])
@ActiveProfiles("test")
@Import(PostgresTestConfig::class, BoardConfig::class, JpaBoardRepository::class, JpaColumnRepository::class)
abstract class BoardPersistenceTest {

    @Autowired
    protected lateinit var boards: JpaBoardRepository

    @Autowired
    protected lateinit var columns: JpaColumnRepository

    @Autowired
    protected lateinit var entityManager: TestEntityManager

    protected val now: Instant = Instant.parse("2026-11-15T09:15:30.123Z")

    protected fun board(
        ownerId: UUID = UUID.randomUUID(),
        key: String = "CRD",
        id: UUID = UUID.randomUUID(),
        name: String = "Roadmap",
        createdAt: Instant = now,
        updatedAt: Instant = createdAt,
    ) = Board(id, ownerId, name, key, createdAt, updatedAt)

    protected fun column(
        boardId: UUID,
        position: Int,
        name: String = "Column $position",
        createdAt: Instant = now,
        updatedAt: Instant = createdAt,
    ) = Column(UUID.randomUUID(), boardId, name, position, createdAt, updatedAt)

    protected fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    protected fun nativeInstant(sql: String, id: UUID): Instant {
        val stored = entityManager.entityManager.createNativeQuery(sql).setParameter(1, id).singleResult
        return when (stored) {
            is Instant -> stored
            is OffsetDateTime -> stored.toInstant()
            is java.sql.Timestamp -> stored.toInstant()
            else -> error("Unexpected type ${stored::class}")
        }
    }

    protected fun nativeCount(sql: String, id: UUID): Long =
        (entityManager.entityManager.createNativeQuery(sql).setParameter(1, id).singleResult as Number).toLong()
}
