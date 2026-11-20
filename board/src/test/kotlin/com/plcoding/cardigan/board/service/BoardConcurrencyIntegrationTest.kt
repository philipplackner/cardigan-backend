package com.plcoding.cardigan.board.service

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.plcoding.cardigan.board.domain.exception.BoardKeyAlreadyUsedException
import com.plcoding.cardigan.board.domain.exception.BoardNotFoundException
import com.plcoding.cardigan.board.domain.model.BoardWithColumns
import com.plcoding.cardigan.board.domain.model.normalizeBoardKey
import com.plcoding.cardigan.common.domain.exception.FieldOutOfRangeException
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigInteger
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["cardigan.board.lock-timeout=30s"],
)
class BoardConcurrencyIntegrationTest : BoardIntegrationTest() {

    private fun BoardWithColumns.names() = columns.map { it.name to it.position }

    private fun BoardWithColumns.column(name: String) = columns.single { it.name == name }

    private fun boardAbc(key: String = "CRD"): BoardWithColumns {
        val created = boardService.create(owner, "Roadmap", key)
        created.columns.zip(listOf("A", "B", "C")).forEach { (column, name) ->
            columnService.rename(owner, created.board.id, column.id, name)
        }
        return boardService.get(owner, created.board.id)
    }

    @RepeatedTest(10)
    fun `two simultaneous creates with one key end with one board and three columns`() {
        val results = race(
            listOf("CRD", "crd").map { key ->
                {
                    tick()
                    boardService.create(owner, "Roadmap", normalizeBoardKey(key))
                }
            },
        )

        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        assertThat(results.single { it.isFailure }.exceptionOrNull()).isNotNull().isInstanceOf<BoardKeyAlreadyUsedException>()
        val board = boardService.get(owner, boardService.list(owner).single().id)
        assertThat(board.names()).containsExactly("To do" to 0, "In progress" to 1, "Done" to 2)
        assertThat(board.board.updatedAt).isEqualTo(board.columns.maxOf { it.updatedAt })
        assertThat(storedBoards()).isEqualTo(1)
        assertThat(storedColumns()).isEqualTo(3)
    }

    @Test
    fun `ten simultaneous column creates fill the positions 0 to 12, each once`() {
        val boardId = boardService.create(owner, "Roadmap", "CRD").board.id

        val results = race(
            (1..10).map { number ->
                {
                    tick()
                    columnService.create(owner, boardId, "Column $number")
                }
            },
        )

        assertThat(results.count { it.isSuccess }).isEqualTo(10)
        val board = boardService.get(owner, boardId)
        assertThat(board.columns.map { it.position }).isEqualTo((0..12).toList())
        assertThat(results.map { it.getOrThrow().position }.sorted()).isEqualTo((3..12).toList())
        assertThat(board.board.updatedAt).isEqualTo(board.columns.maxOf { it.updatedAt })
        assertThat(board.board.updatedAt).isGreaterThan(START)
    }

    @RepeatedTest(15)
    fun `simultaneous move and delete end in one of the two serial results`() {
        val board = boardAbc()
        val boardId = board.board.id

        val (move, delete) = race(
            listOf(
                {
                    tick()
                    columnService.move(owner, boardId, board.column("A").id, BigInteger.TWO)
                    Unit
                },
                {
                    tick()
                    columnService.delete(owner, boardId, board.column("B").id)
                },
            ),
        )

        assertThat(delete.isSuccess).isTrue()
        val after = boardService.get(owner, boardId)
        if (move.isSuccess) {
            assertThat(after.names()).containsExactly("C" to 0, "A" to 1)
        } else {
            val failure = move.exceptionOrNull() as FieldOutOfRangeException
            assertThat(failure.min to failure.max).isEqualTo(0L to 1L)
            assertThat(after.names()).containsExactly("A" to 0, "C" to 1)
            assertThat(after.column("A")).isEqualTo(board.column("A"))
        }
        assertThat(after.column("C").updatedAt).isEqualTo(board.column("C").updatedAt)
        assertThat(after.board.updatedAt).isGreaterThan(board.board.updatedAt)
        assertThat(after.board.updatedAt >= after.columns.maxOf { it.updatedAt }).isTrue()
    }

    @RepeatedTest(15)
    fun `column create racing a board delete leaves no orphan column`() {
        val boardId = boardService.create(owner, "Roadmap", "CRD").board.id

        val (create, delete) = race(
            listOf(
                { columnService.create(owner, boardId, "Review").let { } },
                { boardService.delete(owner, boardId) },
            ),
        )

        assertThat(delete.isSuccess).isTrue()
        if (create.isFailure) {
            assertThat(create.exceptionOrNull()).isNotNull().isInstanceOf<BoardNotFoundException>()
        }
        assertThat(storedBoards()).isEqualTo(0)
        assertThat(storedColumns()).isEqualTo(0)
    }

    @Test
    fun `write waits for a held lock on its board, a write on another board does not`() {
        val x = boardService.create(owner, "X", "XX").board.id
        val y = boardService.create(owner, "Y", "YY").board.id
        val lock = holdBoardLock(x)

        val createInX = executor.submit(Callable { columnService.create(owner, x, "Review") })
        val createInY = executor.submit(Callable { columnService.create(owner, y, "Review") })

        assertThat(createInY.get(10, TimeUnit.SECONDS).position).isEqualTo(3)
        assertFailure { createInX.get(700, TimeUnit.MILLISECONDS) }.isInstanceOf<TimeoutException>()
        assertThat(createInX.isDone).isFalse()
        assertThat(boardService.get(owner, x).columns.size).isEqualTo(3)

        tick()
        lock.release()

        val created = createInX.get(10, TimeUnit.SECONDS)
        assertThat(created.position).isEqualTo(3)
        val after = boardService.get(owner, x)
        assertThat(after.columns.map { it.position }).isEqualTo((0..3).toList())
        assertThat(created.createdAt).isEqualTo(clock.instant())
        assertThat(after.board.updatedAt).isEqualTo(after.columns.maxOf { it.updatedAt })
    }

    @Test
    fun `board create whose third column save fails stores no board and no column`() {
        failingColumns.failOnSave = 3

        assertFailure { boardService.create(owner, "Roadmap", "CRD") }
            .isInstanceOf<IllegalStateException>()
            .hasMessage("column save failed on purpose")

        assertThat(storedBoards()).isEqualTo(0)
        assertThat(storedColumns()).isEqualTo(0)
        failingColumns.reset()
        assertThat(boardService.create(owner, "Roadmap", "CRD").columns.size).isEqualTo(3)
    }

    @Test
    fun `rejected create for a used key stores no second board and no extra column`() {
        boardService.create(owner, "Roadmap", "CRD")

        assertFailure { boardService.create(owner, "Other", "CRD") }.isInstanceOf<BoardKeyAlreadyUsedException>()

        assertThat(storedBoards()).isEqualTo(1)
        assertThat(storedColumns()).isEqualTo(3)
    }

    @Test
    fun `deleting a board removes its columns and frees its key`() {
        val board = boardAbc()
        val kept = boardAbc("KEP")

        boardService.delete(owner, board.board.id)

        assertThat(storedColumns()).isEqualTo(3)
        assertThat(boardService.get(owner, kept.board.id)).isEqualTo(kept)
        assertThat(boardService.create(owner, "Again", "CRD").board.key).isEqualTo("CRD")
    }

    @Test
    fun `move and delete through the real adapters keep positions dense and other updatedAt values`() {
        val board = boardAbc()
        val moved = tick()

        val afterMove = columnService.move(owner, board.board.id, board.column("C").id, BigInteger.ZERO)

        assertThat(afterMove).containsExactly(
            board.column("C").copy(position = 0, updatedAt = moved),
            board.column("A").copy(position = 1),
            board.column("B").copy(position = 2),
        )
        val deleted = tick()
        columnService.delete(owner, board.board.id, board.column("A").id)

        val after = boardService.get(owner, board.board.id)
        assertThat(after.columns).containsExactly(
            board.column("C").copy(position = 0, updatedAt = moved),
            board.column("B").copy(position = 1),
        )
        assertThat(after.board).isEqualTo(board.board.copy(updatedAt = deleted))
    }
}
