import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Prints the path of the combined mutation report and fails when an optional minimum mutation
 * score isn't met by the selected modules together.
 */
abstract class MutationReportTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mutationFiles: ConfigurableFileCollection

    @get:Internal
    abstract val htmlReport: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val minScore: Property<BigDecimal>

    init {
        doNotTrackState("Checks the minimum score on every run")
    }

    @TaskAction
    fun report() {
        logger.lifecycle("Mutation report: ${htmlReport.get().asFile.absolutePath}")

        val min = minScore.orNull ?: return
        val counts = mutationFiles.files.filter { it.isFile }.map(::readCounts).fold(MutationCounts(0, 0, 0), MutationCounts::plus)
        if (counts.total == 0L) return
        if (BigDecimal(counts.killed) * BigDecimal(100) >= min * BigDecimal(counts.total)) return
        throw GradleException(
            "Mutation score ${percent(counts.killed, counts.total)}% (test strength " +
                "${percent(counts.killed, counts.covered)}%) is below the required " +
                "${min.stripTrailingZeros().toPlainString()}% ($MUTATION_MIN_SCORE_PROPERTY)."
        )
    }

    private fun readCounts(file: File): MutationCounts {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isValidating = false
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        }
        val mutations = factory.newDocumentBuilder().parse(file).documentElement.getElementsByTagName("mutation")
        val statuses = (0 until mutations.length).map { (mutations.item(it) as Element).getAttribute("status") }
        return MutationCounts(
            total = statuses.size.toLong(),
            killed = statuses.count { it in KILLED_STATUSES }.toLong(),
            covered = statuses.count { it != "NO_COVERAGE" }.toLong(),
        )
    }

    // FLOOR, so a value just below the minimum never prints as equal to it.
    private fun percent(part: Long, whole: Long): String =
        if (whole == 0L) "n/a"
        else (BigDecimal(part) * BigDecimal(100)).divide(BigDecimal(whole), 2, RoundingMode.FLOOR).toPlainString()
}

/** Statuses PIT's report counts as killed. */
private val KILLED_STATUSES = setOf("KILLED", "TIMED_OUT", "MEMORY_ERROR")

private data class MutationCounts(val total: Long, val killed: Long, val covered: Long) {
    operator fun plus(other: MutationCounts) =
        MutationCounts(total + other.total, killed + other.killed, covered + other.covered)
}
