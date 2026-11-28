package com.plcoding.cardigan.user.api.mapper

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.plcoding.cardigan.user.api.dto.LoginRequest
import com.plcoding.cardigan.user.api.dto.LogoutRequest
import com.plcoding.cardigan.user.api.dto.RefreshRequest
import com.plcoding.cardigan.user.api.dto.RegisterRequest
import com.plcoding.cardigan.user.domain.model.Credentials
import com.plcoding.cardigan.user.domain.model.RefreshTokenTransport
import org.junit.jupiter.api.Test

class AuthDtoMappersTest {
    @Test
    fun `register email is trimmed and lowercased`() {
        val credentials = RegisterRequest("  Ann@Example.com ", "Correct horse 7", "BODY").toCredentials()

        assertThat(credentials).isEqualTo(Credentials("ann@example.com", "Correct horse 7", RefreshTokenTransport.BODY))
    }

    @Test
    fun `login email is trimmed and lowercased`() {
        val credentials = LoginRequest(" ANN@example.COM ", "pw", "COOKIE").toCredentials()

        assertThat(credentials).isEqualTo(Credentials("ann@example.com", "pw", RefreshTokenTransport.COOKIE))
    }

    @Test
    fun `decomposed password is composed`() {
        val decomposed = "Café password 1"

        assertThat(RegisterRequest("ann@example.com", decomposed, "BODY").toCredentials().password)
            .isEqualTo("Café password 1")
        assertThat(LoginRequest("ann@example.com", decomposed, "BODY").toCredentials().password)
            .isEqualTo("Café password 1")
    }

    @Test
    fun `password keeps its whitespace and case`() {
        assertThat(LoginRequest("ann@example.com", "  Pass Word\t ", "BODY").toCredentials().password)
            .isEqualTo("  Pass Word\t ")
    }

    @Test
    fun `email lowercasing doesn't depend on the default locale`() {
        val default = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
        try {
            assertThat(
                LoginRequest("INFO@EXAMPLE.COM", "pw", "BODY").toCredentials().email,
            ).isEqualTo("info@example.com")
        } finally {
            java.util.Locale.setDefault(default)
        }
    }

    @Test
    fun `transports map by name`() {
        assertThat(RefreshRequest("COOKIE", null).toRefreshTokenTransport()).isEqualTo(RefreshTokenTransport.COOKIE)
        assertThat(LogoutRequest("BODY", "t").toRefreshTokenTransport()).isEqualTo(RefreshTokenTransport.BODY)
    }

    @Test
    fun `mapping an unvalidated request fails`() {
        assertFailure { RegisterRequest(null, "Correct horse 7", "BODY").toCredentials() }
            .isInstanceOf<IllegalStateException>()
    }
}
