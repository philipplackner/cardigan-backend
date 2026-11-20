package com.plcoding.cardigan.board.api.mapper

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.plcoding.cardigan.board.api.dto.BoardDto
import com.plcoding.cardigan.board.api.dto.BoardListDto
import com.plcoding.cardigan.board.api.dto.BoardSummaryDto
import com.plcoding.cardigan.board.api.dto.ColumnDto
import com.plcoding.cardigan.board.api.dto.ColumnListDto
import com.plcoding.cardigan.board.api.dto.CreateBoardRequest
import com.plcoding.cardigan.board.api.dto.CreateColumnRequest
import com.plcoding.cardigan.board.api.dto.MoveColumnRequest
import com.plcoding.cardigan.board.api.dto.UpdateBoardRequest
import com.plcoding.cardigan.board.api.dto.UpdateColumnRequest
import com.plcoding.cardigan.board.domain.model.Board
import com.plcoding.cardigan.board.domain.model.BoardWithColumns
import com.plcoding.cardigan.board.domain.model.Column
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.time.Instant
import java.util.UUID

class BoardDtoMappersTest {

    private val created = Instant.parse("2026-11-15T09:15:30.123Z")
    private val updated = Instant.parse("2026-11-15T09:16:30.123Z")
    private val board = Board(UUID.randomUUID(), UUID.randomUUID(), "Roadmap", "CRD", created, updated)

    private fun column(name: String, position: Int) = Column(UUID.randomUUID(), board.id, name, position, created, updated)

    @Test
    fun `create board request gives a normalized name`() {
        assertThat(CreateBoardRequest("  Roadma\u0308p \u3000", "CRD").toBoardName()).isEqualTo("Roadm\u00e4p")
    }

    @Test
    fun `create board request gives a normalized uppercase key`() {
        assertThat(CreateBoardRequest("Roadmap", " crd ").toBoardKey()).isEqualTo("CRD")
    }

    @Test
    fun `update board request gives a normalized name, or null when not sent`() {
        assertThat(UpdateBoardRequest().apply { name = " Platform " }.toBoardName()).isEqualTo("Platform")
        assertThat(UpdateBoardRequest().toBoardName()).isNull()
    }

    @Test
    fun `column requests give a normalized name, or null when not sent`() {
        assertThat(CreateColumnRequest(" Review\t").toColumnName()).isEqualTo("Review")
        assertThat(UpdateColumnRequest().apply { name = " QA " }.toColumnName()).isEqualTo("QA")
        assertThat(UpdateColumnRequest().toColumnName()).isNull()
    }

    @Test
    fun `move request keeps a position beyond Long`() {
        val huge = BigInteger("99999999999999999999")

        assertThat(MoveColumnRequest(huge).toPosition()).isEqualTo(huge)
    }

    @Test
    fun `full board has its own fields and the columns in position order`() {
        val done = column("Done", 2)
        val todo = column("To do", 0)
        val doing = column("In progress", 1)

        val dto = BoardWithColumns(board, listOf(done, todo, doing)).toBoardDto()

        assertThat(dto).isEqualTo(
            BoardDto(
                id = board.id,
                name = "Roadmap",
                key = "CRD",
                createdAt = created,
                updatedAt = updated,
                columns = listOf(todo, doing, done).map { ColumnDto(it.id, it.name, it.position, created, updated) },
            ),
        )
    }

    @Test
    fun `list item has the board's fields and no columns`() {
        assertThat(board.toBoardSummaryDto()).isEqualTo(BoardSummaryDto(board.id, "Roadmap", "CRD", created, updated))
        assertThat(BoardSummaryDto::class.java.declaredFields.map { it.name }.toSet())
            .isEqualTo(setOf("id", "name", "key", "createdAt", "updatedAt"))
    }

    @Test
    fun `board list keeps the given order`() {
        val other = board.copy(id = UUID.randomUUID(), key = "ZZ")

        assertThat(listOf(other, board).toBoardListDto())
            .isEqualTo(BoardListDto(listOf(other.toBoardSummaryDto(), board.toBoardSummaryDto())))
    }

    @Test
    fun `column has no board ID`() {
        val column = column("Review", 3)

        assertThat(column.toColumnDto()).isEqualTo(ColumnDto(column.id, "Review", 3, created, updated))
        assertThat(ColumnDto::class.java.declaredFields.map { it.name }.toSet())
            .isEqualTo(setOf("id", "name", "position", "createdAt", "updatedAt"))
    }

    @Test
    fun `column list is in position order`() {
        val b = column("B", 1)
        val a = column("A", 0)

        assertThat(listOf(b, a).toColumnListDto()).isEqualTo(ColumnListDto(listOf(a.toColumnDto(), b.toColumnDto())))
        assertThat(listOf(b, a).toColumnListDto().columns.map { it.name }).containsExactly("A", "B")
    }
}
