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
        val counts = MutationCounts.of(mutationFiles.files.filter { it.isFile }.flatMap(::readStatuses))
        if (counts.meets(min)) return
        throw GradleException(
            "Mutation score ${counts.scorePercent()}% (test strength ${counts.strengthPercent()}%) is below the required " +
                "${min.stripTrailingZeros().toPlainString()}% ($MUTATION_MIN_SCORE_PROPERTY)."
        )
    }

    private fun readStatuses(file: File): List<String> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isValidating = false
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        }
        val mutations = factory.newDocumentBuilder().parse(file).documentElement.getElementsByTagName("mutation")
        return (0 until mutations.length).map { (mutations.item(it) as Element).getAttribute("status") }
    }
}

/** Statuses that count as killed for the mutation score and test strength: the ones PIT counts as detected. */
internal val KILLED_STATUSES = setOf("KILLED", "TIMED_OUT", "MEMORY_ERROR", "RUN_ERROR", "NON_VIABLE")

/** Mutant counts of a selection, from PIT's per-mutant `status` values. */
internal data class MutationCounts(val total: Long, val killed: Long, val covered: Long) {

    /** Whether the unrounded mutation score reaches [min] percent. No mutants count as met. */
    fun meets(min: BigDecimal): Boolean =
        total == 0L || BigDecimal(killed) * BigDecimal(100) >= min * BigDecimal(total)

    fun scorePercent(): String = percent(killed, total)

    fun strengthPercent(): String = percent(killed, covered)

    companion object {
        fun of(statuses: List<String>) = MutationCounts(
            total = statuses.size.toLong(),
            killed = statuses.count { it in KILLED_STATUSES }.toLong(),
            covered = statuses.count { it != "NO_COVERAGE" }.toLong(),
        )

        // FLOOR, so a value just below the minimum never prints as equal to it.
        private fun percent(part: Long, whole: Long): String =
            if (whole == 0L) "0.00"
            else (BigDecimal(part) * BigDecimal(100)).divide(BigDecimal(whole), 2, RoundingMode.FLOOR).toPlainString()
    }
}
