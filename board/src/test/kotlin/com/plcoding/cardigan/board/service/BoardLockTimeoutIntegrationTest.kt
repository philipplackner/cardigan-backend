package com.plcoding.cardigan.board.service

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.PessimisticLockingFailureException

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["cardigan.board.lock-timeout=300ms"],
)
class BoardLockTimeoutIntegrationTest : BoardIntegrationTest() {

    @Test
    fun `column create fails once the lock timeout has passed and stores nothing`() {
        val board = boardService.create(owner, "Roadmap", "CRD")
        val lock = holdBoardLock(board.board.id)

        try {
            assertFailure { columnService.create(owner, board.board.id, "Review") }
                .isInstanceOf<PessimisticLockingFailureException>()
        } finally {
            lock.release()
        }

        assertThat(storedColumns()).isEqualTo(3)
        assertThat(boardService.get(owner, board.board.id)).isEqualTo(board)
    }

    @Test
    fun `write within the lock timeout succeeds`() {
        val board = boardService.create(owner, "Roadmap", "CRD")

        assertThat(columnService.create(owner, board.board.id, "Review").position).isEqualTo(3)
    }
}
