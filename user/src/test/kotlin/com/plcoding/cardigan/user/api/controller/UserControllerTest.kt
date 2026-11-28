package com.plcoding.cardigan.user.api.controller

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import com.plcoding.cardigan.user.api.dto.CurrentUserDto
import com.plcoding.cardigan.user.domain.exception.CurrentUserNotFoundException
import com.plcoding.cardigan.user.domain.model.User
import com.plcoding.cardigan.user.fake.FakeUserRepository
import com.plcoding.cardigan.user.service.UserService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant
import java.util.UUID

class UserControllerTest {
    private val users = FakeUserRepository()
    private val controller = UserController(UserService(users))

    private val user =
        User(
            id = UUID.fromString("0f8c2b4e-5d6a-4c3b-9e1f-2a7b8c9d0e1f"),
            email = "ann@example.com",
            passwordHash = "hash",
            createdAt = Instant.parse("2026-11-15T09:15:30.123Z"),
            updatedAt = Instant.parse("2026-11-20T10:00:00Z"),
        )

    @AfterEach
    fun clearSecurityContext() = SecurityContextHolder.clearContext()

    private fun signIn(userId: UUID) {
        val jwt =
            Jwt
                .withTokenValue("token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)
    }

    @Test
    fun `returns every field of the user named by the token`() {
        users.save(user)
        users.save(user.copy(id = UUID.randomUUID(), email = "bob@example.com"))
        signIn(user.id)

        assertThat(controller.getCurrentUser()).all {
            prop(CurrentUserDto::id).isEqualTo(user.id)
            prop(CurrentUserDto::email).isEqualTo("ann@example.com")
            prop(CurrentUserDto::createdAt).isEqualTo(user.createdAt)
        }
    }

    @Test
    fun `user without a record is unauthenticated`() {
        signIn(user.id)

        assertFailure { controller.getCurrentUser() }.isInstanceOf(CurrentUserNotFoundException::class)
    }
}
