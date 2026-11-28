package com.plcoding.cardigan.board

import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import com.plcoding.cardigan.common.DatabaseCleaner
import com.plcoding.cardigan.common.MutableClock
import com.plcoding.cardigan.common.PostgresTestConfig
import com.plcoding.cardigan.common.api.validation.RequestValidator
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Clock

@Tag("integration")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["cardigan.board.lock-timeout=5s"],
)
@ActiveProfiles("test")
@Import(PostgresTestConfig::class, BoardTestConfig::class)
class BoardModuleContextTest
    @Autowired
    constructor(
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
