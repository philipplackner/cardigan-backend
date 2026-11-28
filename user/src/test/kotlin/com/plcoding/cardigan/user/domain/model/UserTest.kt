package com.plcoding.cardigan.user.domain.model

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class UserTest {
    private val now = Instant.parse("2026-11-15T09:15:30.123Z")

    private fun user(email: String) = User(UUID.randomUUID(), email, "hash", now, now)

    @Test
    fun `lowercase email is accepted`() {
        assertThat(user("ann@example.com").email).isEqualTo("ann@example.com")
    }

    @Test
    fun `mixed-case email is rejected`() {
        assertFailure { user("Ann@Example.com") }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `single uppercase letter in the domain is rejected`() {
        assertFailure { user("ann@example.coM") }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `toString leaves out the password hash`() {
        assertThat(User(UUID.randomUUID(), "ann@example.com", "secret-hash", now, now).toString())
            .doesNotContain("secret-hash")
    }
}
