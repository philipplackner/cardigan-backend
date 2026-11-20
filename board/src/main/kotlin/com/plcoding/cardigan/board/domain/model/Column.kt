package com.plcoding.cardigan.board.domain.model

import java.time.Instant
import java.util.UUID

/** A column of a board. The columns of one board have the positions 0 to n-1, each used once. */
data class Column(
    val id: UUID,
    val boardId: UUID,
    val name: String,
    val position: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
) {

    init {
        require(position >= 0) { "Position must not be negative" }
    }
}
