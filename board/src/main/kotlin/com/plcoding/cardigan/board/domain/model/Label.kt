package com.plcoding.cardigan.board.domain.model

import com.plcoding.cardigan.board.domain.text.foldCase
import java.time.Instant
import java.util.UUID

/**
 * A label of one user. [name] is the normalized name as entered. [nameKey] is its case-folded form,
 * which decides uniqueness per owner and the list order.
 */
data class Label(
    val id: UUID,
    val ownerId: UUID,
    val name: String,
    val color: LabelColor,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val nameKey: String = foldCase(name)
}

/** The fields an update sent. Null means the field was not sent and keeps its value. */
data class LabelChanges(
    val name: String? = null,
    val color: LabelColor? = null,
)
