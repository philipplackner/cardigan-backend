package com.plcoding.cardigan.board.api.dto

import com.plcoding.cardigan.common.api.validation.Required
import com.plcoding.cardigan.common.api.validation.ValidEnum
import com.plcoding.cardigan.common.api.validation.ValidText

data class CreateLabelRequest(
    @Required
    @ValidText(min = 1, max = 30)
    val name: String?,
    @Required
    @ValidEnum(LabelColorDto::class)
    val color: String?,
)
