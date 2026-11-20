package com.plcoding.cardigan.user.infra.security

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.matches
import org.junit.jupiter.api.Test
import java.util.Base64

class SecureRandomRefreshTokenGeneratorTest {

    private val generator = SecureRandomRefreshTokenGenerator()

    @Test
    fun `token has 43 base64url characters`() {
        repeat(100) {
            assertThat(generator.generate()).matches(Regex("^[A-Za-z0-9_-]{43}$"))
        }
    }

    @Test
    fun `token decodes to 32 bytes`() {
        assertThat(Base64.getUrlDecoder().decode(generator.generate()).size).isEqualTo(32)
    }

    @Test
    fun `1000 generated tokens are distinct`() {
        assertThat((1..1000).map { generator.generate() }.toSet()).hasSize(1000)
    }

    @Test
    fun `two generators don't repeat each other`() {
        val other = SecureRandomRefreshTokenGenerator()

        assertThat((1..100).flatMap { listOf(generator.generate(), other.generate()) }.toSet()).hasSize(200)
    }
}
