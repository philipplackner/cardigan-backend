package com.plcoding.cardigan.board.service

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isIn
import assertk.assertions.isInstanceOf
import com.plcoding.cardigan.board.BoardTestApplication
import com.plcoding.cardigan.board.domain.exception.LabelNameAlreadyUsedException
import com.plcoding.cardigan.board.domain.exception.LabelNotFoundException
import com.plcoding.cardigan.board.domain.model.Label
import com.plcoding.cardigan.board.domain.model.LabelChanges
import com.plcoding.cardigan.board.domain.model.LabelColor
import com.plcoding.cardigan.board.domain.repository.LabelRepository
import com.plcoding.cardigan.board.infra.database.repository.JpaLabelRepository
import com.plcoding.cardigan.common.DatabaseCleaner
import com.plcoding.cardigan.common.MutableClock
import com.plcoding.cardigan.common.PostgresTestConfig
import com.plcoding.cardigan.common.api.validation.RequestValidator
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Holds every caller after a name check that found nothing until [parties] callers have passed it,
 * so the loser of a race is stopped by the database constraint and not by the check.
 */
class RacingLabelRepository(
    private val delegate: LabelRepository,
) : LabelRepository by delegate {

    @Volatile
    var barrier: CyclicBarrier? = null

    override fun existsByOwnerAndNameKey(ownerId: UUID, nameKey: String): Boolean {
        val exists = delegate.existsByOwnerAndNameKey(ownerId, nameKey)
        if (!exists) barrier?.await(10, TimeUnit.SECONDS)
        return exists
    }
}

@Tag("integration")
@SpringBootTest(
    classes = [BoardTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["cardigan.board.lock-timeout=5s"],
)
@ActiveProfiles("test")
@Import(PostgresTestConfig::class, LabelServiceIntegrationTest.Config::class)
class LabelServiceIntegrationTest @Autowired constructor(
    private val labelService: LabelService,
    private val racingRepository: RacingLabelRepository,
    private val databaseCleaner: DatabaseCleaner,
    private val jdbcTemplate: JdbcTemplate,
) {

    @TestConfiguration(proxyBeanMethods = false)
    @Import(RequestValidator::class)
    class Config {

        @Bean
        fun clock(): MutableClock = MutableClock()

        @Bean
        fun databaseCleaner(jdbcTemplate: JdbcTemplate) = DatabaseCleaner(jdbcTemplate)

        @Bean
        @Primary
        fun racingLabelRepository(jpaLabelRepository: JpaLabelRepository) = RacingLabelRepository(jpaLabelRepository)
    }

    private val owner = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        databaseCleaner.clean("board_service")
    }

    @AfterEach
    fun tearDown() {
        racingRepository.barrier = null
    }

    private fun rowCount(): Int =
        jdbcTemplate.queryForObject("SELECT count(*) FROM board_service.labels", Int::class.java)!!

    /** Runs both actions at once, each held after its name check until the other has passed its own. */
    private fun race(first: () -> Label, second: () -> Label): List<Result<Label>> {
        racingRepository.barrier = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        try {
            return executor
                .invokeAll(listOf(Callable { runCatching(first) }, Callable { runCatching(second) }), 30, TimeUnit.SECONDS)
                .map { it.get() }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun assertOneWinnerOneUsedName(results: List<Result<Label>>): Label {
        val winners = results.mapNotNull { it.getOrNull() }
        val failures = results.mapNotNull { it.exceptionOrNull() }
        assertThat(winners).hasSize(1)
        assertThat(failures).hasSize(1)
        assertThat(failures.single()).isInstanceOf<LabelNameAlreadyUsedException>()
        return winners.single()
    }

    @Test
    fun `two concurrent creates of one folded name store one label`() {
        val results = race(
            { labelService.create(owner, "Bug", LabelColor.RED) },
            { labelService.create(owner, "bug", LabelColor.BLUE) },
        )

        val winner = assertOneWinnerOneUsedName(results)
        assertThat(racingRepository.barrier!!.isBroken).isEqualTo(false)
        assertThat(labelService.list(owner)).containsExactlyInAnyOrder(winner)
        assertThat(rowCount()).isEqualTo(1)
    }

    @Test
    fun `two concurrent renames of different labels to one folded name rename one`() {
        val docs = labelService.create(owner, "Docs", LabelColor.RED)
        val api = labelService.create(owner, "Api", LabelColor.BLUE)

        val results = race(
            { labelService.update(owner, docs.id, LabelChanges(name = "Bug")) },
            { labelService.update(owner, api.id, LabelChanges(name = "bug")) },
        )

        val winner = assertOneWinnerOneUsedName(results)
        assertThat(racingRepository.barrier!!.isBroken).isEqualTo(false)
        assertThat(winner.name).isIn("Bug", "bug")
        val loser = if (winner.id == docs.id) api else docs
        assertThat(labelService.list(owner)).containsExactlyInAnyOrder(winner, loser)
    }

    @Test
    fun `sequential clash is found by the name check`() {
        labelService.create(owner, "Bug", LabelColor.RED)

        assertFailure { labelService.create(owner, "bug", LabelColor.BLUE) }
            .isInstanceOf<LabelNameAlreadyUsedException>()

        assertThat(rowCount()).isEqualTo(1)
    }

    @Test
    fun `update after the label was deleted is not found and stores no row`() {
        val label = labelService.create(owner, "Bug", LabelColor.RED)
        labelService.get(owner, label.id)
        labelService.delete(owner, label.id)

        assertFailure { labelService.update(owner, label.id, LabelChanges(name = "Defect")) }
            .isInstanceOf<LabelNotFoundException>()

        assertThat(rowCount()).isEqualTo(0)
    }

    @Test
    fun `update with the stored values returns the stored label`() {
        val label = labelService.create(owner, "Bug", LabelColor.RED)

        val unchanged = labelService.update(owner, label.id, LabelChanges(name = "Bug", color = LabelColor.RED))

        assertThat(unchanged).isEqualTo(label)
        assertThat(labelService.get(owner, label.id)).isEqualTo(label)
    }
}
