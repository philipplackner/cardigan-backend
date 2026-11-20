package com.plcoding.cardigan.common.domain.exception

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.Test

class BaseExceptionsTest {

    @Test
    fun `rate limit keeps its wait time`() {
        assertThat(RateLimitedException(1).retryAfterSeconds).isEqualTo(1)
    }

    @Test
    fun `rate limit never has a wait of zero`() {
        assertFailure { RateLimitedException(0) }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `credential and refresh token failures are not unauthenticated failures`() {
        val failures: List<RuntimeException> = listOf(InvalidCredentialsException(), InvalidRefreshTokenException())

        assertThat(failures.count { it is UnauthenticatedException }).isEqualTo(0)
    }
}
