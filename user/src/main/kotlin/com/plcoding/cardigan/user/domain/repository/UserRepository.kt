package com.plcoding.cardigan.user.domain.repository

import com.plcoding.cardigan.user.domain.model.User
import java.util.UUID

interface UserRepository {

    fun findById(id: UUID): User?

    fun existsByEmail(email: String): Boolean

    fun save(user: User): User
}
