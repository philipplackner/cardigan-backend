package com.plcoding.cardigan.board.domain

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isNotEmpty
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readLines

class DomainLayerTest {
    private val domain = Path.of("src/main/kotlin/com/plcoding/cardigan/board/domain")

    @Test
    fun `domain imports no Spring and no JPA`() {
        val sources = Files.walk(domain).use { paths -> paths.filter { it.extension == "kt" }.toList() }
        val forbidden =
            sources.flatMap { file ->
                file
                    .readLines()
                    .filter {
                        it.startsWith("import org.springframework.") ||
                            it.startsWith("import jakarta.persistence.")
                    }.map { "${file.fileName}: $it" }
            }

        assertThat(sources).isNotEmpty()
        assertThat(forbidden).isEmpty()
    }
}
