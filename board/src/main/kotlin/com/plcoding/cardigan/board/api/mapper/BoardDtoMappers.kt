package com.plcoding.cardigan.board.api.mapper

import com.plcoding.cardigan.board.api.dto.BoardDto
import com.plcoding.cardigan.board.api.dto.BoardListDto
import com.plcoding.cardigan.board.api.dto.BoardSummaryDto
import com.plcoding.cardigan.board.api.dto.CreateBoardRequest
import com.plcoding.cardigan.board.api.dto.UpdateBoardRequest
import com.plcoding.cardigan.board.domain.model.Board
import com.plcoding.cardigan.board.domain.model.BoardWithColumns
import com.plcoding.cardigan.board.domain.model.normalizeBoardKey
import com.plcoding.cardigan.common.domain.text.normalizeText

fun BoardWithColumns.toBoardDto(): BoardDto = BoardDto(
    id = board.id,
    name = board.name,
    key = board.key,
    createdAt = board.createdAt,
    updatedAt = board.updatedAt,
    columns = columns.toColumnDtos(),
)

fun Board.toBoardSummaryDto(): BoardSummaryDto = BoardSummaryDto(
    id = id,
    name = name,
    key = key,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun List<Board>.toBoardListDto(): BoardListDto = BoardListDto(map { it.toBoardSummaryDto() })

/** The normalized name of a validated request. */
fun CreateBoardRequest.toBoardName(): String = normalizeText(checkNotNull(name))

/** The normalized key of a validated request. */
fun CreateBoardRequest.toBoardKey(): String = normalizeBoardKey(checkNotNull(key))

/** The normalized name, or null when the request didn't send one. */
fun UpdateBoardRequest.toBoardName(): String? = name?.let(::normalizeText)
