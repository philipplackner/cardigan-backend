package com.plcoding.cardigan.board.api.dto

import com.plcoding.cardigan.common.api.validation.PatchRequest
import com.plcoding.cardigan.common.api.validation.Required
import com.plcoding.cardigan.common.api.validation.ValidText
import java.math.BigInteger
import java.time.Instant
import java.util.UUID

data class ColumnDto(
    val id: UUID,
    val name: String,
    val position: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** All columns of one board in position order. */
data class ColumnListDto(
    val columns: List<ColumnDto>,
)

data class CreateColumnRequest(
    @Required
    @ValidText(min = 1, max = 30)
    val name: String?,
)

class UpdateColumnRequest : PatchRequest() {

    @ValidText(min = 1, max = 30)
    var name: String? = null
        set(value) {
            field = sent("name", value)
        }
}

/** [position] is the column's final index. Its range depends on the board, so the service checks it. */
data class MoveColumnRequest(
    @Required
    val position: BigInteger?,
)
