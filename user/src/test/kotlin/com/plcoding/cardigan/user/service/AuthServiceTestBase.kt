package com.plcoding.cardigan.user.service

import com.plcoding.cardigan.common.MutableClock
import com.plcoding.cardigan.user.domain.model.Credentials
import com.plcoding.cardigan.user.domain.model.IssuedSession
import com.plcoding.cardigan.user.domain.model.IssuedTokens
import com.plcoding.cardigan.user.domain.model.RefreshResult
import com.plcoding.cardigan.user.domain.model.RefreshToken
import com.plcoding.cardigan.user.domain.model.RefreshTokenTransport
import com.plcoding.cardigan.user.domain.model.RefreshTokenTransport.BODY
import com.plcoding.cardigan.user.domain.model.Session
import com.plcoding.cardigan.user.domain.model.hashRefreshToken
import com.plcoding.cardigan.user.domain.model.normalizeEmail
import com.plcoding.cardigan.user.fake.FakeAccessTokenIssuer
import com.plcoding.cardigan.user.fake.FakeAuthRateLimiter
import com.plcoding.cardigan.user.fake.FakePasswordHasher
import com.plcoding.cardigan.user.fake.FakeRefreshTokenGenerator
import com.plcoding.cardigan.user.fake.FakeRefreshTokenRepository
import com.plcoding.cardigan.user.fake.FakeSessionRepository
import com.plcoding.cardigan.user.fake.FakeUserRepository
import java.time.Instant

abstract class AuthServiceTestBase {
    protected val start: Instant = Instant.parse("2026-11-15T09:15:30.123Z")
    protected val clock = MutableClock(start)
    protected val users = FakeUserRepository()
    protected val refreshTokens = FakeRefreshTokenRepository()
    protected val sessions = FakeSessionRepository(refreshTokens)
    protected val hasher = FakePasswordHasher()
    protected val generator = FakeRefreshTokenGenerator()
    protected val rateLimiter = FakeAuthRateLimiter()
    protected val accessTokens = FakeAccessTokenIssuer(clock)

    private val sessionService = SessionService(users, sessions, refreshTokens, generator, AuthProperties(), clock)

    protected val service =
        AuthService(
            userRepository = users,
            sessionRepository = sessions,
            refreshTokenRepository = refreshTokens,
            sessionService = sessionService,
            passwordHasher = hasher,
            authRateLimiter = rateLimiter,
            accessTokenIssuer = accessTokens,
            clock = clock,
        )

    protected val password = "Correct horse 7"

    protected fun credentials(
        email: String = "ann@example.com",
        password: String = this.password,
        transport: RefreshTokenTransport = BODY,
    ) = Credentials(normalizeEmail(email), password, transport)

    protected fun register(
        email: String = "ann@example.com",
        transport: RefreshTokenTransport = BODY,
    ): IssuedSession = service.register(credentials(email, transport = transport))

    protected fun login(
        email: String = "ann@example.com",
        transport: RefreshTokenTransport = BODY,
    ): IssuedSession = service.login(credentials(email, transport = transport))

    protected fun refreshed(
        token: String,
        transport: RefreshTokenTransport = BODY,
    ): IssuedTokens = (service.refresh(transport, token) as RefreshResult.Refreshed).tokens

    protected fun stored(token: String): RefreshToken = refreshTokens.findByTokenHash(hashRefreshToken(token))!!

    protected fun sessionOf(token: String): Session = sessions.findById(stored(token).sessionId)!!
}
