package com.plcoding.cardigan.board.infra.database.repository

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Test
import java.util.UUID

class JpaColumnRepositoryTest : BoardPersistenceTest() {
    private fun savedBoard(key: String = "CRD") = boards.save(board(key = key))

    private fun namesAndPositions(boardId: UUID) = columns.findAllByBoardId(boardId).map { it.name to it.position }

    @Test
    fun `saved column reads back equal, timestamps at millisecond precision`() {
        val board = savedBoard()
        val column = columns.save(column(board.id, 0, name = "To do", updatedAt = now.plusMillis(1)))
        flushAndClear()

        assertThat(columns.findByIdAndBoardId(column.id, board.id)).isEqualTo(column)
        assertThat(
            nativeInstant("SELECT created_at FROM board_service.columns WHERE id = ?1", column.id),
        ).isEqualTo(now)
    }

    @Test
    fun `column is not found under another board or with an unknown ID`() {
        val board = savedBoard("AA")
        val other = savedBoard("BB")
        val column = columns.save(column(board.id, 0))
        flushAndClear()

        assertThat(columns.findByIdAndBoardId(column.id, other.id)).isNull()
        assertThat(columns.findByIdAndBoardId(column.id, UUID.randomUUID())).isNull()
        assertThat(columns.findByIdAndBoardId(UUID.randomUUID(), board.id)).isNull()
    }

    @Test
    fun `columns are listed by position, whatever the insert order, for their board only`() {
        val board = savedBoard("AA")
        val other = savedBoard("BB")
        columns.save(column(board.id, 2, name = "C"))
        columns.save(column(board.id, 0, name = "A"))
        columns.save(column(other.id, 0, name = "X"))
        columns.save(column(board.id, 1, name = "B"))
        flushAndClear()

        assertThat(namesAndPositions(board.id)).containsExactly("A" to 0, "B" to 1, "C" to 2)
        assertThat(columns.countByBoardId(board.id)).isEqualTo(3)
        assertThat(columns.countByBoardId(UUID.randomUUID())).isEqualTo(0)
    }

    @Test
    fun `database rejects a negative position`() {
        val board = savedBoard()
        flushAndClear()
        val insert =
            entityManager.entityManager
                .createNativeQuery(
                    """
            INSERT INTO board_service.columns (id, board_id, name, position, created_at, updated_at)
            VALUES (?1, ?2, 'Broken', -1, now(), now())
            """,
                ).setParameter(1, UUID.randomUUID())
                .setParameter(2, board.id)

        assertFailure { insert.executeUpdate() }.isInstanceOf<ConstraintViolationException>()
    }

    @Test
    fun `database rejects a column without an existing board`() {
        val insert =
            entityManager.entityManager
                .createNativeQuery(
                    """
            INSERT INTO board_service.columns (id, board_id, name, position, created_at, updated_at)
            VALUES (?1, ?2, 'Orphan', 0, now(), now())
            """,
                ).setParameter(1, UUID.randomUUID())
                .setParameter(2, UUID.randomUUID())

        assertFailure { insert.executeUpdate() }.isInstanceOf<ConstraintViolationException>()
    }

    @Test
    fun `deleting a board row directly removes its column rows and no others`() {
        val board = savedBoard("AA")
        val other = savedBoard("BB")
        columns.save(column(board.id, 0))
        columns.save(column(board.id, 1))
        columns.save(column(other.id, 0))
        flushAndClear()

        entityManager.entityManager
            .createNativeQuery("DELETE FROM board_service.boards WHERE id = ?1")
            .setParameter(1, board.id)
            .executeUpdate()

        val count = "SELECT count(*) FROM board_service.columns WHERE board_id = ?1"
        assertThat(nativeCount(count, board.id)).isEqualTo(0)
        assertThat(nativeCount(count, other.id)).isEqualTo(1)
    }

    @Test
    fun `deleting a board through the repository removes its columns`() {
        val board = savedBoard()
        columns.save(column(board.id, 0))
        flushAndClear()

        boards.delete(board.id)
        flushAndClear()

        assertThat(columns.findAllByBoardId(board.id)).isEmpty()
    }

    @Test
    fun `deleted column is gone and the others stay`() {
        val board = savedBoard()
        val first = columns.save(column(board.id, 0, name = "A"))
        columns.save(column(board.id, 1, name = "B"))
        flushAndClear()

        columns.delete(first.id)
        flushAndClear()

        assertThat(namesAndPositions(board.id)).containsExactly("B" to 1)
    }

    @Test
    fun `shift changes exactly the positions in the closed range of its board`() {
        val board = savedBoard("AA")
        val other = savedBoard("BB")
        (0..4).forEach { columns.save(column(board.id, it, name = "C$it")) }
        (0..4).forEach { columns.save(column(other.id, it, name = "X$it")) }
        flushAndClear()

        columns.shiftPositions(board.id, first = 1, last = 3, delta = 1)

        assertThat(namesAndPositions(board.id).toSet())
            .isEqualTo(setOf("C0" to 0, "C1" to 2, "C2" to 3, "C3" to 4, "C4" to 4))
        assertThat(namesAndPositions(other.id)).containsExactly("X0" to 0, "X1" to 1, "X2" to 2, "X3" to 3, "X4" to 4)
    }

    @Test
    fun `shift down moves the range one position lower`() {
        val board = savedBoard()
        (0..3).forEach { columns.save(column(board.id, it, name = "C$it")) }
        flushAndClear()

        columns.shiftPositions(board.id, first = 2, last = 3, delta = -1)

        assertThat(namesAndPositions(board.id).toSet())
            .isEqualTo(setOf("C0" to 0, "C1" to 1, "C2" to 1, "C3" to 2))
    }

    @Test
    fun `closing a gap moves only the columns after it`() {
        val board = savedBoard("AA")
        val other = savedBoard("BB")
        listOf(0, 1, 3, 4).forEach { columns.save(column(board.id, it, name = "C$it")) }
        listOf(0, 1, 2, 3).forEach { columns.save(column(other.id, it, name = "X$it")) }
        flushAndClear()

        columns.closeGapAfter(board.id, position = 2)

        assertThat(namesAndPositions(board.id)).containsExactly("C0" to 0, "C1" to 1, "C3" to 2, "C4" to 3)
        assertThat(namesAndPositions(other.id)).containsExactly("X0" to 0, "X1" to 1, "X2" to 2, "X3" to 3)
    }

    @Test
    fun `shifts leave updated_at of the shifted rows alone`() {
        val board = savedBoard()
        (0..2).forEach { columns.save(column(board.id, it, updatedAt = now.plusSeconds(it.toLong()))) }
        flushAndClear()

        columns.shiftPositions(board.id, first = 0, last = 2, delta = 1)
        columns.closeGapAfter(board.id, position = 0)

        assertThat(columns.findAllByBoardId(board.id).map { it.updatedAt })
            .containsExactly(now, now.plusSeconds(1), now.plusSeconds(2))
    }

    @Test
    fun `shift writes pending changes first and later reads see the new positions`() {
        val board = savedBoard()
        columns.save(column(board.id, 0, name = "A"))
        val pending = columns.save(column(board.id, 1, name = "B"))

        columns.shiftPositions(board.id, first = 1, last = 1, delta = 1)

        assertThat(columns.findByIdAndBoardId(pending.id, board.id)?.position).isEqualTo(2)
    }

    @Test
    fun `saving an existing column changes name, position and updatedAt but not createdAt`() {
        val board = savedBoard()
        val column = columns.save(column(board.id, 0, name = "A"))
        flushAndClear()

        columns.save(
            column.copy(name = "B", position = 4, createdAt = now.plusSeconds(9), updatedAt = now.plusSeconds(60)),
        )
        flushAndClear()

        assertThat(columns.findByIdAndBoardId(column.id, board.id))
            .isEqualTo(column.copy(name = "B", position = 4, updatedAt = now.plusSeconds(60)))
    }
}
