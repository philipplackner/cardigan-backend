package com.plcoding.cardigan.board.infra.database.repository

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.plcoding.cardigan.board.domain.exception.LabelNameAlreadyUsedException
import com.plcoding.cardigan.board.domain.model.Label
import com.plcoding.cardigan.board.domain.model.LabelColor
import com.plcoding.cardigan.common.MutableClock
import com.plcoding.cardigan.common.PostgresTestConfig
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
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

@Tag("integration")
@DataJpaTest
@ActiveProfiles("test")
@Import(PostgresTestConfig::class, JpaLabelRepository::class)
class JpaLabelRepositoryTest @Autowired constructor(
    private val repository: JpaLabelRepository,
    private val entityManager: TestEntityManager,
) {

    private val clock = MutableClock(Instant.parse("2026-11-15T09:15:30.123456Z"))
    private val owner = UUID.randomUUID()
    private val otherOwner = UUID.randomUUID()

    private fun label(name: String = "Bug", ownerId: UUID = owner, color: LabelColor = LabelColor.RED): Label {
        val now = clock.instant()
        return Label(UUID.randomUUID(), ownerId, name, color, now, now)
    }

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    private fun nativeValue(column: String, id: UUID): Any =
        entityManager.entityManager
            .createNativeQuery("SELECT $column FROM board_service.labels WHERE id = ?1")
            .setParameter(1, id)
            .singleResult

    @Test
    fun `saved label reads back equal`() {
        val label = repository.save(label(name = "Straße", color = LabelColor.FUCHSIA))
        flushAndClear()

        assertThat(repository.findByIdAndOwnerId(label.id, owner)).isEqualTo(label)
    }

    @Test
    fun `timestamps are stored at millisecond precision`() {
        val label = repository.save(label())
        flushAndClear()

        val storedInstant = when (val stored = nativeValue("created_at", label.id)) {
            is Instant -> stored
            is Timestamp -> stored.toInstant()
            is OffsetDateTime -> stored.toInstant()
            else -> error("Unexpected type ${stored::class}")
        }
        assertThat(storedInstant).isEqualTo(Instant.parse("2026-11-15T09:15:30.123Z"))
    }

    @Test
    fun `name key and color are stored next to the name as entered`() {
        val label = repository.save(label(name = "Straße", color = LabelColor.FUCHSIA))
        flushAndClear()

        assertThat(nativeValue("name", label.id)).isEqualTo("Straße")
        assertThat(nativeValue("name_key", label.id)).isEqualTo("strasse")
        assertThat(nativeValue("color", label.id)).isEqualTo("FUCHSIA")
    }

    @Test
    fun `longest name whose key triples in length fits`() {
        val label = repository.save(label(name = "ﬃ".repeat(30)))
        flushAndClear()

        assertThat(repository.findByIdAndOwnerId(label.id, owner)).isEqualTo(label)
        assertThat(nativeValue("name_key", label.id)).isEqualTo("ffi".repeat(30))
    }

    @Test
    fun `saving a stored label again updates it`() {
        val label = repository.save(label())
        flushAndClear()
        clock.advanceBy(Duration.ofMinutes(1))

        val changed = repository.save(label.copy(name = "Defect", color = LabelColor.ROSE, updatedAt = clock.instant()))
        flushAndClear()

        assertThat(repository.findAllByOwnerId(owner)).containsExactlyInAnyOrder(changed)
        assertThat(nativeValue("name_key", label.id)).isEqualTo("defect")
    }

    @Test
    fun `label of another owner is not found`() {
        val label = repository.save(label())
        flushAndClear()

        assertThat(repository.findByIdAndOwnerId(label.id, otherOwner)).isNull()
        assertThat(repository.findByIdAndOwnerIdForUpdate(label.id, otherOwner)).isNull()
        assertThat(repository.findByIdAndOwnerId(UUID.randomUUID(), owner)).isNull()
    }

    @Test
    fun `locking read returns the owner's label`() {
        val label = repository.save(label())
        flushAndClear()

        assertThat(repository.findByIdAndOwnerIdForUpdate(label.id, owner)).isEqualTo(label)
    }

    @Test
    fun `list holds only the owner's labels`() {
        val bug = repository.save(label("Bug"))
        val docs = repository.save(label("Docs"))
        repository.save(label("Bug", ownerId = otherOwner))
        flushAndClear()

        assertThat(repository.findAllByOwnerId(owner)).containsExactlyInAnyOrder(bug, docs)
        assertThat(repository.findAllByOwnerId(UUID.randomUUID())).isEmpty()
    }

    @Test
    fun `name key exists only for its owner`() {
        repository.save(label("Bug"))
        flushAndClear()

        assertThat(repository.existsByOwnerAndNameKey(owner, "bug")).isTrue()
        assertThat(repository.existsByOwnerAndNameKey(owner, "Bug")).isFalse()
        assertThat(repository.existsByOwnerAndNameKey(otherOwner, "bug")).isFalse()
    }

    @Test
    fun `delete with another owner removes nothing`() {
        val label = repository.save(label())
        flushAndClear()

        assertThat(repository.deleteByIdAndOwnerId(label.id, otherOwner)).isEqualTo(0)

        assertThat(repository.findByIdAndOwnerId(label.id, owner)).isEqualTo(label)
    }

    @Test
    fun `delete removes only the named label of the owner`() {
        val label = repository.save(label("Bug"))
        val kept = repository.save(label("Docs"))
        val foreign = repository.save(label("Bug", ownerId = otherOwner))
        flushAndClear()

        assertThat(repository.deleteByIdAndOwnerId(label.id, owner)).isEqualTo(1)

        assertThat(repository.findAllByOwnerId(owner)).containsExactlyInAnyOrder(kept)
        assertThat(repository.findAllByOwnerId(otherOwner)).containsExactlyInAnyOrder(foreign)
        assertThat(repository.deleteByIdAndOwnerId(label.id, owner)).isEqualTo(0)
    }

    @Test
    fun `database rejects a second row with the same owner and name key`() {
        repository.save(label("Bug"))
        flushAndClear()
        val insert = entityManager.entityManager.createNativeQuery(
            """
            INSERT INTO board_service.labels (id, owner_id, name, name_key, color, created_at, updated_at)
            VALUES (?1, ?2, 'BUG', 'bug', 'BLUE', now(), now())
            """,
        ).setParameter(1, UUID.randomUUID()).setParameter(2, owner)

        assertFailure { insert.executeUpdate() }.isInstanceOf<ConstraintViolationException>()
    }

    @Test
    fun `database accepts the same name key for another owner`() {
        repository.save(label("Bug"))
        flushAndClear()
        val insert = entityManager.entityManager.createNativeQuery(
            """
            INSERT INTO board_service.labels (id, owner_id, name, name_key, color, created_at, updated_at)
            VALUES (?1, ?2, 'BUG', 'bug', 'BLUE', now(), now())
            """,
        ).setParameter(1, UUID.randomUUID()).setParameter(2, otherOwner)

        assertThat(insert.executeUpdate()).isEqualTo(1)
    }

    @Test
    fun `case variant for one owner fails as a used name`() {
        repository.save(label("Bug"))

        assertFailure { repository.save(label("bug")) }.isInstanceOf<LabelNameAlreadyUsedException>()
    }

    @Test
    fun `names that fold to one key beyond lowercase fail as a used name`() {
        repository.save(label("Straße"))

        assertFailure { repository.save(label("STRASSE")) }.isInstanceOf<LabelNameAlreadyUsedException>()
    }

    @Test
    fun `case variants for two owners are both stored`() {
        val first = repository.save(label("Bug"))
        val second = repository.save(label("bug", ownerId = otherOwner))
        flushAndClear()

        assertThat(repository.findAllByOwnerId(owner)).containsExactlyInAnyOrder(first)
        assertThat(repository.findAllByOwnerId(otherOwner)).containsExactlyInAnyOrder(second)
    }

    @Test
    fun `another integrity violation is not reported as a used name`() {
        assertFailure { repository.save(label("a".repeat(31))) }
            .isInstanceOf<DataIntegrityViolationException>()
            .isNotInstanceOf<LabelNameAlreadyUsedException>()
    }
}
