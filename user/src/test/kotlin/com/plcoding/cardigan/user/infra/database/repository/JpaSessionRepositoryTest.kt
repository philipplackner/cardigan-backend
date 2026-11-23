package com.plcoding.cardigan.user.infra.database.repository

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.plcoding.cardigan.common.PostgresTestConfig
import com.plcoding.cardigan.user.domain.exception.EmailAlreadyUsedException
import com.plcoding.cardigan.user.domain.model.RefreshToken
import com.plcoding.cardigan.user.domain.model.RefreshTokenTransport
import com.plcoding.cardigan.user.domain.model.Session
import com.plcoding.cardigan.user.domain.model.User
import com.plcoding.cardigan.user.domain.model.hashRefreshToken
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Tag("integration")
@DataJpaTest
@ActiveProfiles("test")
@Import(PostgresTestConfig::class, JpaUserRepository::class, JpaSessionRepository::class, JpaRefreshTokenRepository::class)
class JpaSessionRepositoryTest @Autowired constructor(
    private val users: JpaUserRepository,
    private val sessions: JpaSessionRepository,
    private val refreshTokens: JpaRefreshTokenRepository,
    private val entityManager: TestEntityManager,
) {

    private val now = Instant.parse("2026-11-15T09:15:30.123Z")

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    private fun savedUser(email: String = "ann@example.com"): User =
        users.save(User(UUID.randomUUID(), email, "hash", now, now))

    private fun savedSession(
        user: User = savedUser(),
        transport: RefreshTokenTransport = RefreshTokenTransport.BODY,
        revokedAt: Instant? = null,
    ): Session = sessions.save(Session(UUID.randomUUID(), user.id, transport, now, revokedAt))

    private fun savedToken(session: Session, value: String = "token", rotatedAt: Instant? = null): RefreshToken =
        refreshTokens.save(
            RefreshToken(UUID.randomUUID(), session.id, hashRefreshToken(value), now, now.plus(Duration.ofDays(30)), rotatedAt),
        )

    private fun count(table: String): Long =
        (entityManager.entityManager.createNativeQuery("SELECT count(*) FROM user_service.$table").singleResult as Number).toLong()

    @Test
    fun `session reads back equal at millisecond precision`() {
        val session = savedSession(transport = RefreshTokenTransport.COOKIE, revokedAt = now.plusMillis(1))
        flushAndClear()

        assertThat(sessions.findById(session.id)).isEqualTo(session)
    }

    @Test
    fun `active session reads back without a revocation time`() {
        val session = savedSession()
        flushAndClear()

        assertThat(sessions.findById(session.id)).isEqualTo(session)
        assertThat(sessions.findById(UUID.randomUUID())).isNull()
    }

    @Test
    fun `refresh token reads back equal at millisecond precision`() {
        val token = savedToken(savedSession(), rotatedAt = now.plusMillis(999))
        flushAndClear()

        assertThat(refreshTokens.findByTokenHash(token.tokenHash)).isEqualTo(token)
    }

    @Test
    fun `token is found by its exact hash only`() {
        val token = savedToken(savedSession(), "token")
        flushAndClear()

        assertThat(refreshTokens.findByTokenHash(token.tokenHash.uppercase())).isNull()
        assertThat(refreshTokens.findByTokenHash("token")).isNull()
        assertThat(refreshTokens.findByTokenHash("")).isNull()
    }

    @Test
    fun `token hash is unique`() {
        val session = savedSession()
        savedToken(session, "same")
        flushAndClear()

        savedToken(session, "same")

        assertFailure { entityManager.flush() }.isInstanceOf<ConstraintViolationException>()
    }

    @Test
    fun `token hash is unique across sessions`() {
        savedToken(savedSession(savedUser("ann@example.com")), "same")
        flushAndClear()

        savedToken(savedSession(savedUser("bob@example.com")), "same")

        assertFailure { entityManager.flush() }.isInstanceOf<ConstraintViolationException>()
    }

    @Test
    fun `database rejects an unknown transport`() {
        val user = savedUser()
        flushAndClear()
        val insert = entityManager.entityManager.createNativeQuery(
            """
            INSERT INTO user_service.sessions (id, user_id, transport, created_at)
            VALUES (?1, ?2, 'HEADER', now())
            """,
        ).setParameter(1, UUID.randomUUID()).setParameter(2, user.id)

        assertFailure { insert.executeUpdate() }.isInstanceOf<ConstraintViolationException>()
    }

    @Test
    fun `session of an unknown user is rejected`() {
        sessions.save(Session(UUID.randomUUID(), UUID.randomUUID(), RefreshTokenTransport.BODY, now))

        assertFailure { entityManager.flush() }
    }

    @Test
    fun `token of an unknown session is rejected`() {
        refreshTokens.save(
            RefreshToken(UUID.randomUUID(), UUID.randomUUID(), hashRefreshToken("t"), now, now.plusSeconds(1)),
        )

        assertFailure { entityManager.flush() }
    }

    @Test
    fun `deleting a user removes its sessions and tokens, and nobody else's`() {
        val ann = savedUser("ann@example.com")
        val bob = savedUser("bob@example.com")
        val annSession = savedSession(ann)
        savedToken(annSession, "a1", rotatedAt = now)
        savedToken(annSession, "a2")
        savedToken(savedSession(ann), "a3")
        val bobToken = savedToken(savedSession(bob), "b1")
        flushAndClear()

        entityManager.entityManager.createNativeQuery("DELETE FROM user_service.users WHERE id = ?1")
            .setParameter(1, ann.id)
            .executeUpdate()

        assertThat(count("sessions")).isEqualTo(1)
        assertThat(count("refresh_tokens")).isEqualTo(1)
        assertThat(refreshTokens.findByTokenHash(bobToken.tokenHash)).isEqualTo(bobToken)
    }

    @Test
    fun `deleting a session removes its tokens`() {
        val session = savedSession()
        savedToken(session, "t1", rotatedAt = now)
        savedToken(session, "t2")
        flushAndClear()

        entityManager.entityManager.createNativeQuery("DELETE FROM user_service.sessions WHERE id = ?1")
            .setParameter(1, session.id)
            .executeUpdate()

        assertThat(count("refresh_tokens")).isEqualTo(0)
    }

    @Test
    fun `claim updates one row the first time and none the second`() {
        val token = savedToken(savedSession())
        flushAndClear()

        assertThat(refreshTokens.markRotated(token.id, now.plusSeconds(5))).isTrue()
        assertThat(refreshTokens.markRotated(token.id, now.plusSeconds(9))).isFalse()

        assertThat(refreshTokens.findByTokenHash(token.tokenHash)).isEqualTo(token.copy(rotatedAt = now.plusSeconds(5)))
    }

    @Test
    fun `claim touches only its own token`() {
        val session = savedSession()
        val first = savedToken(session, "t1")
        val second = savedToken(session, "t2")
        flushAndClear()

        refreshTokens.markRotated(first.id, now)

        assertThat(refreshTokens.findByTokenHash(second.tokenHash)).isEqualTo(second)
        assertThat(refreshTokens.markRotated(UUID.randomUUID(), now)).isFalse()
    }

    @Test
    fun `claim sees a token saved in the same transaction`() {
        val token = savedToken(savedSession())

        assertThat(refreshTokens.markRotated(token.id, now)).isTrue()
    }

    @Test
    fun `revoke sets the time once and keeps it`() {
        val session = savedSession()
        flushAndClear()

        assertThat(sessions.revoke(session.id, now.plusSeconds(5))).isTrue()
        assertThat(sessions.revoke(session.id, now.plusSeconds(9))).isFalse()

        assertThat(sessions.findById(session.id)).isEqualTo(session.copy(revokedAt = now.plusSeconds(5)))
        assertThat(sessions.revoke(UUID.randomUUID(), now)).isFalse()
    }

    @Test
    fun `revoke leaves other sessions of the user active`() {
        val user = savedUser()
        val first = savedSession(user)
        val second = savedSession(user)
        flushAndClear()

        sessions.revoke(first.id, now)

        assertThat(sessions.findById(second.id)).isEqualTo(second)
    }

    @Test
    fun `duplicate email on save is EmailAlreadyUsedException without an existence check`() {
        savedUser("ann@example.com")

        assertFailure { savedUser("ann@example.com") }
            .isInstanceOf<EmailAlreadyUsedException>()
    }
}
