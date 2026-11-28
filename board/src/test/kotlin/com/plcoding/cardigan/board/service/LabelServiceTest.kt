package com.plcoding.cardigan.board.service

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import com.plcoding.cardigan.board.domain.exception.LabelNameAlreadyUsedException
import com.plcoding.cardigan.board.domain.exception.LabelNotFoundException
import com.plcoding.cardigan.board.domain.model.LabelChanges
import com.plcoding.cardigan.board.domain.model.LabelColor
import com.plcoding.cardigan.board.fake.FakeLabelRepository
import com.plcoding.cardigan.common.MutableClock
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class LabelServiceTest {
    private val start = Instant.parse("2026-11-15T09:15:30.123Z")
    private val clock = MutableClock(start)
    private val repository = FakeLabelRepository()
    private val service = LabelService(repository, clock)

    private val owner = UUID.randomUUID()
    private val otherOwner = UUID.randomUUID()

    private fun create(
        name: String,
        color: LabelColor = LabelColor.RED,
        ownerId: UUID = owner,
    ) = service.create(ownerId, name, color)

    private fun oneMinuteLater(): Instant {
        clock.advanceBy(Duration.ofMinutes(1))
        return clock.instant()
    }

    @Test
    fun `created label gets the owner, the values and one creation time`() {
        val label = create("Bug", LabelColor.TEAL)

        assertThat(label.ownerId).isEqualTo(owner)
        assertThat(label.name).isEqualTo("Bug")
        assertThat(label.color).isEqualTo(LabelColor.TEAL)
        assertThat(label.createdAt).isEqualTo(start)
        assertThat(label.updatedAt).isEqualTo(start)
        assertThat(repository.all).containsExactly(label)
    }

    @Test
    fun `create with a case variant of a used name fails and stores nothing`() {
        val bug = create("Bug")

        assertFailure { create("bug") }.isInstanceOf<LabelNameAlreadyUsedException>()

        assertThat(repository.all).containsExactly(bug)
        assertThat(repository.saveCount).isEqualTo(1)
    }

    @Test
    fun `create clashes by folding beyond lowercase`() {
        create("Straße")

        assertFailure { create("STRASSE") }.isInstanceOf<LabelNameAlreadyUsedException>()
    }

    @Test
    fun `create that passes the name check but loses in the repository fails as a used name`() {
        create("Bug")
        repository.nameCheckSeesNothing = true

        assertFailure { create("bug") }.isInstanceOf<LabelNameAlreadyUsedException>()

        assertThat(repository.all).hasSize(1)
    }

    @Test
    fun `same name for another owner is created`() {
        create("Bug")

        val other = create("Bug", ownerId = otherOwner)

        assertThat(service.list(otherOwner)).containsExactly(other)
    }

    @Test
    fun `rename to another label's folded name fails and changes nothing`() {
        create("Bug")
        val docs = create("Docs")
        oneMinuteLater()

        assertFailure { service.update(owner, docs.id, LabelChanges(name = "BUG", color = LabelColor.BLUE)) }
            .isInstanceOf<LabelNameAlreadyUsedException>()

        assertThat(service.get(owner, docs.id)).isEqualTo(docs)
    }

    @Test
    fun `rename to a case variant of the own name is a change`() {
        val bug = create("bug")
        val later = oneMinuteLater()

        val renamed = service.update(owner, bug.id, LabelChanges(name = "Bug"))

        assertThat(renamed).isEqualTo(bug.copy(name = "Bug", updatedAt = later))
        assertThat(service.get(owner, bug.id)).isEqualTo(renamed)
    }

    @Test
    fun `same color again changes nothing and saves nothing`() {
        val bug = create("Bug", LabelColor.RED)
        oneMinuteLater()

        val result = service.update(owner, bug.id, LabelChanges(color = LabelColor.RED))

        assertThat(result).isEqualTo(bug)
        assertThat(repository.saveCount).isEqualTo(1)
    }

    @Test
    fun `same name again changes nothing and saves nothing`() {
        val bug = create("Bug")
        oneMinuteLater()

        val result = service.update(owner, bug.id, LabelChanges(name = "Bug"))

        assertThat(result).isEqualTo(bug)
        assertThat(repository.saveCount).isEqualTo(1)
    }

    @Test
    fun `same name and color again change nothing`() {
        val bug = create("Bug", LabelColor.RED)
        oneMinuteLater()

        val result = service.update(owner, bug.id, LabelChanges(name = "Bug", color = LabelColor.RED))

        assertThat(result).isEqualTo(bug)
        assertThat(repository.saveCount).isEqualTo(1)
    }

    @Test
    fun `one differing field of two moves the updated time`() {
        val bug = create("Bug", LabelColor.RED)
        val later = oneMinuteLater()

        val result = service.update(owner, bug.id, LabelChanges(name = "Bug", color = LabelColor.BLUE))

        assertThat(result).isEqualTo(bug.copy(color = LabelColor.BLUE, updatedAt = later))
    }

    @Test
    fun `absent field keeps its value`() {
        val bug = create("Bug", LabelColor.RED)
        oneMinuteLater()

        val renamed = service.update(owner, bug.id, LabelChanges(name = "Defect"))

        assertThat(renamed.name).isEqualTo("Defect")
        assertThat(renamed.color).isEqualTo(LabelColor.RED)
    }

    @Test
    fun `empty changes return the stored label and save nothing`() {
        val bug = create("Bug")
        oneMinuteLater()

        val result = service.update(owner, bug.id, LabelChanges())

        assertThat(result).isEqualTo(bug)
        assertThat(repository.saveCount).isEqualTo(1)
    }

    @Test
    fun `creation time survives every update`() {
        val bug = create("Bug", LabelColor.RED)
        oneMinuteLater()
        service.update(owner, bug.id, LabelChanges(name = "Defect"))
        val later = oneMinuteLater()

        val result = service.update(owner, bug.id, LabelChanges(color = LabelColor.ROSE))

        assertThat(result.createdAt).isEqualTo(start)
        assertThat(result.updatedAt).isEqualTo(later)
        assertThat(result.id).isEqualTo(bug.id)
        assertThat(result.ownerId).isEqualTo(owner)
    }

    @Test
    fun `get, update and delete of another owner's label are not found and change nothing`() {
        val bug = create("Bug")

        assertFailure { service.get(otherOwner, bug.id) }.isInstanceOf<LabelNotFoundException>()
        assertFailure { service.update(otherOwner, bug.id, LabelChanges(color = LabelColor.BLUE)) }
            .isInstanceOf<LabelNotFoundException>()
        assertFailure { service.delete(otherOwner, bug.id) }.isInstanceOf<LabelNotFoundException>()

        assertThat(repository.all).containsExactly(bug)
    }

    @Test
    fun `get, update and delete of an unknown id are not found`() {
        val unknown = UUID.randomUUID()

        assertFailure { service.get(owner, unknown) }.isInstanceOf<LabelNotFoundException>()
        assertFailure { service.update(owner, unknown, LabelChanges(name = "Bug")) }
            .isInstanceOf<LabelNotFoundException>()
        assertFailure { service.delete(owner, unknown) }.isInstanceOf<LabelNotFoundException>()

        assertThat(repository.all).isEmpty()
    }

    @Test
    fun `update of a label deleted after it was found is not found and does not recreate it`() {
        val bug = create("Bug")
        service.get(owner, bug.id)
        service.delete(owner, bug.id)

        assertFailure { service.update(owner, bug.id, LabelChanges(name = "Defect")) }
            .isInstanceOf<LabelNotFoundException>()

        assertThat(repository.all).isEmpty()
    }

    @Test
    fun `delete removes only the named label`() {
        val bug = create("Bug")
        val docs = create("Docs")
        val foreign = create("Bug", ownerId = otherOwner)

        service.delete(owner, bug.id)

        assertThat(repository.all).containsExactly(docs, foreign)
        assertFailure { service.delete(owner, bug.id) }.isInstanceOf<LabelNotFoundException>()
    }

    @Test
    fun `name is free again after delete`() {
        val bug = create("Bug")
        service.delete(owner, bug.id)

        val again = create("bug")

        assertThat(again.id).isNotEqualTo(bug.id)
        assertThat(service.list(owner)).containsExactly(again)
    }

    @Test
    fun `name is free again after a rename`() {
        val bug = create("Bug")
        service.update(owner, bug.id, LabelChanges(name = "Defect"))

        assertThat(create("BUG").name).isEqualTo("BUG")
    }

    @Test
    fun `list is ordered by the folded name, not by the name as entered`() {
        listOf("frontend", "Bug", "api").forEach { create(it) }

        assertThat(service.list(owner).map { it.name }).containsExactly("api", "Bug", "frontend")
    }

    @Test
    fun `list orders non-ASCII and supplementary characters by code point`() {
        listOf("Zebra", "Äpfel", "apple", "😀", "ｚ").forEach { create(it) }

        assertThat(service.list(owner).map { it.name }).containsExactly("apple", "Zebra", "Äpfel", "ｚ", "😀")
    }

    @Test
    fun `list holds only the owner's labels and is empty without any`() {
        create("Bug", ownerId = otherOwner)

        assertThat(service.list(owner)).isEmpty()
    }
}
