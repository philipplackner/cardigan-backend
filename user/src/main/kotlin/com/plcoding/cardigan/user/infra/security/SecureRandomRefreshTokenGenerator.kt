package com.plcoding.cardigan.user.infra.security

import com.plcoding.cardigan.user.domain.repository.RefreshTokenGenerator
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64

/** 32 random bytes as base64url without padding, 43 characters. */
@Component
class SecureRandomRefreshTokenGenerator : RefreshTokenGenerator {
    private val random = SecureRandom()

    override fun generate(): String {
        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        const val TOKEN_BYTES = 32
    }
}
