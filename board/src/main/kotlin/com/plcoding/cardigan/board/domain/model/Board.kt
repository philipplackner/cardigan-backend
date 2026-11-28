package com.plcoding.cardigan.board.domain.model

import java.time.Instant
import java.util.UUID

/** A board. [ownerId] is the ID of the user who created it; [key] is uppercase and never changes. */
data class Board(
    val id: UUID,
    val ownerId: UUID,
    val name: String,
    val key: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(checkBoardKey(key) == null) { "Board key must be 2 to 5 letters A to Z" }
    }
}

/** A board together with all its columns in position order. */
data class BoardWithColumns(
    val board: Board,
    val columns: List<Column>,
)
