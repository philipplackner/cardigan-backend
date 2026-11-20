package com.plcoding.cardigan.board.api.dto

import com.plcoding.cardigan.common.api.validation.PatchRequest
import com.plcoding.cardigan.common.api.validation.ValidEnum
import com.plcoding.cardigan.common.api.validation.ValidText

class UpdateLabelRequest : PatchRequest() {

    @ValidText(min = 1, max = 30)
    var name: String? = null
        set(value) {
            field = sent("name", value)
        }

    @ValidEnum(LabelColorDto::class)
    var color: String? = null
        set(value) {
            field = sent("color", value)
        }
}
