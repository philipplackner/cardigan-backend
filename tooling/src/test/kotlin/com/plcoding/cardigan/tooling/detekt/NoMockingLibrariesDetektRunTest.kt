package com.plcoding.cardigan.tooling.detekt

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.single
import dev.detekt.api.Issue
import dev.detekt.api.RuleSetId
import dev.detekt.tooling.api.DetektProvider
import dev.detekt.tooling.api.IssuesFound
import dev.detekt.tooling.dsl.ProcessingSpecBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Runs detekt's engine in-process with the shared `config/detekt/detekt.yml`. The `cardigan` rule set
 * is never referenced here; the engine loads it through its `META-INF/services` registration.
 */
class NoMockingLibrariesDetektRunTest {

    @TempDir
    lateinit var root: Path

    // tests.md: be-detekt-konsist A01
    @Test
    fun `a Mockito import in a test file is reported by cardigan NoMockingLibraries`() {
        write(
            "sample/src/test/kotlin/SampleTest.kt",
            """
            package sample

            import org.mockito.Mockito

            class SampleTest
            """,
        )

        val findings = runDetekt(root)

        assertThat(findings.map { it.fileName() to it.line() }).containsExactly("SampleTest.kt" to 3)
    }

    // tests.md: be-detekt-konsist A02
    @Test
    fun `a star import and an aliased import from MockK are each reported once`() {
        write(
            "sample/src/test/kotlin/SampleTest.kt",
            """
            package sample

            import io.mockk.*
            import io.mockk.mockk as m

            class SampleTest
            """,
        )

        val findings = runDetekt(root)

        assertThat(findings.map { it.line() }.sorted()).containsExactly(3, 4)
    }

    // tests.md: be-detekt-konsist A03
    @Test
    fun `packages that only share the leading letters are not reported`() {
        write(
            "sample/src/test/kotlin/SampleTest.kt",
            """
            package sample

            import org.mockitox.Foo
            import io.mockkx.Bar

            class SampleTest
            """,
        )

        val findings = runDetekt(root)

        assertThat(findings).isEmpty()
    }

    // tests.md: be-detekt-konsist A04
    @Test
    fun `a MockK import in a main source file is not reported`() {
        write(
            "sample/src/main/kotlin/Sample.kt",
            """
            package sample

            import io.mockk.mockk

            class Sample
            """,
        )
        write(
            "sample/src/test/kotlin/SampleTest.kt",
            """
            package sample

            import io.mockk.mockk

            class SampleTest
            """,
        )

        val findings = runDetekt(root)

        assertThat(findings.map { it.fileName() }).containsExactly("SampleTest.kt")
    }

    // tests.md: be-detekt-konsist A05
    @Test
    fun `a MockK import in a testFixtures source file is reported`() {
        write(
            "sample/src/testFixtures/kotlin/Fixture.kt",
            """
            package sample

            import io.mockk.mockk

            class Fixture
            """,
        )

        val findings = runDetekt(root)

        assertThat(findings.map { it.fileName() }).containsExactly("Fixture.kt")
    }

    // tests.md: be-detekt-konsist A06
    @Test
    fun `a fully qualified MockK call without an import is not reported`() {
        write(
            "sample/src/test/kotlin/SampleTest.kt",
            """
            package sample

            class Foo

            class SampleTest {
                fun create() {
                    val foo = io.mockk.mockk<Foo>()
                }
            }
            """,
        )

        val findings = runDetekt(root)

        assertThat(findings).isEmpty()
    }

    // tests.md: be-detekt-konsist A07
    @Test
    fun `file Suppress with the rule id suppresses the finding`() {
        write(
            "sample/src/test/kotlin/SampleTest.kt",
            """
            @file:Suppress("cardigan:NoMockingLibraries")

            package sample

            import org.mockito.Mockito

            class SampleTest
            """,
        )

        val findings = runDetekt(root)

        assertThat(findings).isEmpty()
    }

    // tests.md: be-detekt-konsist A08
    @Test
    fun `an import that differs only in letter case is not reported`() {
        write(
            "sample/src/test/kotlin/SampleTest.kt",
            """
            package sample

            import Org.Mockito.Mockito

            class SampleTest
            """,
        )

        val findings = runDetekt(root)

        assertThat(findings).isEmpty()
    }

    // tests.md: be-detekt-konsist A09
    @Test
    fun `a plain import's finding has the exact fixed message`() {
        write(
            "sample/src/test/kotlin/SampleTest.kt",
            """
            package sample

            import org.mockito.Mockito

            class SampleTest
            """,
        )

        val findings = runDetekt(root)

        assertThat(findings).single().transform { it.message }
            .isEqualTo("Import of 'org.mockito.Mockito' is not allowed. Use fakes instead of mocking libraries.")
    }

    // tests.md: be-detekt-konsist A10
    @Test
    fun `a star import's finding names the path with the star`() {
        write(
            "sample/src/test/kotlin/SampleTest.kt",
            """
            package sample

            import io.mockk.*

            class SampleTest
            """,
        )

        val findings = runDetekt(root)

        assertThat(findings).single().transform { it.message }
            .isEqualTo("Import of 'io.mockk.*' is not allowed. Use fakes instead of mocking libraries.")
    }

    // tests.md: be-detekt-konsist A11
    @Test
    fun `without a cardigan section in the config the rule reports nothing`() {
        writeMockkImportTest()
        val configWithoutCardigan = write(
            "detekt.yml",
            """
            style:
              MaxLineLength:
                active: false
            """,
        )

        val findings = runDetekt(root.resolve("sample"), config = configWithoutCardigan)

        assertThat(findings).isEmpty()
    }

    // tests.md: be-detekt-konsist A12
    @Test
    fun `the shared config activates the rule`() {
        writeMockkImportTest()

        val findings = runDetekt(root.resolve("sample"))

        assertThat(findings.map { it.line() }).containsExactly(3)
    }

    private fun writeMockkImportTest() {
        write(
            "sample/src/test/kotlin/SampleTest.kt",
            """
            package sample

            import io.mockk.mockk

            class SampleTest
            """,
        )
    }

    private fun write(relativePath: String, content: String): Path {
        val file = root.resolve(relativePath)
        file.parent.createDirectories()
        file.writeText(content.trimIndent() + "\n")
        return file
    }

    private fun runDetekt(input: Path, config: Path = sharedConfig): List<Issue> {
        val log = StringBuilder()
        val spec = ProcessingSpecBuilder().apply {
            project {
                inputPaths = listOf(input)
                basePath = input
            }
            config {
                configPaths = listOf(config)
                useDefaultConfig = true
            }
            logging {
                outputChannel = log
                errorChannel = log
            }
        }.build()

        val result = DetektProvider.load().get(spec).run()

        val error = result.error
        if (error != null && error !is IssuesFound) {
            throw AssertionError("detekt did not finish its analysis: $error\n$log", error)
        }
        return checkNotNull(result.container) { "detekt returned no result\n$log" }
            .issues
            .filter { it.ruleInstance.ruleSetId == RuleSetId("cardigan") && it.ruleInstance.id == "NoMockingLibraries" }
    }

    private fun Issue.fileName(): String = location.path.fileName.toString()

    private fun Issue.line(): Int = location.source.line

    private companion object {
        // Gradle runs the tests with the module directory, `tooling/`, as working directory.
        val sharedConfig: Path = Path("..", "config", "detekt", "detekt.yml").toAbsolutePath().normalize()
    }
}
