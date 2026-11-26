package com.plcoding.cardigan.board.api.controller

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import com.plcoding.cardigan.board.api.dto.CreateLabelRequest
import com.plcoding.cardigan.board.api.dto.LabelColorDto
import com.plcoding.cardigan.board.api.dto.LabelDto
import com.plcoding.cardigan.board.api.dto.UpdateLabelRequest
import com.plcoding.cardigan.board.domain.exception.LabelNotFoundException
import com.plcoding.cardigan.board.domain.model.LabelColor
import com.plcoding.cardigan.board.fake.FakeLabelRepository
import com.plcoding.cardigan.board.service.LabelService
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

class LabelControllerTest {

    private val start = Instant.parse("2026-11-15T09:15:30.123Z")
    private val clock = MutableClock(start)
    private val labels = FakeLabelRepository()
    private val controller = LabelController(LabelService(labels, clock), plainRequestValidator)

    private val ann = UUID.randomUUID()
    private val bob = UUID.randomUUID()

    @BeforeEach
    fun signInAnn() = signIn(ann)

    @AfterEach
    fun clear() = signOut()

    private fun createBug(): LabelDto = controller.createLabel(CreateLabelRequest(" Bug ", "TEAL"))

    @Test
    fun `create returns the normalized label of the request user`() {
        val label = createBug()

        assertThat(label).all {
            prop(LabelDto::name).isEqualTo("Bug")
            prop(LabelDto::color).isEqualTo(LabelColorDto.TEAL)
            prop(LabelDto::createdAt).isEqualTo(start)
            prop(LabelDto::updatedAt).isEqualTo(start)
        }
        val stored = labels.all.single()
        assertThat(label.id).isEqualTo(stored.id)
        assertThat(stored.ownerId).isEqualTo(ann)
    }

    @Test
    fun `list returns only the request user's labels`() {
        val bug = createBug()
        signIn(bob)
        controller.createLabel(CreateLabelRequest("Other", "RED"))
        signIn(ann)

        assertThat(controller.listLabels().labels).containsExactly(bug)
    }

    @Test
    fun `update changes the sent fields`() {
        val bug = createBug()
        clock.advanceBy(Duration.ofMinutes(1))

        val updated = controller.updateLabel(UpdateLabelRequest().apply { color = "RED" }, bug.id)

        assertThat(updated).isEqualTo(bug.copy(color = LabelColorDto.RED, updatedAt = clock.instant()))
        assertThat(labels.all.single().color).isEqualTo(LabelColor.RED)
    }

    @Test
    fun `update with a broken color is VALIDATION_FAILED and changes nothing`() {
        val bug = createBug()

        assertFailure { controller.updateLabel(UpdateLabelRequest().apply { color = "MAUVE" }, bug.id) }
            .isInstanceOf<RequestValidationException>()
            .prop(RequestValidationException::fieldErrors)
            .containsExactly(ProblemFieldError("color", FieldErrorCode.INVALID_VALUE))
        assertThat(controller.listLabels().labels).containsExactly(bug)
    }

    @Test
    fun `update of another user's label is LabelNotFoundException before the body is validated`() {
        val bug = createBug()
        signIn(bob)

        assertFailure { controller.updateLabel(UpdateLabelRequest().apply { color = "MAUVE" }, bug.id) }
            .isInstanceOf<LabelNotFoundException>()
    }

    @Test
    fun `delete removes the label`() {
        val bug = createBug()

        controller.deleteLabel(bug.id)

        assertThat(labels.all).isEmpty()
    }
}
