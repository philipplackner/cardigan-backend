package com.plcoding.cardigan.user.infra.database.repository

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.plcoding.cardigan.common.MutableClock
import com.plcoding.cardigan.common.PostgresTestConfig
import com.plcoding.cardigan.user.domain.exception.EmailAlreadyUsedException
import com.plcoding.cardigan.user.domain.model.User
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles
import java.sql.Timestamp
import java.time.Instant
import java.util.Locale
import java.util.UUID

@Tag("integration")
@DataJpaTest
@ActiveProfiles("test")
@Import(PostgresTestConfig::class, JpaUserRepository::class)
class JpaUserRepositoryTest @Autowired constructor(
    private val repository: JpaUserRepository,
    private val entityManager: TestEntityManager,
) {

    private val clock = MutableClock(Instant.parse("2026-11-15T09:15:30.123456Z"))

    private fun user(email: String = "ann@example.com"): User {
        val now = clock.instant()
        return User(UUID.randomUUID(), email, "hash", now, now)
    }

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    @Test
    fun `saved user reads back equal`() {
        val user = repository.save(user())
        flushAndClear()

        assertThat(repository.findById(user.id)).isEqualTo(user)
    }

    @Test
    fun `unknown id reads back null`() {
        assertThat(repository.findById(UUID.randomUUID())).isNull()
    }

    @Test
    fun `existsByEmail matches the stored email only`() {
        repository.save(user("ann@example.com"))
        flushAndClear()

        assertThat(repository.existsByEmail("ann@example.com")).isTrue()
        assertThat(repository.existsByEmail("bob@example.com")).isFalse()
    }

    @Test
    fun `exact duplicate email is rejected`() {
        repository.save(user("ann@example.com"))
        flushAndClear()

        assertFailure { repository.save(user("ann@example.com")) }.isInstanceOf<EmailAlreadyUsedException>()
    }

    @Test
    fun `mixed-case input lowercased before storing is rejected as a duplicate`() {
        repository.save(user("ann@example.com"))
        flushAndClear()

        assertFailure { repository.save(user("Ann@EXAMPLE.com".lowercase(Locale.ROOT))) }
            .isInstanceOf<EmailAlreadyUsedException>()
    }

    @Test
    fun `duplicate email is reported with its field`() {
        repository.save(user("ann@example.com"))

        assertFailure { repository.save(user("ann@example.com")) }
            .isInstanceOf<EmailAlreadyUsedException>()
            .transform { it.field }
            .isEqualTo("email")
    }

    @Test
    fun `duplicate id with another email is not an email conflict`() {
        val first = repository.save(user("ann@example.com"))
        flushAndClear()

        val updated = repository.save(first.copy(email = "bob@example.com"))
        flushAndClear()

        assertThat(repository.findById(first.id)).isEqualTo(updated)
    }

    @Test
    fun `another broken constraint is not reported as a used email`() {
        val tooLong = user("a".repeat(250) + "@example.com")

        assertFailure { repository.save(tooLong) }
            .isInstanceOf<DataIntegrityViolationException>()
    }

    @Test
    fun `findByEmail matches the stored email only`() {
        val ann = repository.save(user("ann@example.com"))
        flushAndClear()

        assertThat(repository.findByEmail("ann@example.com")).isEqualTo(ann)
        assertThat(repository.findByEmail("Ann@example.com")).isNull()
        assertThat(repository.findByEmail("bob@example.com")).isNull()
        assertThat(repository.findByEmail("")).isNull()
    }

    @Test
    fun `email unique constraint has its agreed name`() {
        val names = entityManager.entityManager.createNativeQuery(
            """
            SELECT conname FROM pg_constraint
            WHERE conrelid = 'user_service.users'::regclass AND contype = 'u'
            """,
        ).resultList

        assertThat(names).isEqualTo(listOf<Any>("users_email_unique"))
    }

    @Test
    fun `database rejects an uppercase email inserted directly`() {
        val insert = entityManager.entityManager.createNativeQuery(
            """
            INSERT INTO user_service.users (id, email, password_hash, created_at, updated_at)
            VALUES (?1, 'Ann@Example.com', 'hash', now(), now())
            """,
        ).setParameter(1, UUID.randomUUID())

        assertFailure { insert.executeUpdate() }.isInstanceOf<ConstraintViolationException>()
    }

    @Test
    fun `created_at is stored at millisecond precision`() {
        val user = repository.save(user())
        flushAndClear()

        val stored = entityManager.entityManager
            .createNativeQuery("SELECT created_at FROM user_service.users WHERE id = ?1")
            .setParameter(1, user.id)
            .singleResult

        val storedInstant = when (stored) {
            is Instant -> stored
            is Timestamp -> stored.toInstant()
            is java.time.OffsetDateTime -> stored.toInstant()
            else -> error("Unexpected type ${stored::class}")
        }
        assertThat(storedInstant).isEqualTo(Instant.parse("2026-11-15T09:15:30.123Z"))
    }
}
