import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Runs diff-cover through `uvx` on the Kover XML report. Report-only: it never fails on low coverage.
 */
abstract class DiffCoverageTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val xmlReport: RegularFileProperty

    @get:Internal
    abstract val htmlReport: RegularFileProperty

    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    @get:Input
    abstract val compareBranch: Property<String>

    @get:Input
    abstract val diffCoverVersion: Property<String>

    @get:Input
    abstract val excludeNewer: Property<String>

    /** Source roots relative to [repositoryRoot], because diff-cover matches them against `git diff` paths. */
    @get:Input
    abstract val sourceRoots: ListProperty<String>

    init {
        doNotTrackState("Compares against the working tree and Git state on every run")
    }

    @TaskAction
    fun diffCoverage() {
        requireUv()
        requireRef(compareBranch.get())

        val root = repositoryRoot.get().asFile
        val html = htmlReport.get().asFile
        html.parentFile.mkdirs()
        execOperations.exec {
            workingDir = root
            commandLine(
                listOf(
                    "uvx", "--exclude-newer", excludeNewer.get(), "diff-cover==${diffCoverVersion.get()}",
                    xmlReport.get().asFile.absolutePath,
                    "--compare-branch=${compareBranch.get()}",
                    "--include-untracked",
                    "--format", "html:${html.absolutePath}",
                    "--src-roots",
                ) + sourceRoots.get()
            )
        }
        logger.lifecycle("Diff coverage HTML report: ${html.absolutePath}")
    }

    private fun requireUv() {
        val exitValue = try {
            execOperations.exec {
                commandLine("uv", "--version")
                standardOutput = ByteArrayOutputStream()
                errorOutput = ByteArrayOutputStream()
                isIgnoreExitValue = true
            }.exitValue
        } catch (e: Exception) {
            -1
        }
        if (exitValue != 0) {
            throw GradleException("`uv` is required for diffCoverage: https://docs.astral.sh/uv/")
        }
    }

    private fun requireRef(ref: String) {
        val result = execOperations.exec {
            workingDir = repositoryRoot.get().asFile
            commandLine("git", "rev-parse", "--verify", "--quiet", "$ref^{commit}")
            standardOutput = ByteArrayOutputStream()
            errorOutput = ByteArrayOutputStream()
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) {
            throw GradleException(
                "Base ref '$ref' for diffCoverage doesn't exist. Run `git fetch` or pass -P$COMPARE_BRANCH_PROPERTY=<ref>."
            )
        }
    }
}
