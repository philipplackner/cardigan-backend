package com.plcoding.cardigan.user.domain.repository

import com.plcoding.cardigan.user.domain.model.User
import java.util.UUID

interface UserRepository {

    fun findById(id: UUID): User?

    fun findByEmail(email: String): User?

    fun existsByEmail(email: String): Boolean

    /** Stores the user right away. Throws `EmailAlreadyUsedException` when another user has the email. */
    fun save(user: User): User
}
