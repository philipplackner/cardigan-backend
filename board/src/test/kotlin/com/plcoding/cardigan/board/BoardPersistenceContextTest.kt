package com.plcoding.cardigan.board

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.plcoding.cardigan.common.PostgresTestConfig
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@Tag("integration")
@DataJpaTest
@ActiveProfiles("test")
@Import(PostgresTestConfig::class)
class BoardPersistenceContextTest @Autowired constructor(
    private val entityManager: TestEntityManager,
) {

    @Test
    fun `persistence context starts against Postgres`() {
        val version = entityManager.entityManager
            .createNativeQuery("SELECT current_setting('server_version_num')::int / 10000")
            .singleResult as Number

        assertThat(version.toInt()).isEqualTo(17)
    }
}
