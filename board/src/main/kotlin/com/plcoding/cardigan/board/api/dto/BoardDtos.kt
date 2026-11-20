package com.plcoding.cardigan.board.api.dto

import com.plcoding.cardigan.board.api.validation.ValidBoardKey
import com.plcoding.cardigan.common.api.validation.PatchRequest
import com.plcoding.cardigan.common.api.validation.Required
import com.plcoding.cardigan.common.api.validation.ValidText
import java.time.Instant
import java.util.UUID

/** A full board: its own fields plus all its columns in position order. */
data class BoardDto(
    val id: UUID,
    val name: String,
    val key: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val columns: List<ColumnDto>,
)

/** A board as an entry of the board list, without its columns. */
data class BoardSummaryDto(
    val id: UUID,
    val name: String,
    val key: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class BoardListDto(
    val boards: List<BoardSummaryDto>,
)

data class CreateBoardRequest(
    @Required
    @ValidText(min = 1, max = 100)
    val name: String?,
    @Required
    @ValidBoardKey
    val key: String?,
)

class UpdateBoardRequest : PatchRequest() {

    @ValidText(min = 1, max = 100)
    var name: String? = null
        set(value) {
            field = sent("name", value)
        }
}
