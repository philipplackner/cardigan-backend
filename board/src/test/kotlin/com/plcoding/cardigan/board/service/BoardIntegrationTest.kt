package com.plcoding.cardigan.board.service

import com.plcoding.cardigan.board.BoardTestConfig
import com.plcoding.cardigan.board.domain.model.Column
import com.plcoding.cardigan.board.domain.repository.BoardRepository
import com.plcoding.cardigan.board.domain.repository.ColumnRepository
import com.plcoding.cardigan.board.infra.database.repository.JpaColumnRepository
import com.plcoding.cardigan.common.DatabaseCleaner
import com.plcoding.cardigan.common.MutableClock
import com.plcoding.cardigan.common.PostgresTestConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/** The real column adapter, with a switch that makes one `save` call fail. */
class FailingColumnRepository(
    private val delegate: JpaColumnRepository,
) : ColumnRepository by delegate {

    /** The number of the next `save` call that throws, counted from 1. Null never throws. */
    @Volatile
    var failOnSave: Int? = null

    @Volatile
    private var saves = 0

    fun reset() {
        failOnSave = null
        saves = 0
    }

    override fun save(column: Column): Column {
        saves++
        if (saves == failOnSave) throw IllegalStateException("column save failed on purpose")
        return delegate.save(column)
    }
}

@TestConfiguration(proxyBeanMethods = false)
class FailingColumnRepositoryConfig {

    @Bean
    @Primary
    fun failingColumnRepository(delegate: JpaColumnRepository) = FailingColumnRepository(delegate)
}

/**
 * Base of the module integration tests: the real services and adapters against Postgres. Subclasses
 * are `@SpringBootTest`s that set `cardigan.board.lock-timeout`.
 */
@Tag("integration")
@ActiveProfiles("test")
@Import(PostgresTestConfig::class, BoardTestConfig::class, FailingColumnRepositoryConfig::class)
abstract class BoardIntegrationTest {

    @Autowired
    protected lateinit var boardService: BoardService

    @Autowired
    protected lateinit var columnService: ColumnService

    @Autowired
    protected lateinit var boardRepository: BoardRepository

    @Autowired
    protected lateinit var failingColumns: FailingColumnRepository

    @Autowired
    protected lateinit var clock: MutableClock

    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var databaseCleaner: DatabaseCleaner

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    protected val owner: UUID = UUID.randomUUID()

    protected lateinit var executor: ExecutorService

    @BeforeEach
    fun setUpIntegration() {
        databaseCleaner.clean("board_service")
        failingColumns.reset()
        clock.set(START)
        executor = Executors.newFixedThreadPool(12)
    }

    @AfterEach
    fun tearDownIntegration() {
        executor.shutdownNow()
        executor.awaitTermination(10, TimeUnit.SECONDS)
    }

    /** Moves the shared clock forward. Synchronized, so it never runs backwards between threads. */
    protected fun tick(): Instant = synchronized(clock) {
        clock.advanceBy(Duration.ofMillis(1))
        clock.instant()
    }

    /** Runs [tasks] at the same moment on separate threads and returns each outcome, in order. */
    protected fun <T> race(tasks: List<() -> T>): List<Result<T>> {
        val barrier = CyclicBarrier(tasks.size)
        val futures = tasks.map { task ->
            executor.submit(
                Callable {
                    barrier.await(10, TimeUnit.SECONDS)
                    runCatching(task)
                },
            )
        }
        return futures.map { it.get(60, TimeUnit.SECONDS) }
    }

    /**
     * Opens a transaction on another thread that holds the row lock of the board until the returned
     * handle is released.
     */
    protected fun holdBoardLock(boardId: UUID): HeldLock {
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder = executor.submit {
            TransactionTemplate(transactionManager).executeWithoutResult {
                checkNotNull(boardRepository.lockByIdAndOwner(boardId, owner))
                locked.countDown()
                release.await(60, TimeUnit.SECONDS)
            }
        }
        check(locked.await(10, TimeUnit.SECONDS)) { "Board lock was not acquired" }
        return HeldLock(release, holder)
    }

    protected class HeldLock(
        private val release: CountDownLatch,
        private val holder: Future<*>,
    ) {

        fun release() {
            release.countDown()
            holder.get(10, TimeUnit.SECONDS)
        }
    }

    protected fun storedBoards(): Int =
        jdbcTemplate.queryForObject("SELECT count(*) FROM board_service.boards", Int::class.java)!!

    protected fun storedColumns(): Int =
        jdbcTemplate.queryForObject("SELECT count(*) FROM board_service.columns", Int::class.java)!!

    companion object {
        val START: Instant = Instant.parse("2026-11-15T09:15:30.123Z")
    }
}
