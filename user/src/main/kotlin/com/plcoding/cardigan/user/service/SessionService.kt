package com.plcoding.cardigan.user.service

import com.plcoding.cardigan.user.domain.exception.EmailAlreadyUsedException
import com.plcoding.cardigan.user.domain.model.IssuedRefreshToken
import com.plcoding.cardigan.user.domain.model.RefreshToken
import com.plcoding.cardigan.user.domain.model.RefreshTokenTransport
import com.plcoding.cardigan.user.domain.model.Session
import com.plcoding.cardigan.user.domain.model.User
import com.plcoding.cardigan.user.domain.model.hashRefreshToken
import com.plcoding.cardigan.user.domain.repository.RefreshTokenGenerator
import com.plcoding.cardigan.user.domain.repository.RefreshTokenRepository
import com.plcoding.cardigan.user.domain.repository.SessionRepository
import com.plcoding.cardigan.user.domain.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * The database parts of register and login. They live in their own bean so that `AuthService` can
 * hash passwords outside a transaction and still get one through the proxy.
 */
@Service
class SessionService(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val refreshTokenGenerator: RefreshTokenGenerator,
    private val properties: AuthProperties,
    private val clock: Clock,
) {

    /** Stores a new user and starts their first session. [email] must be normalized and lowercased. */
    @Transactional
    fun registerUser(email: String, passwordHash: String, transport: RefreshTokenTransport): Pair<User, IssuedRefreshToken> {
        if (userRepository.existsByEmail(email)) throw EmailAlreadyUsedException()
        val now = clock.instant()
        val user = userRepository.save(
            User(id = UUID.randomUUID(), email = email, passwordHash = passwordHash, createdAt = now, updatedAt = now),
        )
        return user to start(user.id, transport)
    }

    /** Starts a session with its first refresh token. */
    @Transactional
    fun start(userId: UUID, transport: RefreshTokenTransport): IssuedRefreshToken {
        val session = sessionRepository.save(
            Session(id = UUID.randomUUID(), userId = userId, transport = transport, createdAt = clock.instant()),
        )
        return issueToken(session.id)
    }

    /** Adds a new refresh token to a session. Only its hash is stored. */
    @Transactional
    fun issueToken(sessionId: UUID): IssuedRefreshToken {
        val value = refreshTokenGenerator.generate()
        val now = clock.instant()
        val refreshToken = refreshTokenRepository.save(
            RefreshToken(
                id = UUID.randomUUID(),
                sessionId = sessionId,
                tokenHash = hashRefreshToken(value),
                createdAt = now,
                expiresAt = now.plus(properties.refreshTokenTtl),
            ),
        )
        return IssuedRefreshToken(value, refreshToken.expiresAt)
    }
}
