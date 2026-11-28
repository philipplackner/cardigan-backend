package com.plcoding.cardigan.board.service

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import com.plcoding.cardigan.board.domain.exception.BoardNotFoundException
import com.plcoding.cardigan.board.domain.exception.ColumnNotFoundException
import com.plcoding.cardigan.board.domain.model.BoardWithColumns
import com.plcoding.cardigan.board.domain.model.Column
import com.plcoding.cardigan.board.fake.FakeBoardRepository
import com.plcoding.cardigan.board.fake.FakeColumnRepository
import com.plcoding.cardigan.common.MutableClock
import com.plcoding.cardigan.common.domain.exception.FieldOutOfRangeException
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ColumnServiceTest {
    private val start = Instant.parse("2026-11-15T09:15:30.123Z")
    private val clock = MutableClock(start)
    private val columns = FakeColumnRepository()
    private val boards = FakeBoardRepository(columns)
    private val boardService = BoardService(boards, columns, clock)
    private val service = ColumnService(boards, columns, clock)

    private val ann = UUID.randomUUID()
    private val bob = UUID.randomUUID()

    /** A board of [owner] with the columns A, B, C at 0, 1, 2, each created one second apart. */
    private fun boardAbc(
        owner: UUID = ann,
        key: String = "CRD",
    ): BoardWithColumns {
        val board = emptyBoard(owner, key)
        listOf("A", "B", "C").forEach {
            clock.advanceBy(Duration.ofSeconds(1))
            service.create(owner, board, it)
        }
        return boardService.get(owner, board)
    }

    private fun emptyBoard(
        owner: UUID = ann,
        key: String = "CRD",
    ): UUID {
        val created = boardService.create(owner, "Roadmap", key)
        created.columns.forEach { service.delete(owner, created.board.id, it.id) }
        return created.board.id
    }

    private fun oneMinuteLater(): Instant {
        clock.advanceBy(Duration.ofMinutes(1))
        return clock.instant()
    }

    private fun state(
        owner: UUID,
        boardId: UUID,
    ) = boardService.get(owner, boardId)

    private fun BoardWithColumns.names() = columns.map { it.name to it.position }

    private fun BoardWithColumns.column(name: String): Column = columns.single { it.name == name }

    private fun move(
        board: BoardWithColumns,
        name: String,
        position: Long,
    ) = service.move(ann, board.board.id, board.column(name).id, BigInteger.valueOf(position))

    @Test
    fun `first column of an empty board gets position 0`() {
        val board = emptyBoard()

        assertThat(service.create(ann, board, "First").position).isEqualTo(0)
    }

    @Test
    fun `new column goes after the last one with equal timestamps`() {
        val board = boardAbc()
        val later = oneMinuteLater()

        val created = service.create(ann, board.board.id, "Review")

        assertThat(created).isEqualTo(Column(created.id, board.board.id, "Review", 3, later, later))
        assertThat(state(ann, board.board.id).names()).containsExactly("A" to 0, "B" to 1, "C" to 2, "Review" to 3)
    }

    @Test
    fun `create sets the board's updatedAt to the column's createdAt and leaves the other columns alone`() {
        val board = boardAbc()
        oneMinuteLater()

        val created = service.create(ann, board.board.id, "Review")

        val after = state(ann, board.board.id)
        assertThat(after.board).isEqualTo(board.board.copy(updatedAt = created.createdAt))
        assertThat(after.columns.dropLast(1)).isEqualTo(board.columns)
    }

    @Test
    fun `get returns the column of the board`() {
        val board = boardAbc()

        assertThat(service.get(ann, board.board.id, board.column("B").id)).isEqualTo(board.column("B"))
    }

    @Test
    fun `rename changes the column's name and both updated times, nothing else`() {
        val board = boardAbc()
        val later = oneMinuteLater()

        val renamed = service.rename(ann, board.board.id, board.column("B").id, "QA")

        assertThat(renamed).isEqualTo(board.column("B").copy(name = "QA", updatedAt = later))
        val after = state(ann, board.board.id)
        assertThat(after.board).isEqualTo(board.board.copy(updatedAt = later))
        assertThat(after.columns).containsExactly(board.column("A"), renamed, board.column("C"))
    }

    @Test
    fun `rename to the stored name writes nothing`() {
        val board = boardAbc()
        val writes = boards.saveCount to columns.saveCount
        oneMinuteLater()

        val renamed = service.rename(ann, board.board.id, board.column("B").id, "B")

        assertThat(renamed).isEqualTo(board.column("B"))
        assertThat(state(ann, board.board.id)).isEqualTo(board)
        assertThat(boards.saveCount to columns.saveCount).isEqualTo(writes)
    }

    @Test
    fun `name not sent writes nothing`() {
        val board = boardAbc()
        val writes = boards.saveCount to columns.saveCount
        oneMinuteLater()

        assertThat(service.rename(ann, board.board.id, board.column("B").id, null)).isEqualTo(board.column("B"))
        assertThat(state(ann, board.board.id)).isEqualTo(board)
        assertThat(boards.saveCount to columns.saveCount).isEqualTo(writes)
    }

    @Test
    fun `case-only rename is a change`() {
        val board = boardAbc()
        val later = oneMinuteLater()

        assertThat(service.rename(ann, board.board.id, board.column("B").id, "b").updatedAt).isEqualTo(later)
    }

    @Test
    fun `move left`() {
        val board = boardAbc()

        assertThat(move(board, "C", 1).map { it.name to it.position }).containsExactly("A" to 0, "C" to 1, "B" to 2)
    }

    @Test
    fun `move right`() {
        val board = boardAbc()

        assertThat(move(board, "A", 1).map { it.name to it.position }).containsExactly("B" to 0, "A" to 1, "C" to 2)
    }

    @Test
    fun `move to the first position`() {
        val board = boardAbc()

        assertThat(move(board, "C", 0).map { it.name to it.position }).containsExactly("C" to 0, "A" to 1, "B" to 2)
    }

    @Test
    fun `move to the last position`() {
        val board = boardAbc()

        assertThat(move(board, "A", 2).map { it.name to it.position }).containsExactly("B" to 0, "C" to 1, "A" to 2)
    }

    @Test
    fun `move in a longer board shifts only the columns in between`() {
        val board = boardAbc()
        listOf("D", "E").forEach { service.create(ann, board.board.id, it) }
        val full = state(ann, board.board.id)

        assertThat(move(full, "B", 3).map { it.name }).containsExactly("A", "C", "D", "B", "E")
        assertThat(move(state(ann, board.board.id), "B", 1).map { it.name }).containsExactly("A", "B", "C", "D", "E")
    }

    @Test
    fun `move sets only the moved column's and the board's updatedAt`() {
        val board = boardAbc()
        val later = oneMinuteLater()

        val moved = move(board, "C", 0)

        assertThat(moved).containsExactly(
            board.column("C").copy(position = 0, updatedAt = later),
            board.column("A").copy(position = 1),
            board.column("B").copy(position = 2),
        )
        assertThat(state(ann, board.board.id).board).isEqualTo(board.board.copy(updatedAt = later))
    }

    @Test
    fun `move to the current position writes nothing and returns all columns`() {
        val board = boardAbc()
        val writes = boards.saveCount to columns.saveCount
        oneMinuteLater()

        assertThat(move(board, "B", 1)).isEqualTo(board.columns)
        assertThat(state(ann, board.board.id)).isEqualTo(board)
        assertThat(boards.saveCount to columns.saveCount).isEqualTo(writes)
    }

    @Test
    fun `position equal to the column count is out of range with 0 and n-1 and changes nothing`() {
        val board = boardAbc()
        oneMinuteLater()

        assertFailure { move(board, "A", 3) }.isInstanceOf<FieldOutOfRangeException>().limits("position", 0, 2)
        assertThat(state(ann, board.board.id)).isEqualTo(board)
    }

    @Test
    fun `negative position is out of range`() {
        val board = boardAbc()

        assertFailure { move(board, "A", -1) }.isInstanceOf<FieldOutOfRangeException>().limits("position", 0, 2)
    }

    @Test
    fun `position beyond Long is out of range`() {
        val board = boardAbc()

        assertFailure { service.move(ann, board.board.id, board.column("A").id, BigInteger("99999999999999999999")) }
            .isInstanceOf<FieldOutOfRangeException>()
            .limits("position", 0, 2)
    }

    @Test
    fun `only column can move to 0 only, with max 0`() {
        val boardId = emptyBoard()
        val only = service.create(ann, boardId, "Only")

        assertThat(service.move(ann, boardId, only.id, BigInteger.ZERO)).containsExactly(only)
        assertFailure { service.move(ann, boardId, only.id, BigInteger.ONE) }
            .isInstanceOf<FieldOutOfRangeException>()
            .limits("position", 0, 0)
    }

    @Test
    fun `range follows the columns left after a delete`() {
        val board = boardAbc()
        listOf("D", "E").forEach { service.create(ann, board.board.id, it) }
        service.delete(ann, board.board.id, board.column("B").id)
        service.delete(ann, board.board.id, board.column("C").id)

        assertFailure { move(board, "A", 4) }.isInstanceOf<FieldOutOfRangeException>().limits("position", 0, 2)
    }

    @Test
    fun `delete of the first column moves the rest down and keeps their updatedAt`() {
        val board = boardAbc()
        oneMinuteLater()

        service.delete(ann, board.board.id, board.column("A").id)

        assertThat(state(ann, board.board.id).columns)
            .containsExactly(board.column("B").copy(position = 0), board.column("C").copy(position = 1))
    }

    @Test
    fun `delete of the middle column closes the gap`() {
        val board = boardAbc()
        oneMinuteLater()

        service.delete(ann, board.board.id, board.column("B").id)

        assertThat(state(ann, board.board.id).columns)
            .containsExactly(board.column("A"), board.column("C").copy(position = 1))
    }

    @Test
    fun `delete of the last column leaves the others as they are`() {
        val board = boardAbc()
        oneMinuteLater()

        service.delete(ann, board.board.id, board.column("C").id)

        assertThat(state(ann, board.board.id).columns).containsExactly(board.column("A"), board.column("B"))
    }

    @Test
    fun `delete of the only column leaves an empty board`() {
        val boardId = emptyBoard()
        val only = service.create(ann, boardId, "Only")

        service.delete(ann, boardId, only.id)

        assertThat(state(ann, boardId).columns).isEmpty()
        assertThat(service.create(ann, boardId, "Next").position).isEqualTo(0)
    }

    @Test
    fun `delete sets the board's updatedAt`() {
        val board = boardAbc()
        val later = oneMinuteLater()

        service.delete(ann, board.board.id, board.column("B").id)

        assertThat(state(ann, board.board.id).board).isEqualTo(board.board.copy(updatedAt = later))
    }

    @Test
    fun `delete in one board leaves another board's positions alone`() {
        val board = boardAbc(key = "AA")
        val other = boardAbc(key = "BB")

        service.delete(ann, board.board.id, board.column("A").id)

        assertThat(state(ann, other.board.id)).isEqualTo(other)
    }

    @Test
    fun `second delete of a column is ColumnNotFoundException`() {
        val board = boardAbc()
        service.delete(ann, board.board.id, board.column("B").id)

        assertFailure {
            service.delete(
                ann,
                board.board.id,
                board.column("B").id,
            )
        }.isInstanceOf<ColumnNotFoundException>()
        assertThat(state(ann, board.board.id).names()).containsExactly("A" to 0, "C" to 1)
    }

    @Test
    fun `column under another board of the same owner is ColumnNotFoundException for every operation`() {
        val x = boardAbc(key = "XX")
        val y = boardAbc(key = "YY")
        oneMinuteLater()
        val columnOfX = x.column("B").id

        assertFailure { service.get(ann, y.board.id, columnOfX) }.isInstanceOf<ColumnNotFoundException>()
        assertFailure { service.rename(ann, y.board.id, columnOfX, "X") }.isInstanceOf<ColumnNotFoundException>()
        assertFailure {
            service.move(
                ann,
                y.board.id,
                columnOfX,
                BigInteger.ZERO,
            )
        }.isInstanceOf<ColumnNotFoundException>()
        assertFailure { service.delete(ann, y.board.id, columnOfX) }.isInstanceOf<ColumnNotFoundException>()
        assertThat(state(ann, x.board.id)).isEqualTo(x)
        assertThat(state(ann, y.board.id)).isEqualTo(y)
    }

    @Test
    fun `another owner's column under the caller's own board is ColumnNotFoundException for every operation`() {
        val annBoard = boardAbc(ann)
        val bobBoard = boardAbc(bob)
        oneMinuteLater()
        val annColumn = annBoard.column("B").id

        assertFailure { service.get(bob, bobBoard.board.id, annColumn) }.isInstanceOf<ColumnNotFoundException>()
        assertFailure { service.rename(bob, bobBoard.board.id, annColumn, "X") }.isInstanceOf<ColumnNotFoundException>()
        assertFailure {
            service.move(bob, bobBoard.board.id, annColumn, BigInteger.ZERO)
        }.isInstanceOf<ColumnNotFoundException>()
        assertFailure { service.delete(bob, bobBoard.board.id, annColumn) }.isInstanceOf<ColumnNotFoundException>()
        assertThat(state(ann, annBoard.board.id)).isEqualTo(annBoard)
        assertThat(state(bob, bobBoard.board.id)).isEqualTo(bobBoard)
    }

    @Test
    fun `column in another owner's board is BoardNotFoundException for every operation`() {
        val annBoard = boardAbc(ann)
        oneMinuteLater()
        val annColumn = annBoard.column("B").id

        assertFailure { service.create(bob, annBoard.board.id, "X") }.isInstanceOf<BoardNotFoundException>()
        assertFailure { service.get(bob, annBoard.board.id, annColumn) }.isInstanceOf<BoardNotFoundException>()
        assertFailure { service.rename(bob, annBoard.board.id, annColumn, "X") }.isInstanceOf<BoardNotFoundException>()
        assertFailure {
            service.move(bob, annBoard.board.id, annColumn, BigInteger.ZERO)
        }.isInstanceOf<BoardNotFoundException>()
        assertFailure { service.delete(bob, annBoard.board.id, annColumn) }.isInstanceOf<BoardNotFoundException>()
        assertThat(state(ann, annBoard.board.id)).isEqualTo(annBoard)
    }

    @Test
    fun `unknown column ID is ColumnNotFoundException and unknown board ID is BoardNotFoundException`() {
        val board = boardAbc()
        val unknown = UUID.randomUUID()

        assertFailure { service.rename(ann, board.board.id, unknown, "X") }.isInstanceOf<ColumnNotFoundException>()
        assertFailure {
            service.move(
                ann,
                board.board.id,
                unknown,
                BigInteger.ZERO,
            )
        }.isInstanceOf<ColumnNotFoundException>()
        assertFailure { service.delete(ann, board.board.id, unknown) }.isInstanceOf<ColumnNotFoundException>()
        assertFailure { service.create(ann, unknown, "X") }.isInstanceOf<BoardNotFoundException>()
        assertFailure { service.rename(ann, unknown, board.column("A").id, "X") }.isInstanceOf<BoardNotFoundException>()
        assertFailure {
            service.move(ann, unknown, board.column("A").id, BigInteger.ZERO)
        }.isInstanceOf<BoardNotFoundException>()
        assertFailure { service.delete(ann, unknown, board.column("A").id) }.isInstanceOf<BoardNotFoundException>()
        assertThat(state(ann, board.board.id)).isEqualTo(board)
    }

    @Test
    fun `missing column wins over an out-of-range position`() {
        val board = boardAbc()

        assertFailure { service.move(ann, board.board.id, UUID.randomUUID(), BigInteger.valueOf(-1)) }
            .isInstanceOf<ColumnNotFoundException>()
    }

    @Test
    fun `column of a deleted board is BoardNotFoundException`() {
        val board = boardAbc()
        boardService.delete(ann, board.board.id)

        assertFailure {
            service.rename(
                ann,
                board.board.id,
                board.column("A").id,
                "X",
            )
        }.isInstanceOf<BoardNotFoundException>()
    }

    private fun assertk.Assert<FieldOutOfRangeException>.limits(
        field: String,
        min: Long,
        max: Long,
    ) {
        prop(FieldOutOfRangeException::field).isEqualTo(field)
        prop(FieldOutOfRangeException::min).isEqualTo(min)
        prop(FieldOutOfRangeException::max).isEqualTo(max)
    }
}
