package com.plcoding.cardigan.board

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import com.plcoding.cardigan.common.DatabaseCleaner
import com.plcoding.cardigan.common.MutableClock
import com.plcoding.cardigan.common.PostgresTestConfig
import com.plcoding.cardigan.common.api.validation.RequestValidator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Clock

@DataJpaTest
@ActiveProfiles("test")
@Import(PostgresTestConfig::class)
class BoardPersistenceContextTest @Autowired constructor(
    private val entityManager: TestEntityManager,
) {

    @Test
    fun `persistence context starts against Postgres`() {
        val one = entityManager.entityManager.createNativeQuery("SELECT 1").singleResult

        assertThat((one as Number).toInt()).isEqualTo(1)
    }
}

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["cardigan.board.lock-timeout=5s"],
)
@ActiveProfiles("test")
@Import(PostgresTestConfig::class, BoardTestConfig::class)
class BoardModuleContextTest @Autowired constructor(
    private val clock: Clock,
    private val databaseCleaner: DatabaseCleaner,
    private val requestValidator: RequestValidator,
) {

    @Test
    fun `module context starts with the test clock, cleaner and validator`() {
        databaseCleaner.clean("board_service")

        assertThat(clock).isInstanceOf<MutableClock>()
        assertThat(requestValidator).isNotNull()
    }
}
