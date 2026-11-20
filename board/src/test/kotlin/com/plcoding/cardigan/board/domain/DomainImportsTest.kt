package com.plcoding.cardigan.board.domain

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isNotEmpty
import org.junit.jupiter.api.Test
import java.io.File

class DomainImportsTest {

    private val domain = File("src/main/kotlin/com/plcoding/cardigan/board/domain")

    @Test
    fun `domain imports no Spring and no JPA`() {
        val files = domain.walkTopDown().filter { it.extension == "kt" }.toList()
        val forbidden = files.flatMap { file ->
            file.readLines()
                .filter { it.startsWith("import org.springframework") || it.startsWith("import jakarta.persistence") }
                .map { "${file.name}: $it" }
        }

        assertThat(files).isNotEmpty()
        assertThat(forbidden).isEmpty()
    }
}
