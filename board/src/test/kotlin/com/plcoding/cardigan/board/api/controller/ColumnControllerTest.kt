package com.plcoding.cardigan.board.api.controller

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import com.plcoding.cardigan.board.api.dto.ColumnDto
import com.plcoding.cardigan.board.api.dto.CreateColumnRequest
import com.plcoding.cardigan.board.api.dto.MoveColumnRequest
import com.plcoding.cardigan.board.api.dto.UpdateColumnRequest
import com.plcoding.cardigan.board.domain.exception.BoardNotFoundException
import com.plcoding.cardigan.board.domain.exception.ColumnNotFoundException
import com.plcoding.cardigan.board.domain.model.BoardWithColumns
import com.plcoding.cardigan.board.fake.FakeBoardRepository
import com.plcoding.cardigan.board.fake.FakeColumnRepository
import com.plcoding.cardigan.board.service.BoardService
import com.plcoding.cardigan.board.service.ColumnService
import com.plcoding.cardigan.common.MutableClock
import com.plcoding.cardigan.common.api.ProblemFieldError
import com.plcoding.cardigan.common.api.validation.RequestValidationException
import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ColumnControllerTest {

    private val start = Instant.parse("2026-11-15T09:15:30.123Z")
    private val clock = MutableClock(start)
    private val columns = FakeColumnRepository()
    private val boards = FakeBoardRepository(columns)
    private val boardService = BoardService(boards, columns, clock)
    private val controller = ColumnController(boardService, ColumnService(boards, columns, clock), plainRequestValidator)

    private val ann = UUID.randomUUID()
    private val bob = UUID.randomUUID()

    private lateinit var board: BoardWithColumns
    private val boardId get() = board.board.id

    @BeforeEach
    fun setUp() {
        signIn(ann)
        board = boardService.create(ann, "Roadmap", "CRD")
        clock.advanceBy(Duration.ofMinutes(1))
    }

    @AfterEach
    fun clear() = signOut()

    private fun columnId(name: String): UUID = board.columns.single { it.name == name }.id

    private fun rename(name: String?) = UpdateColumnRequest().apply { this.name = name }

    private fun names() = boardService.get(ann, boardId).columns.map { it.name to it.position }

    @Test
    fun `create returns the new column at the end of the board`() {
        val column = controller.createColumn(CreateColumnRequest(" Review "), boardId)

        assertThat(column).all {
            prop(ColumnDto::name).isEqualTo("Review")
            prop(ColumnDto::position).isEqualTo(3)
            prop(ColumnDto::createdAt).isEqualTo(clock.instant())
            prop(ColumnDto::updatedAt).isEqualTo(clock.instant())
        }
        assertThat(column.id).isEqualTo(columns.all.single { it.name == "Review" }.id)
    }

    @Test
    fun `create with a broken name is VALIDATION_FAILED and adds nothing`() {
        assertFailure { controller.createColumn(CreateColumnRequest(null), boardId) }
            .isInstanceOf<RequestValidationException>()
            .prop(RequestValidationException::fieldErrors)
            .containsExactly(ProblemFieldError("name", FieldErrorCode.REQUIRED))
        assertThat(columns.all.size).isEqualTo(3)
    }

    @Test
    fun `create on another user's board is BoardNotFoundException before the body is validated`() {
        signIn(bob)

        assertFailure { controller.createColumn(CreateColumnRequest(null), boardId) }
            .isInstanceOf<BoardNotFoundException>()
    }

    @Test
    fun `update renames the column`() {
        val renamed = controller.updateColumn(rename(" QA "), boardId, columnId("Done"))

        assertThat(renamed.id).isEqualTo(columnId("Done"))
        assertThat(renamed.name).isEqualTo("QA")
        assertThat(renamed.position).isEqualTo(2)
        assertThat(renamed.updatedAt).isEqualTo(clock.instant())
    }

    @Test
    fun `update with a broken name is VALIDATION_FAILED and changes nothing`() {
        assertFailure { controller.updateColumn(rename("   "), boardId, columnId("Done")) }
            .isInstanceOf<RequestValidationException>()
        assertThat(names()).containsExactly("To do" to 0, "In progress" to 1, "Done" to 2)
    }

    @Test
    fun `update of an unknown column is ColumnNotFoundException before the body is validated`() {
        assertFailure { controller.updateColumn(rename("   "), boardId, UUID.randomUUID()) }
            .isInstanceOf<ColumnNotFoundException>()
    }

    @Test
    fun `move returns all columns of the board in their new order`() {
        val moved = controller.moveColumn(MoveColumnRequest(BigInteger.ZERO), boardId, columnId("Done"))

        assertThat(moved.columns.map { it.name to it.position })
            .containsExactly("Done" to 0, "To do" to 1, "In progress" to 2)
        assertThat(names()).containsExactly("Done" to 0, "To do" to 1, "In progress" to 2)
    }

    @Test
    fun `move without a position is VALIDATION_FAILED and moves nothing`() {
        assertFailure { controller.moveColumn(MoveColumnRequest(null), boardId, columnId("Done")) }
            .isInstanceOf<RequestValidationException>()
            .prop(RequestValidationException::fieldErrors)
            .containsExactly(ProblemFieldError("position", FieldErrorCode.REQUIRED))
        assertThat(names()).containsExactly("To do" to 0, "In progress" to 1, "Done" to 2)
    }

    @Test
    fun `move of an unknown column is ColumnNotFoundException before the body is validated`() {
        assertFailure { controller.moveColumn(MoveColumnRequest(null), boardId, UUID.randomUUID()) }
            .isInstanceOf<ColumnNotFoundException>()
    }

    @Test
    fun `delete removes the column and closes the gap`() {
        controller.deleteColumn(boardId, columnId("To do"))

        assertThat(names()).containsExactly("In progress" to 0, "Done" to 1)
    }
}
