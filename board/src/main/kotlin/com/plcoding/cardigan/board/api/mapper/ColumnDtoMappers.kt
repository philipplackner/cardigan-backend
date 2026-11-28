package com.plcoding.cardigan.board.api.mapper

import com.plcoding.cardigan.board.api.dto.ColumnDto
import com.plcoding.cardigan.board.api.dto.ColumnListDto
import com.plcoding.cardigan.board.api.dto.CreateColumnRequest
import com.plcoding.cardigan.board.api.dto.MoveColumnRequest
import com.plcoding.cardigan.board.api.dto.UpdateColumnRequest
import com.plcoding.cardigan.board.domain.model.Column
import com.plcoding.cardigan.common.domain.text.normalizeText
import java.math.BigInteger

fun Column.toColumnDto(): ColumnDto =
    ColumnDto(
        id = id,
        name = name,
        position = position,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

/** The columns in ascending position order. */
fun List<Column>.toColumnDtos(): List<ColumnDto> = sortedBy { it.position }.map { it.toColumnDto() }

fun List<Column>.toColumnListDto(): ColumnListDto = ColumnListDto(toColumnDtos())

/** The normalized name of a validated request. */
fun CreateColumnRequest.toColumnName(): String = normalizeText(checkNotNull(name))

/** The normalized name, or null when the request didn't send one. */
fun UpdateColumnRequest.toColumnName(): String? = name?.let(::normalizeText)

/** The target position of a validated request. */
fun MoveColumnRequest.toPosition(): BigInteger = checkNotNull(position)
