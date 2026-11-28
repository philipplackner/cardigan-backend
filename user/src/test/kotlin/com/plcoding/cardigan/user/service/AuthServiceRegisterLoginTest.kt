package com.plcoding.cardigan.user.service

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import com.plcoding.cardigan.common.domain.exception.InvalidCredentialsException
import com.plcoding.cardigan.common.domain.exception.UnauthenticatedException
import com.plcoding.cardigan.user.domain.exception.AuthRateLimitedException
import com.plcoding.cardigan.user.domain.exception.EmailAlreadyUsedException
import com.plcoding.cardigan.user.domain.model.AuthAction
import com.plcoding.cardigan.user.domain.model.RefreshTokenTransport.BODY
import com.plcoding.cardigan.user.domain.model.RefreshTokenTransport.COOKIE
import com.plcoding.cardigan.user.domain.model.hashRefreshToken
import com.plcoding.cardigan.user.fake.FakePasswordHasher
import org.junit.jupiter.api.Test
import java.time.Duration

class AuthServiceRegisterLoginTest : AuthServiceTestBase() {
    @Test
    fun `register stores a lowercased email and the password hash`() {
        val issued = service.register(credentials("  Ann@Example.com "))

        val user = users.users.values.single()
        assertThat(user.email).isEqualTo("ann@example.com")
        assertThat(user.passwordHash).isEqualTo(FakePasswordHasher.hashOf(password))
        assertThat(user.createdAt).isEqualTo(start)
        assertThat(user.updatedAt).isEqualTo(start)
        assertThat(issued.user).isEqualTo(user)
    }

    @Test
    fun `register creates one session with one token expiring in 30 days`() {
        val issued = register(transport = COOKIE)

        val session = sessions.sessions.values.single()
        val token = refreshTokens.tokens.values.single()
        assertThat(session.userId).isEqualTo(issued.user.id)
        assertThat(session.transport).isEqualTo(COOKIE)
        assertThat(session.createdAt).isEqualTo(start)
        assertThat(session.revokedAt).isNull()
        assertThat(token.sessionId).isEqualTo(session.id)
        assertThat(token.tokenHash).isEqualTo(hashRefreshToken(issued.tokens.refreshToken))
        assertThat(token.createdAt).isEqualTo(start)
        assertThat(token.expiresAt).isEqualTo(start.plus(Duration.ofDays(30)))
        assertThat(token.rotatedAt).isNull()
    }

    @Test
    fun `register returns the generated refresh token and an access token for the new user`() {
        val issued = register(transport = COOKIE)

        assertThat(issued.tokens.refreshToken).isEqualTo(generator.generated.single())
        assertThat(issued.tokens.refreshTokenExpiresAt).isEqualTo(start.plus(Duration.ofDays(30)))
        assertThat(issued.tokens.transport).isEqualTo(COOKIE)
        val (userId, accessToken) = accessTokens.issued.single()
        assertThat(userId).isEqualTo(issued.user.id)
        assertThat(issued.tokens.accessToken).isEqualTo(accessToken.token)
        assertThat(issued.tokens.accessTokenExpiresAt).isEqualTo(accessToken.expiresAt)
    }

    @Test
    fun `register hashes the password as it is, with its whitespace`() {
        service.register(credentials(password = "  Correct horse 7 "))

        assertThat(hasher.hashed).containsExactly("  Correct horse 7 ")
    }

    @Test
    fun `used email throws and stores nothing new`() {
        register("ann@example.com")

        assertFailure { register("ANN@Example.com") }.isInstanceOf<EmailAlreadyUsedException>()

        assertThat(users.users).hasSize(1)
        assertThat(sessions.sessions).hasSize(1)
        assertThat(refreshTokens.tokens).hasSize(1)
        assertThat(accessTokens.issued).hasSize(1)
    }

    @Test
    fun `email stored after the existence check throws the same exception`() {
        register("ann@example.com")
        users.existenceCheckSeesNothing = true

        assertFailure { register("ann@example.com") }.isInstanceOf<EmailAlreadyUsedException>()

        assertThat(users.users).hasSize(1)
        assertThat(sessions.sessions).hasSize(1)
    }

    @Test
    fun `two users with the same password register independently`() {
        val ann = register("ann@example.com")
        val bob = register("bob@example.com")

        assertThat(ann.user.id).isNotEqualTo(bob.user.id)
        assertThat(ann.tokens.refreshToken).isNotEqualTo(bob.tokens.refreshToken)
        assertThat(sessions.active).hasSize(2)
    }

    @Test
    fun `login returns the user and starts a new session with its transport`() {
        val registered = register(transport = BODY)
        clock.advanceBy(Duration.ofHours(1))

        val issued = login(transport = COOKIE)

        assertThat(issued.user).isEqualTo(registered.user)
        assertThat(sessions.active).hasSize(2)
        val session = sessionOf(issued.tokens.refreshToken)
        assertThat(session.transport).isEqualTo(COOKIE)
        assertThat(session.userId).isEqualTo(registered.user.id)
        assertThat(stored(issued.tokens.refreshToken).expiresAt).isEqualTo(clock.instant().plus(Duration.ofDays(30)))
        assertThat(issued.tokens.refreshTokenExpiresAt).isEqualTo(clock.instant().plus(Duration.ofDays(30)))
        assertThat(issued.tokens.refreshToken).isNotEqualTo(registered.tokens.refreshToken)
    }

    @Test
    fun `wrong password and unknown email throw the same exception and verify once each`() {
        register("ann@example.com")

        val wrongPassword = runCatching { service.login(credentials(password = "Wrong horse 7")) }.exceptionOrNull()!!
        val wrongPasswordVerifications = hasher.verified.toList()
        hasher.verified.clear()
        val unknownEmail = runCatching { service.login(credentials("bob@example.com")) }.exceptionOrNull()!!

        assertThat(wrongPassword::class).isEqualTo(InvalidCredentialsException::class)
        assertThat(unknownEmail::class).isEqualTo(InvalidCredentialsException::class)
        assertThat(wrongPassword.message).isEqualTo(unknownEmail.message)
        assertThat(wrongPasswordVerifications)
            .containsExactly("Wrong horse 7" to FakePasswordHasher.hashOf(password))
        assertThat(hasher.verified).containsExactly(password to null)
        assertThat(sessions.sessions).hasSize(1)
    }

    @Test
    fun `failed login is not an unauthenticated failure`() {
        assertFailure { login("nobody@example.com") }
            .transform { it is UnauthenticatedException }
            .isEqualTo(false)
    }

    @Test
    fun `successful login verifies the password once`() {
        register()

        login()

        assertThat(hasher.verified).hasSize(1)
    }

    @Test
    fun `stored email that breaks the format logs in with the right password`() {
        register(email = "ann@localhost")

        val session = login(email = "ann@localhost")

        assertThat(session.user.email).isEqualTo("ann@localhost")
    }

    @Test
    fun `malformed email is looked up and fails as unknown`() {
        assertFailure { service.login(credentials("not an email")) }.isInstanceOf<InvalidCredentialsException>()

        assertThat(users.emailLookups).isEqualTo(1)
        assertThat(hasher.verified).hasSize(1)
    }

    @Test
    fun `login in another case succeeds`() {
        val registered = service.register(credentials("Ann@Example.com"))

        val issued = service.login(credentials(" ANN@example.COM "))

        assertThat(issued.user).isEqualTo(registered.user)
    }

    @Test
    fun `password with a leading space fails`() {
        register()

        assertFailure {
            service.login(
                credentials(password = " $password"),
            )
        }.isInstanceOf<InvalidCredentialsException>()
        assertFailure {
            service.login(
                credentials(password = "$password "),
            )
        }.isInstanceOf<InvalidCredentialsException>()
    }

    @Test
    fun `password in another case fails`() {
        register()

        assertFailure { service.login(credentials(password = password.lowercase())) }
            .isInstanceOf<InvalidCredentialsException>()
    }

    @Test
    fun `20 logins leave 20 active sessions, each with its own token`() {
        val registered = register()
        sessions.sessions.clear()
        refreshTokens.tokens.clear()

        val logins =
            (1..20).map {
                clock.advanceBy(Duration.ofMinutes(1))
                login()
            }

        assertThat(sessions.active).hasSize(20)
        assertThat(sessions.active.map { it.userId }.toSet()).isEqualTo(setOf(registered.user.id))
        assertThat(logins.map { it.tokens.refreshToken }.toSet()).hasSize(20)
        assertThat(logins.map { sessionOf(it.tokens.refreshToken).id }.toSet()).hasSize(20)
        assertThat(
            refreshed(logins.first().tokens.refreshToken).refreshToken,
        ).isNotEqualTo(logins.first().tokens.refreshToken)
    }

    @Test
    fun `failed login starts no session and issues no token`() {
        register()
        val issuedBefore = accessTokens.issued.size

        runCatching { service.login(credentials(password = "Wrong horse 7")) }

        assertThat(sessions.sessions).hasSize(1)
        assertThat(refreshTokens.tokens).hasSize(1)
        assertThat(accessTokens.issued).hasSize(issuedBefore)
    }

    @Test
    fun `rate limit key is the normalized, lowercased email`() {
        service.checkRateLimit(AuthAction.LOGIN, "ann@example.com")
        service.checkRateLimit(AuthAction.LOGIN, " Ann@Example.com ")
        service.checkRateLimit(AuthAction.REGISTER, "ANN@EXAMPLE.COM")

        assertThat(rateLimiter.checks).containsExactly(
            AuthAction.LOGIN to "ann@example.com",
            AuthAction.LOGIN to "ann@example.com",
            AuthAction.REGISTER to "ann@example.com",
        )
    }

    @Test
    fun `request without an email is not counted`() {
        service.checkRateLimit(AuthAction.LOGIN, null)
        service.checkRateLimit(AuthAction.LOGIN, "")
        service.checkRateLimit(AuthAction.REGISTER, "    ")

        assertThat(rateLimiter.checks).isEmpty()
    }

    @Test
    fun `invalid emails are counted too`() {
        service.checkRateLimit(AuthAction.REGISTER, "not an email")
        service.checkRateLimit(AuthAction.LOGIN, "ann\u0000@example.com")

        assertThat(rateLimiter.checks).hasSize(2)
    }

    @Test
    fun `over the limit throws the limiter's exception`() {
        rateLimiter.rejectWithRetryAfter = 7

        assertFailure { service.checkRateLimit(AuthAction.LOGIN, "ann@example.com") }
            .isInstanceOf<AuthRateLimitedException>()
            .transform { it.retryAfterSeconds }
            .isEqualTo(7L)
    }

    @Test
    fun `register and login don't count toward the limit themselves`() {
        register()
        login()

        assertThat(rateLimiter.checks).isEmpty()
    }

    @Test
    fun `login returns the stored user object's data, not the submitted email`() {
        val registered = service.register(credentials("Ann@Example.com"))

        assertThat(service.login(credentials("ANN@EXAMPLE.COM")).user.email).isEqualTo("ann@example.com")
        assertThat(users.findById(registered.user.id)).isSameInstanceAs(registered.user)
    }
}
