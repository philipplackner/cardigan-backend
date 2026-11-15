package com.plcoding.cardigan.user

import com.plcoding.cardigan.user.domain.model.User
import com.plcoding.cardigan.user.domain.repository.UserRepository
import java.time.Clock
import java.util.UUID

/** Creates users directly in the repository, for tests that need a user before register exists. */
class UserFixtures(
    private val userRepository: UserRepository,
    private val clock: Clock,
) {

    fun createUser(email: String = "ann@example.com"): User {
        val now = clock.instant()
        return userRepository.save(
            User(
                id = UUID.randomUUID(),
                email = email,
                passwordHash = "not-a-real-hash",
                createdAt = now,
                updatedAt = now,
            ),
        )
    }
}
