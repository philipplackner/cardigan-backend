package com.plcoding.cardigan.user.service

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.plcoding.cardigan.common.domain.exception.UnauthenticatedException
import com.plcoding.cardigan.user.domain.exception.CurrentUserNotFoundException
import com.plcoding.cardigan.user.domain.model.User
import com.plcoding.cardigan.user.fake.FakeUserRepository
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class UserServiceTest {
    private val users = FakeUserRepository()
    private val service = UserService(users)

    private val user =
        User(
            id = UUID.randomUUID(),
            email = "ann@example.com",
            passwordHash = "hash",
            createdAt = Instant.parse("2026-11-15T09:15:30.123Z"),
            updatedAt = Instant.parse("2026-11-15T09:15:30.123Z"),
        )

    @Test
    fun `known user is returned`() {
        users.save(user)

        assertThat(service.getCurrentUser(user.id)).isEqualTo(user)
    }

    @Test
    fun `unknown user is an unauthenticated failure`() {
        users.save(user)

        assertFailure { service.getCurrentUser(UUID.randomUUID()) }
            .isInstanceOf(CurrentUserNotFoundException::class)
            .isInstanceOf(UnauthenticatedException::class)
    }
}
