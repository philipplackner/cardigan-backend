package com.plcoding.cardigan.user.service

import com.plcoding.cardigan.common.domain.exception.InvalidCredentialsException
import com.plcoding.cardigan.common.domain.security.AccessTokenIssuer
import com.plcoding.cardigan.user.domain.model.AuthAction
import com.plcoding.cardigan.user.domain.model.Credentials
import com.plcoding.cardigan.user.domain.model.IssuedRefreshToken
import com.plcoding.cardigan.user.domain.model.IssuedSession
import com.plcoding.cardigan.user.domain.model.IssuedTokens
import com.plcoding.cardigan.user.domain.model.RefreshResult
import com.plcoding.cardigan.user.domain.model.RefreshTokenTransport
import com.plcoding.cardigan.user.domain.model.hashRefreshToken
import com.plcoding.cardigan.user.domain.model.normalizeEmail
import com.plcoding.cardigan.user.domain.repository.AuthRateLimiter
import com.plcoding.cardigan.user.domain.repository.PasswordHasher
import com.plcoding.cardigan.user.domain.repository.RefreshTokenRepository
import com.plcoding.cardigan.user.domain.repository.SessionRepository
import com.plcoding.cardigan.user.domain.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val sessionService: SessionService,
    private val passwordHasher: PasswordHasher,
    private val authRateLimiter: AuthRateLimiter,
    private val accessTokenIssuer: AccessTokenIssuer,
    private val clock: Clock,
) {
    /**
     * Counts one request for the submitted [email] as it came in. A request without an email isn't
     * counted; field validation answers it. Throws `AuthRateLimitedException` over the limit.
     */
    fun checkRateLimit(
        action: AuthAction,
        email: String?,
    ) {
        val key = email?.let(::normalizeEmail).orEmpty()
        if (key.isNotEmpty()) {
            authRateLimiter.check(action, key)
        }
    }

    // Not transactional: no connection is held while the password is hashed.
    fun register(credentials: Credentials): IssuedSession {
        val passwordHash = passwordHasher.hash(credentials.password)
        val (user, refreshToken) = sessionService.registerUser(credentials.email, passwordHash, credentials.transport)
        return IssuedSession(user, tokensFor(user.id, refreshToken, credentials.transport))
    }

    /** An unknown email and a wrong password fail the same way and after the same hashing work. */
    fun login(credentials: Credentials): IssuedSession {
        val user = userRepository.findByEmail(credentials.email)
        val matches = passwordHasher.matches(credentials.password, user?.passwordHash)
        if (user == null || !matches) throw InvalidCredentialsException()
        val refreshToken = sessionService.start(user.id, credentials.transport)
        return IssuedSession(user, tokensFor(user.id, refreshToken, credentials.transport))
    }

    /**
     * Exchanges a refresh token for a new one. Using a token that was already rotated revokes its
     * session. The claim is one atomic update, so of several parallel requests with one token at
     * most one succeeds and the others count as reuse.
     */
    @Transactional
    fun refresh(
        transport: RefreshTokenTransport,
        token: String?,
    ): RefreshResult {
        val refreshToken =
            token?.let { refreshTokenRepository.findByTokenHash(hashRefreshToken(it)) }
                ?: return RefreshResult.Rejected
        val session = sessionRepository.findById(refreshToken.sessionId) ?: return RefreshResult.Rejected
        if (session.transport != transport || session.isRevoked) return RefreshResult.Rejected
        val now = clock.instant()
        if (refreshToken.isRotated) {
            sessionRepository.revoke(session.id, now)
            return RefreshResult.Rejected
        }
        if (refreshToken.isExpiredAt(now)) return RefreshResult.Rejected
        if (!refreshTokenRepository.markRotated(refreshToken.id, now)) {
            sessionRepository.revoke(session.id, now)
            return RefreshResult.Rejected
        }
        val newRefreshToken = sessionService.issueToken(session.id)
        return RefreshResult.Refreshed(tokensFor(session.userId, newRefreshToken, transport))
    }

    /**
     * Ends the session of [token]. Nothing happens, and nothing is reported, when the token is
     * missing, unknown, expired, of another transport, or its session is already revoked. A rotated
     * token still ends its session, also after its own expiry.
     */
    @Transactional
    fun logout(
        transport: RefreshTokenTransport,
        token: String?,
    ) {
        val refreshToken = token?.let { refreshTokenRepository.findByTokenHash(hashRefreshToken(it)) } ?: return
        val session = sessionRepository.findById(refreshToken.sessionId) ?: return
        if (session.transport != transport || session.isRevoked) return
        val now = clock.instant()
        if (refreshToken.isRotated || !refreshToken.isExpiredAt(now)) {
            sessionRepository.revoke(session.id, now)
        }
    }

    private fun tokensFor(
        userId: UUID,
        refreshToken: IssuedRefreshToken,
        transport: RefreshTokenTransport,
    ): IssuedTokens {
        val accessToken = accessTokenIssuer.issue(userId)
        return IssuedTokens(
            accessToken = accessToken.token,
            accessTokenExpiresAt = accessToken.expiresAt,
            refreshToken = refreshToken.value,
            refreshTokenExpiresAt = refreshToken.expiresAt,
            transport = transport,
        )
    }
}
