package com.plcoding.cardigan.board.infra.database.repository

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.plcoding.cardigan.board.domain.exception.BoardKeyAlreadyUsedException
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.time.Duration
import java.util.UUID

class JpaBoardRepositoryTest : BoardPersistenceTest() {

    @Test
    fun `saved board reads back equal, timestamps at millisecond precision`() {
        val board = boards.save(board(updatedAt = now.plusMillis(1)))
        flushAndClear()

        assertThat(boards.findByIdAndOwnerId(board.id, board.ownerId)).isEqualTo(board)
        assertThat(nativeInstant("SELECT updated_at FROM board_service.boards WHERE id = ?1", board.id))
            .isEqualTo(now.plusMillis(1))
    }

    @Test
    fun `board is not found for another owner or an unknown ID`() {
        val board = boards.save(board())
        flushAndClear()

        assertThat(boards.findByIdAndOwnerId(board.id, UUID.randomUUID())).isNull()
        assertThat(boards.findByIdAndOwnerId(UUID.randomUUID(), board.ownerId)).isNull()
    }

    @Test
    fun `lock query returns the board to its owner only`() {
        val board = boards.save(board())
        flushAndClear()

        assertThat(boards.lockByIdAndOwner(board.id, board.ownerId)).isEqualTo(board)
        assertThat(boards.lockByIdAndOwner(board.id, UUID.randomUUID())).isNull()
        assertThat(boards.lockByIdAndOwner(UUID.randomUUID(), board.ownerId)).isNull()
    }

    @Test
    fun `lock query sets the configured lock timeout for the transaction`() {
        val board = boards.save(board())

        boards.lockByIdAndOwner(board.id, board.ownerId)

        assertThat(entityManager.entityManager.createNativeQuery("SHOW lock_timeout").singleResult).isEqualTo("5s")
    }

    @Test
    fun `list is ordered by updated time, then created time, newest first`() {
        val owner = UUID.randomUUID()
        val oldest = boards.save(board(owner, "AA", createdAt = now, updatedAt = now))
        val createdLater = boards.save(board(owner, "BB", createdAt = now.plusSeconds(1), updatedAt = now.plusSeconds(60)))
        val createdEarlier = boards.save(board(owner, "CC", createdAt = now, updatedAt = now.plusSeconds(60)))
        val newest = boards.save(board(owner, "DD", createdAt = now, updatedAt = now.plusSeconds(120)))
        flushAndClear()

        assertThat(boards.findAllByOwnerId(owner).map { it.key })
            .containsExactly(newest.key, createdLater.key, createdEarlier.key, oldest.key)
    }

    @Test
    fun `equal timestamps are ordered by the ID as a lowercase string, not as signed numbers`() {
        val owner = UUID.randomUUID()
        val low = UUID.fromString("0f000000-0000-4000-8000-000000000000")
        val high = UUID.fromString("f0000000-0000-4000-8000-000000000000")
        boards.save(board(owner, "AA", id = high))
        boards.save(board(owner, "BB", id = low))
        flushAndClear()

        assertThat(high.compareTo(low)).isLessThan(0)
        assertThat(boards.findAllByOwnerId(owner).map { it.id }).containsExactly(low, high)
    }

    @Test
    fun `equal most significant halves are ordered by the rest of the ID`() {
        val owner = UUID.randomUUID()
        val low = UUID.fromString("11111111-1111-4111-0f00-000000000000")
        val high = UUID.fromString("11111111-1111-4111-f000-000000000000")
        boards.save(board(owner, "AA", id = high))
        boards.save(board(owner, "BB", id = low))
        flushAndClear()

        assertThat(boards.findAllByOwnerId(owner).map { it.id }).containsExactly(low, high)
    }

    @Test
    fun `list holds only the owner's boards`() {
        val owner = UUID.randomUUID()
        val own = boards.save(board(owner, "AA"))
        boards.save(board(UUID.randomUUID(), "AA"))
        flushAndClear()

        assertThat(boards.findAllByOwnerId(owner)).containsExactly(own)
    }

    @Test
    fun `existsByOwnerIdAndKey matches owner and key together`() {
        val board = boards.save(board(key = "CRD"))
        flushAndClear()

        assertThat(boards.existsByOwnerIdAndKey(board.ownerId, "CRD")).isTrue()
        assertThat(boards.existsByOwnerIdAndKey(board.ownerId, "CRX")).isFalse()
        assertThat(boards.existsByOwnerIdAndKey(UUID.randomUUID(), "CRD")).isFalse()
    }

    @Test
    fun `saving an existing board changes name and updatedAt but never key, owner or createdAt`() {
        val board = boards.save(board())
        flushAndClear()

        boards.save(
            board.copy(
                name = "Platform",
                key = "NEW",
                ownerId = UUID.randomUUID(),
                createdAt = now.plusSeconds(5),
                updatedAt = now.plusSeconds(60),
            ),
        )
        flushAndClear()

        assertThat(boards.findByIdAndOwnerId(board.id, board.ownerId))
            .isEqualTo(board.copy(name = "Platform", updatedAt = now.plusSeconds(60)))
    }

    @Test
    fun `deleted board is gone`() {
        val board = boards.save(board())
        flushAndClear()

        boards.delete(board.id)
        flushAndClear()

        assertThat(boards.findByIdAndOwnerId(board.id, board.ownerId)).isNull()
    }

    @Test
    fun `database rejects a second row with the same owner and key`() {
        val board = boards.save(board(key = "CRD"))
        flushAndClear()
        val insert = entityManager.entityManager.createNativeQuery(
            """
            INSERT INTO board_service.boards (id, owner_id, name, key, created_at, updated_at)
            VALUES (?1, ?2, 'Other', 'CRD', now(), now())
            """,
        ).setParameter(1, UUID.randomUUID()).setParameter(2, board.ownerId)

        assertFailure { insert.executeUpdate() }.isInstanceOf<ConstraintViolationException>()
    }

    @Test
    fun `second board with the same key for one owner is BoardKeyAlreadyUsedException`() {
        val owner = UUID.randomUUID()
        boards.save(board(owner, "CRD"))

        assertFailure { boards.save(board(owner, "CRD")) }.isInstanceOf<BoardKeyAlreadyUsedException>()
    }

    @Test
    fun `same key for two owners is stored twice`() {
        val first = boards.save(board(UUID.randomUUID(), "CRD"))
        val second = boards.save(board(UUID.randomUUID(), "CRD"))
        flushAndClear()

        assertThat(boards.findByIdAndOwnerId(first.id, first.ownerId)).isNotNull()
        assertThat(boards.findByIdAndOwnerId(second.id, second.ownerId)).isNotNull()
    }

    @Test
    fun `another integrity violation is rethrown as it is`() {
        assertFailure { boards.save(board(name = "x".repeat(101))) }
            .isInstanceOf<DataIntegrityViolationException>()
    }

    @Test
    fun `timestamps keep their order when they differ by one millisecond`() {
        val owner = UUID.randomUUID()
        val earlier = boards.save(board(owner, "AA", updatedAt = now))
        val later = boards.save(board(owner, "BB", updatedAt = now.plus(Duration.ofMillis(1))))
        flushAndClear()

        assertThat(boards.findAllByOwnerId(owner)).containsExactly(later, earlier)
    }
}
