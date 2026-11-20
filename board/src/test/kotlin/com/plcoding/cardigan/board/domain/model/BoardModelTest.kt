package com.plcoding.cardigan.board.domain.model

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.plcoding.cardigan.board.domain.exception.BoardKeyAlreadyUsedException
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class BoardModelTest {

    private val now = Instant.parse("2026-11-15T09:15:30.123Z")

    private fun board(key: String) = Board(UUID.randomUUID(), UUID.randomUUID(), "Roadmap", key, now, now)

    private fun column(position: Int) = Column(UUID.randomUUID(), UUID.randomUUID(), "To do", position, now, now)

    @Test
    fun `uppercase key of valid length is accepted`() {
        assertThat(board("CRD").key).isEqualTo("CRD")
        assertThat(board("AB").key).isEqualTo("AB")
        assertThat(board("ABCDE").key).isEqualTo("ABCDE")
    }

    @Test
    fun `lowercase key is rejected`() {
        assertFailure { board("crd") }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `key with one lowercase letter is rejected`() {
        assertFailure { board("CRd") }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `key that is not normalized is rejected`() {
        assertFailure { board(" CRD") }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `key outside the length limits is rejected`() {
        assertFailure { board("C") }.isInstanceOf<IllegalArgumentException>()
        assertFailure { board("ABCDEF") }.isInstanceOf<IllegalArgumentException>()
        assertFailure { board("") }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `key with a digit is rejected`() {
        assertFailure { board("CR1") }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `position 0 is accepted`() {
        assertThat(column(0).position).isEqualTo(0)
    }

    @Test
    fun `negative position is rejected`() {
        assertFailure { column(-1) }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `key conflict names the field key`() {
        assertThat(BoardKeyAlreadyUsedException("CRD").field).isEqualTo("key")
    }
}
