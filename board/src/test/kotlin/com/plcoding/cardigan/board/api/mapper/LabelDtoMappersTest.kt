package com.plcoding.cardigan.board.api.mapper

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.plcoding.cardigan.board.api.dto.CreateLabelRequest
import com.plcoding.cardigan.board.api.dto.LabelColorDto
import com.plcoding.cardigan.board.api.dto.LabelDto
import com.plcoding.cardigan.board.api.dto.LabelListDto
import com.plcoding.cardigan.board.api.dto.UpdateLabelRequest
import com.plcoding.cardigan.board.domain.model.Label
import com.plcoding.cardigan.board.domain.model.LabelChanges
import com.plcoding.cardigan.board.domain.model.LabelColor
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class LabelDtoMappersTest {
    private val createdAt = Instant.parse("2026-11-15T09:15:30.123Z")
    private val updatedAt = Instant.parse("2026-11-15T09:16:30.123Z")

    private fun label(
        name: String = "Bug",
        color: LabelColor = LabelColor.RED,
    ) = Label(UUID.randomUUID(), UUID.randomUUID(), name, color, createdAt, updatedAt)

    @Test
    fun `domain and API palettes have the same constant names in the same order`() {
        assertThat(LabelColorDto.entries.map { it.name }).isEqualTo(LabelColor.entries.map { it.name })
    }

    @Test
    fun `every API color maps to the domain color of the same name and back`() {
        LabelColorDto.entries.forEach { dto ->
            assertThat(dto.toLabelColor().name, dto.name).isEqualTo(dto.name)
            assertThat(dto.toLabelColor().toLabelColorDto(), dto.name).isEqualTo(dto)
        }
    }

    @Test
    fun `every domain color maps to an API color`() {
        LabelColor.entries.forEach { color ->
            assertThat(color.toLabelColorDto().toLabelColor(), color.name).isEqualTo(color)
        }
    }

    @Test
    fun `label maps to its DTO without the owner`() {
        val label = label("Straße", LabelColor.FUCHSIA)

        assertThat(label.toLabelDto())
            .isEqualTo(LabelDto(label.id, "Straße", LabelColorDto.FUCHSIA, createdAt, updatedAt))
    }

    @Test
    fun `list keeps the given order`() {
        val first = label("api")
        val second = label("Bug")

        assertThat(listOf(first, second).toLabelListDto())
            .isEqualTo(LabelListDto(listOf(first.toLabelDto(), second.toLabelDto())))
        assertThat(emptyList<Label>().toLabelListDto()).isEqualTo(LabelListDto(emptyList()))
    }

    @Test
    fun `create request name is trimmed`() {
        assertThat(CreateLabelRequest("  Bug　", "RED").toLabelName()).isEqualTo("Bug")
    }

    @Test
    fun `create request name is composed to NFC`() {
        assertThat(CreateLabelRequest("Äpfel", "RED").toLabelName()).isEqualTo("Äpfel")
    }

    @Test
    fun `create request name keeps inner spaces and letter case`() {
        assertThat(CreateLabelRequest(" In  Review ", "RED").toLabelName()).isEqualTo("In  Review")
    }

    @Test
    fun `create request color maps by name`() {
        assertThat(CreateLabelRequest("Bug", "TEAL").toLabelColor()).isEqualTo(LabelColor.TEAL)
    }

    @Test
    fun `update request with both fields normalizes the name`() {
        val request =
            UpdateLabelRequest().apply {
                name = " Äpfel "
                color = "ROSE"
            }

        assertThat(request.toLabelChanges()).isEqualTo(LabelChanges(name = "Äpfel", color = LabelColor.ROSE))
    }

    @Test
    fun `update request leaves absent fields null`() {
        assertThat(UpdateLabelRequest().toLabelChanges()).isEqualTo(LabelChanges())

        val colorOnly = UpdateLabelRequest().apply { color = "BLUE" }.toLabelChanges()
        assertThat(colorOnly.name).isNull()
        assertThat(colorOnly.color).isEqualTo(LabelColor.BLUE)
    }
}
