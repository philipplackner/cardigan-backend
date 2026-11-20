package com.plcoding.cardigan.user.fake

import com.plcoding.cardigan.user.domain.exception.AuthRateLimitedException
import com.plcoding.cardigan.user.domain.model.AuthAction
import com.plcoding.cardigan.user.domain.repository.AuthRateLimiter
import com.plcoding.cardigan.user.domain.repository.PasswordHasher
import com.plcoding.cardigan.user.domain.repository.RefreshTokenGenerator

class FakePasswordHasher : PasswordHasher {

    val hashed = mutableListOf<String>()
    val verified = mutableListOf<Pair<String, String?>>()

    override fun hash(password: String): String {
        hashed += password
        return hashOf(password)
    }

    override fun matches(password: String, hash: String?): Boolean {
        verified += password to hash
        return hash != null && hash == hashOf(password)
    }

    companion object {
        fun hashOf(password: String): String = "hashed<$password>"
    }
}

class FakeRefreshTokenGenerator : RefreshTokenGenerator {

    val generated = mutableListOf<String>()

    override fun generate(): String = "refresh-token-${generated.size + 1}".also { generated += it }
}

class FakeAuthRateLimiter : AuthRateLimiter {

    val checks = mutableListOf<Pair<AuthAction, String>>()
    var rejectWithRetryAfter: Long? = null

    override fun check(action: AuthAction, email: String) {
        checks += action to email
        rejectWithRetryAfter?.let { throw AuthRateLimitedException(action, it) }
    }
}
