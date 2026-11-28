package com.plcoding.cardigan.user.infra.database.mapper

import com.plcoding.cardigan.user.domain.model.User
import com.plcoding.cardigan.user.infra.database.entity.UserEntity

fun UserEntity.toUser(): User =
    User(
        id = id,
        email = email,
        passwordHash = passwordHash,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun User.toUserEntity(): UserEntity =
    UserEntity(
        id = id,
        email = email,
        passwordHash = passwordHash,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
