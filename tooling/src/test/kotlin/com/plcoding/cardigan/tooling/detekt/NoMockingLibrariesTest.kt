package com.plcoding.cardigan.tooling.detekt

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.single
import dev.detekt.api.Finding
import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import dev.detekt.test.location
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class NoMockingLibrariesTest {

    private val rule = NoMockingLibraries(TestConfig("active" to true))

    // Mockito and MockK are not on the classpath, so snippets are only parsed, never compiled.
    private fun lint(code: String): List<Finding> = rule.lint(code, compile = false)

    // tests.md: be-detekt-konsist C01
    @ParameterizedTest
    @ValueSource(strings = ["org.mockito", "io.mockk"])
    fun `an import of the bare package name is reported`(path: String) {
        val findings = lint("import $path\n")

        assertThat(findings).hasSize(1)
    }

    // tests.md: be-detekt-konsist C02
    @ParameterizedTest
    @ValueSource(
        strings = [
            "org.mockito.kotlin.mock",
            "org.mockito.junit.jupiter.MockitoExtension",
            "io.mockk.junit5.MockKExtension",
            "org.mockito.kotlin.*",
        ],
    )
    fun `imports from deep subpackages are reported`(path: String) {
        val findings = lint("import $path\n")

        assertThat(findings).hasSize(1)
    }

    // tests.md: be-detekt-konsist C03
    @ParameterizedTest
    @ValueSource(
        strings = [
            "com.example.org.mockito.Mockito",
            "com.io.mockk.mockk",
            "fake.io.mockk",
        ],
    )
    fun `a mocking package name in the middle or at the end of a path is not reported`(path: String) {
        val findings = lint("import $path\n")

        assertThat(findings).isEmpty()
    }

    // tests.md: be-detekt-konsist C04
    @Test
    fun `the alias name is ignored in both directions`() {
        val findings = lint(
            """
            import com.example.FakeRepository as mockk
            import com.example.Clock as Mockito
            """.trimIndent(),
        )

        assertThat(findings).isEmpty()
    }

    // tests.md: be-detekt-konsist C05
    @ParameterizedTest
    @ValueSource(
        strings = [
            "import `org`.mockito.Mockito",
            "import io.`mockk`.mockk",
            "import org.mockito.Mockito;",
            "import io.mockk.mockk // test helper",
        ],
    )
    fun `the import's text form does not matter, only its FQ name`(importLine: String) {
        val findings = lint("$importLine\n")

        assertThat(findings).hasSize(1)
    }

    // tests.md: be-detekt-konsist C06
    @Test
    fun `mocking names in comments, strings and the package declaration are not reported`() {
        val findings = lint(
            """
            package org.mockito.sample

            // import io.mockk.mockk

            /**
             * Uses no mocks, so no import org.mockito.Mockito here.
             */
            val s = "import io.mockk.every"
            """.trimIndent(),
        )

        assertThat(findings).isEmpty()
    }

    // tests.md: be-detekt-konsist C07
    @Test
    fun `every offending import in a file is reported at its own line and clean imports are not`() {
        val findings = lint(
            """
            package sample

            import kotlin.test.Test
            import org.mockito.Mockito
            import java.util.UUID
            import io.mockk.every
            """.trimIndent(),
        )

        assertThat(findings.map { it.location.source.line }).containsExactly(4, 6)
    }

    // tests.md: be-detekt-konsist C08
    @Test
    fun `the same offending import twice gives two findings`() {
        val findings = lint(
            """
            import io.mockk.mockk
            import io.mockk.mockk
            """.trimIndent(),
        )

        assertThat(findings.map { it.location.source.line }).containsExactly(1, 2)
    }

    // tests.md: be-detekt-konsist C09
    @Test
    fun `an aliased import's message names the imported path, not the alias`() {
        val findings = lint("import io.mockk.mockk as m\n")

        assertThat(findings).single().transform { it.message }
            .isEqualTo("Import of 'io.mockk.mockk' is not allowed. Use fakes instead of mocking libraries.")
    }

    // tests.md: be-detekt-konsist C10
    @Test
    fun `a file with only clean imports has no finding`() {
        val findings = lint(
            """
            import kotlin.test.Test
            import assertk.assertThat
            import com.plcoding.cardigan.common.fake.MutableClock
            import org.junit.jupiter.api.Test
            """.trimIndent(),
        )

        assertThat(findings).isEmpty()
    }

    // tests.md: be-detekt-konsist C12
    @Test
    fun `the rule's name is NoMockingLibraries`() {
        assertThat(NoMockingLibraries(TestConfig()).ruleName.value).isEqualTo("NoMockingLibraries")
    }
}
