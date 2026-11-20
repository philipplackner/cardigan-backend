package com.plcoding.cardigan.board.api.dto

import java.time.Instant
import java.util.UUID

data class LabelDto(
    val id: UUID,
    val name: String,
    val color: LabelColorDto,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class LabelListDto(
    val labels: List<LabelDto>,
)
