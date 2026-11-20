package com.plcoding.cardigan.board.domain.model

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class LabelTest {

    private val now = Instant.parse("2026-11-15T09:15:30.123Z")

    private fun label(name: String) = Label(UUID.randomUUID(), UUID.randomUUID(), name, LabelColor.RED, now, now)

    @Test
    fun `palette has exactly the 20 decided names`() {
        assertThat(LabelColor.entries.map { it.name }).containsExactly(
            "AMBER", "BLUE", "BROWN", "CYAN", "EMERALD", "FUCHSIA", "GREEN", "INDIGO", "LIME", "OLIVE",
            "ORANGE", "PINK", "PURPLE", "RED", "ROSE", "SKY", "SLATE", "TEAL", "VIOLET", "YELLOW",
        )
    }

    @Test
    fun `name key is the folded name and the name stays as entered`() {
        val label = label("Straße")

        assertThat(label.name).isEqualTo("Straße")
        assertThat(label.nameKey).isEqualTo("strasse")
    }

    @Test
    fun `name key follows a renamed copy`() {
        val renamed = label("Bug").copy(name = "DOCS")

        assertThat(renamed.nameKey).isEqualTo("docs")
    }

    @Test
    fun `case variants share a name key but are different labels`() {
        val lower = label("bug")
        val upper = lower.copy(name = "Bug")

        assertThat(upper.nameKey).isEqualTo(lower.nameKey)
        assertThat(upper == lower).isEqualTo(false)
    }
}
