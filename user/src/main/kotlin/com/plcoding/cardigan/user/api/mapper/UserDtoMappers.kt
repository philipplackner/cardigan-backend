package com.plcoding.cardigan.user.api.mapper

import com.plcoding.cardigan.user.api.dto.CurrentUserDto
import com.plcoding.cardigan.user.domain.model.User

fun User.toCurrentUserDto(): CurrentUserDto =
    CurrentUserDto(
        id = id,
        email = email,
        createdAt = createdAt,
    )
