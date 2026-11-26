package com.plcoding.cardigan.board.api.controller

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import com.plcoding.cardigan.board.api.dto.BoardDto
import com.plcoding.cardigan.board.api.dto.CreateBoardRequest
import com.plcoding.cardigan.board.api.dto.UpdateBoardRequest
import com.plcoding.cardigan.board.domain.exception.BoardNotFoundException
import com.plcoding.cardigan.board.fake.FakeBoardRepository
import com.plcoding.cardigan.board.fake.FakeColumnRepository
import com.plcoding.cardigan.board.service.BoardService
import com.plcoding.cardigan.common.MutableClock
import com.plcoding.cardigan.common.api.ProblemFieldError
import com.plcoding.cardigan.common.api.validation.RequestValidationException
import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class BoardControllerTest {

    private val start = Instant.parse("2026-11-15T09:15:30.123Z")
    private val clock = MutableClock(start)
    private val columns = FakeColumnRepository()
    private val boards = FakeBoardRepository(columns)
    private val controller = BoardController(BoardService(boards, columns, clock), plainRequestValidator)

    private val ann = UUID.randomUUID()
    private val bob = UUID.randomUUID()

    @BeforeEach
    fun signInAnn() = signIn(ann)

    @AfterEach
    fun clear() = signOut()

    private fun rename(name: String?) = UpdateBoardRequest().apply { this.name = name }

    private fun createRoadmap(): BoardDto = controller.createBoard(CreateBoardRequest(" Roadmap ", " crd "))

    @Test
    fun `create returns the normalized board with its default columns for the request user`() {
        val board = createRoadmap()

        assertThat(board).all {
            prop(BoardDto::name).isEqualTo("Roadmap")
            prop(BoardDto::key).isEqualTo("CRD")
            prop(BoardDto::createdAt).isEqualTo(start)
            prop(BoardDto::updatedAt).isEqualTo(start)
        }
        assertThat(board.columns.map { it.name to it.position })
            .containsExactly("To do" to 0, "In progress" to 1, "Done" to 2)
        val stored = boards.all.single()
        assertThat(stored.ownerId).isEqualTo(ann)
        assertThat(board.id).isEqualTo(stored.id)
    }

    @Test
    fun `get returns the stored board with its columns`() {
        val created = createRoadmap()

        assertThat(controller.getBoard(created.id)).isEqualTo(created)
    }

    @Test
    fun `get of another user's board is BoardNotFoundException`() {
        val created = createRoadmap()
        signIn(bob)

        assertFailure { controller.getBoard(created.id) }.isInstanceOf<BoardNotFoundException>()
    }

    @Test
    fun `list returns the request user's boards as summaries`() {
        val created = createRoadmap()
        signIn(bob)
        controller.createBoard(CreateBoardRequest("Other", "OTH"))
        signIn(ann)

        val summary = controller.listBoards().boards.single()

        assertThat(summary.id).isEqualTo(created.id)
        assertThat(summary.name).isEqualTo("Roadmap")
        assertThat(summary.key).isEqualTo("CRD")
        assertThat(summary.createdAt).isEqualTo(start)
        assertThat(summary.updatedAt).isEqualTo(start)
    }

    @Test
    fun `update renames the board`() {
        val created = createRoadmap()
        clock.advanceBy(Duration.ofMinutes(1))

        val renamed = controller.updateBoard(rename(" Platform "), created.id)

        assertThat(renamed.name).isEqualTo("Platform")
        assertThat(renamed.updatedAt).isEqualTo(clock.instant())
        assertThat(renamed.columns).isEqualTo(created.columns)
    }

    @Test
    fun `update with a broken name is VALIDATION_FAILED and changes nothing`() {
        val created = createRoadmap()

        assertFailure { controller.updateBoard(rename("   "), created.id) }
            .isInstanceOf<RequestValidationException>()
            .prop(RequestValidationException::fieldErrors)
            .containsExactly(ProblemFieldError("name", FieldErrorCode.REQUIRED))
        assertThat(controller.getBoard(created.id)).isEqualTo(created)
    }

    @Test
    fun `update of an unknown board is BoardNotFoundException before the body is validated`() {
        assertFailure { controller.updateBoard(rename("   "), UUID.randomUUID()) }
            .isInstanceOf<BoardNotFoundException>()
    }

    @Test
    fun `delete removes the board and its columns`() {
        val created = createRoadmap()

        controller.deleteBoard(created.id)

        assertThat(boards.all).isEmpty()
        assertThat(columns.all).isEmpty()
    }
}
