package com.plcoding.cardigan.user.api.dto

import java.time.Instant
import java.util.UUID

data class CurrentUserDto(
    val id: UUID,
    val email: String,
    val createdAt: Instant,
)
