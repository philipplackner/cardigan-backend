package com.plcoding.cardigan.board.api.mapper

import com.plcoding.cardigan.board.api.dto.CreateLabelRequest
import com.plcoding.cardigan.board.api.dto.LabelColorDto
import com.plcoding.cardigan.board.api.dto.LabelDto
import com.plcoding.cardigan.board.api.dto.LabelListDto
import com.plcoding.cardigan.board.api.dto.UpdateLabelRequest
import com.plcoding.cardigan.board.domain.model.Label
import com.plcoding.cardigan.board.domain.model.LabelChanges
import com.plcoding.cardigan.board.domain.model.LabelColor
import com.plcoding.cardigan.common.domain.text.normalizeText

fun LabelColor.toLabelColorDto(): LabelColorDto = LabelColorDto.valueOf(name)

fun LabelColorDto.toLabelColor(): LabelColor = LabelColor.valueOf(name)

fun Label.toLabelDto(): LabelDto = LabelDto(
    id = id,
    name = name,
    color = color.toLabelColorDto(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun List<Label>.toLabelListDto(): LabelListDto = LabelListDto(labels = map { it.toLabelDto() })

/** The normalized name of a validated request. */
fun CreateLabelRequest.toLabelName(): String = normalizeText(name!!)

/** The color of a validated request. */
fun CreateLabelRequest.toLabelColor(): LabelColor = LabelColorDto.valueOf(color!!).toLabelColor()

/** The sent fields of a validated request, with the name normalized. */
fun UpdateLabelRequest.toLabelChanges(): LabelChanges = LabelChanges(
    name = name?.let { normalizeText(it) },
    color = color?.let { LabelColorDto.valueOf(it).toLabelColor() },
)
