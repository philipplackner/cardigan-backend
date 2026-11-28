package com.plcoding.cardigan.common.api

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.plcoding.cardigan.common.domain.exception.UnauthenticatedException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.util.UUID

class RequestUserTest {
    @AfterEach
    fun clear() = SecurityContextHolder.clearContext()

    @Test
    fun `request user is the token subject`() {
        val userId = UUID.fromString("0b6a1f43-58c2-4d0e-9a35-3f1c9e2b7d10")
        val jwt =
            Jwt
                .withTokenValue("token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)

        assertThat(requestUserId).isEqualTo(userId)
    }

    @Test
    fun `no authentication is unauthenticated`() {
        assertFailure { requestUserId }.isInstanceOf<UnauthenticatedException>()
    }

    @Test
    fun `authentication without a token is unauthenticated`() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken.authenticated("0b6a1f43-58c2-4d0e-9a35-3f1c9e2b7d10", null, emptyList())

        assertFailure { requestUserId }.isInstanceOf<UnauthenticatedException>()
    }
}
