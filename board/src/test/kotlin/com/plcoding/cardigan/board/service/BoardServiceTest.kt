package com.plcoding.cardigan.board.service

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import com.plcoding.cardigan.board.domain.exception.BoardKeyAlreadyUsedException
import com.plcoding.cardigan.board.domain.exception.BoardNotFoundException
import com.plcoding.cardigan.board.domain.model.Board
import com.plcoding.cardigan.board.fake.FakeBoardRepository
import com.plcoding.cardigan.board.fake.FakeColumnRepository
import com.plcoding.cardigan.common.MutableClock
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class BoardServiceTest {

    private val start = Instant.parse("2026-11-15T09:15:30.123Z")
    private val clock = MutableClock(start)
    private val columns = FakeColumnRepository()
    private val boards = FakeBoardRepository(columns)
    private val service = BoardService(boards, columns, clock)

    private val ann = UUID.randomUUID()
    private val bob = UUID.randomUUID()

    private fun oneMinuteLater(): Instant {
        clock.advanceBy(Duration.ofMinutes(1))
        return clock.instant()
    }

    @Test
    fun `create stores the board with equal timestamps`() {
        val created = service.create(ann, "Roadmap", "CRD").board

        assertThat(created).isEqualTo(Board(created.id, ann, "Roadmap", "CRD", createdAt = start, updatedAt = start))
        assertThat(boards.all).containsExactly(created)
    }

    @Test
    fun `create adds the three default columns at 0, 1, 2 with the board's timestamps`() {
        val created = service.create(ann, "Roadmap", "CRD")

        assertThat(created.columns.map { it.name to it.position })
            .containsExactly("To do" to 0, "In progress" to 1, "Done" to 2)
        created.columns.forEach { column ->
            assertThat(column.boardId).isEqualTo(created.board.id)
            assertThat(column.createdAt).isEqualTo(created.board.createdAt)
            assertThat(column.updatedAt).isEqualTo(created.board.createdAt)
        }
        assertThat(created.columns.map { it.id }.toSet().size).isEqualTo(3)
        assertThat(columns.all).isEqualTo(created.columns)
    }

    @Test
    fun `key used by the same owner is rejected and stores nothing`() {
        service.create(ann, "Roadmap", "CRD")

        assertFailure { service.create(ann, "Other", "CRD") }.isInstanceOf<BoardKeyAlreadyUsedException>()
        assertThat(boards.all.size).isEqualTo(1)
        assertThat(columns.all.size).isEqualTo(3)
    }

    @Test
    fun `same key for another owner is accepted`() {
        service.create(ann, "Roadmap", "CRD")

        assertThat(service.create(bob, "Roadmap", "CRD").board.key).isEqualTo("CRD")
    }

    @Test
    fun `key is free again after its board was deleted`() {
        val first = service.create(ann, "Roadmap", "CRD").board
        service.delete(ann, first.id)

        assertThat(service.create(ann, "Again", "CRD").board.id).isNotEqualTo(first.id)
    }

    @Test
    fun `rename changes the name and updatedAt, never key or createdAt`() {
        val created = service.create(ann, "Roadmap", "CRD")
        val later = oneMinuteLater()

        val renamed = service.rename(ann, created.board.id, "Platform")

        assertThat(renamed.board).isEqualTo(created.board.copy(name = "Platform", updatedAt = later))
        assertThat(renamed.columns).isEqualTo(created.columns)
        assertThat(service.get(ann, created.board.id)).isEqualTo(renamed)
    }

    @Test
    fun `rename to the stored name writes nothing and keeps updatedAt`() {
        val created = service.create(ann, "Roadmap", "CRD")
        val writes = boards.saveCount
        oneMinuteLater()

        val renamed = service.rename(ann, created.board.id, "Roadmap")

        assertThat(renamed).isEqualTo(created)
        assertThat(boards.saveCount).isEqualTo(writes)
    }

    @Test
    fun `case-only rename is a change`() {
        val created = service.create(ann, "roadmap", "CRD")
        val later = oneMinuteLater()

        val renamed = service.rename(ann, created.board.id, "Roadmap")

        assertThat(renamed.board.name).isEqualTo("Roadmap")
        assertThat(renamed.board.updatedAt).isEqualTo(later)
    }

    @Test
    fun `name not sent writes nothing and returns the board with its columns`() {
        val created = service.create(ann, "Roadmap", "CRD")
        val writes = boards.saveCount
        oneMinuteLater()

        assertThat(service.rename(ann, created.board.id, null)).isEqualTo(created)
        assertThat(boards.saveCount).isEqualTo(writes)
    }

    @Test
    fun `rename never touches the columns`() {
        val created = service.create(ann, "Roadmap", "CRD")
        val writes = columns.saveCount
        oneMinuteLater()

        service.rename(ann, created.board.id, "Platform")

        assertThat(columns.saveCount).isEqualTo(writes)
        assertThat(columns.all).isEqualTo(created.columns)
    }

    @Test
    fun `get returns the board with its columns in position order`() {
        val created = service.create(ann, "Roadmap", "CRD")
        columns.save(created.columns[2].copy(position = 0))
        columns.save(created.columns[0].copy(position = 2))

        assertThat(service.get(ann, created.board.id).columns.map { it.name })
            .containsExactly("Done", "In progress", "To do")
    }

    @Test
    fun `get, rename and delete of another owner's board are BoardNotFoundException and change nothing`() {
        val created = service.create(ann, "Roadmap", "CRD")
        oneMinuteLater()

        assertFailure { service.get(bob, created.board.id) }.isInstanceOf<BoardNotFoundException>()
        assertFailure { service.rename(bob, created.board.id, "Mine") }.isInstanceOf<BoardNotFoundException>()
        assertFailure { service.delete(bob, created.board.id) }.isInstanceOf<BoardNotFoundException>()
        assertThat(service.get(ann, created.board.id)).isEqualTo(created)
    }

    @Test
    fun `get, rename and delete of an unknown ID are BoardNotFoundException`() {
        service.create(ann, "Roadmap", "CRD")
        val unknown = UUID.randomUUID()

        assertFailure { service.get(ann, unknown) }.isInstanceOf<BoardNotFoundException>()
        assertFailure { service.rename(ann, unknown, "Mine") }.isInstanceOf<BoardNotFoundException>()
        assertFailure { service.delete(ann, unknown) }.isInstanceOf<BoardNotFoundException>()
    }

    @Test
    fun `delete removes the board and its columns only`() {
        val gone = service.create(ann, "Roadmap", "CRD")
        val kept = service.create(ann, "Platform", "PLT")

        service.delete(ann, gone.board.id)

        assertThat(boards.all).containsExactly(kept.board)
        assertThat(columns.all).isEqualTo(kept.columns)
        assertFailure { service.delete(ann, gone.board.id) }.isInstanceOf<BoardNotFoundException>()
    }

    @Test
    fun `list is newest change first and a rename moves a board to the front`() {
        val a = service.create(ann, "A", "AA").board
        oneMinuteLater()
        val b = service.create(ann, "B", "BB").board
        assertThat(service.list(ann).map { it.id }).containsExactly(b.id, a.id)
        oneMinuteLater()

        service.rename(ann, a.id, "A2")

        assertThat(service.list(ann).map { it.id }).containsExactly(a.id, b.id)
    }

    @Test
    fun `boards created at the same instant are listed by ID`() {
        val created = (1..5).map { service.create(ann, "Board", "K" + ('A' + it)).board }

        assertThat(service.list(ann).map { it.id }).isEqualTo(created.map { it.id }.sortedBy { it.toString() })
    }

    @Test
    fun `list holds only the owner's boards and is empty without any`() {
        val own = service.create(ann, "Roadmap", "CRD").board

        assertThat(service.list(ann)).containsExactly(own)
        assertThat(service.list(bob)).isEmpty()
    }
}
