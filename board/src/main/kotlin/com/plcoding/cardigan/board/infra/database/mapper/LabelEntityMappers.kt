package com.plcoding.cardigan.board.infra.database.mapper

import com.plcoding.cardigan.board.domain.model.Label
import com.plcoding.cardigan.board.infra.database.entity.LabelEntity

fun LabelEntity.toLabel(): Label =
    Label(
        id = id,
        ownerId = ownerId,
        name = name,
        color = color,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun Label.toLabelEntity(): LabelEntity =
    LabelEntity(
        id = id,
        ownerId = ownerId,
        name = name,
        nameKey = nameKey,
        color = color,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
