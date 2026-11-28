import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import java.io.File
import java.math.BigDecimal
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Prints a mutation summary per selected module and in total, and fails when an optional minimum
 * mutation score isn't met by the selected modules together.
 */
abstract class MutationReportTask : DefaultTask() {
    /** Gradle paths of the selected modules, in the order the summary lists them. */
    @get:Input
    abstract val modulePaths: ListProperty<String>

    @get:Internal
    abstract val mutationFiles: MapProperty<String, RegularFile>

    @get:Internal
    abstract val combinedReportDir: DirectoryProperty

    /** Path of the combined HTML report relative to the root project directory. */
    @get:Input
    abstract val htmlReportPath: Property<String>

    @get:Input
    @get:Optional
    abstract val minScore: Property<BigDecimal>

    init {
        doNotTrackState("Prints the summary and checks the minimum score on every run")
    }

    @TaskAction
    fun report() {
        val files = mutationFiles.get()
        val perModule =
            modulePaths.get().map { path ->
                val file = files.getValue(path).asFile
                path to MutationCounts.of(if (file.isFile) readStatuses(file) else emptyList())
            }
        val total = perModule.map { it.second }.fold(MutationCounts(0, 0, 0), MutationCounts::plus)
        if (total.total == 0L) {
            // The aggregate task is skipped then, so a report left from an earlier run would be stale.
            combinedReportDir.get().asFile.deleteRecursively()
        }
        mutationSummary(perModule, htmlReportPath.get()).forEach(logger::lifecycle)

        val min = minScore.orNull ?: return
        if (!total.meets(min)) throw GradleException(minScoreFailure(total, min))
    }

    private fun readStatuses(file: File): List<String> {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                isValidating = false
                isNamespaceAware = false
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            }
        val mutations =
            factory
                .newDocumentBuilder()
                .parse(file)
                .documentElement
                .getElementsByTagName("mutation")
        return (0 until mutations.length).map { (mutations.item(it) as Element).getAttribute("status") }
    }
}

/** Statuses that count as killed for the mutation score and test strength: the ones PIT counts as detected. */
internal val KILLED_STATUSES = setOf("KILLED", "TIMED_OUT", "MEMORY_ERROR", "RUN_ERROR", "NON_VIABLE")

internal const val NO_MUTANTS_MESSAGE = "No mutants in the selected modules. No combined report was written."

/**
 * The terminal summary for [perModule] (Gradle path to counts, in display order). Modules without
 * mutants are left out; when no module has any, only [NO_MUTANTS_MESSAGE] is printed.
 */
internal fun mutationSummary(
    perModule: List<Pair<String, MutationCounts>>,
    htmlReportPath: String,
): List<String> {
    val withMutants = perModule.filter { it.second.total > 0 }
    if (withMutants.isEmpty()) return listOf(NO_MUTANTS_MESSAGE)
    val count = withMutants.size
    val total = withMutants.map { it.second }.reduce(MutationCounts::plus)
    return buildList {
        add("Mutation testing ($count ${if (count == 1) "module" else "modules"} with mutants):")
        withMutants.forEach { (path, counts) -> add("  $path: ${counts.describe()}") }
        add("Total: ${total.describe()}")
        add("HTML: $htmlReportPath")
    }
}

internal fun minScoreFailure(
    total: MutationCounts,
    min: BigDecimal,
): String =
    "Mutation score ${total.scorePercent()} is below $MUTATION_MIN_SCORE_PROPERTY=${min.stripTrailingZeros().toPlainString()}"

/** Mutant counts from PIT's per-mutant `status` values. */
internal data class MutationCounts(
    val total: Long,
    val killed: Long,
    val noCoverage: Long,
) {
    val survived: Long get() = total - killed - noCoverage

    operator fun plus(other: MutationCounts) =
        MutationCounts(total + other.total, killed + other.killed, noCoverage + other.noCoverage)

    /** Whether the unrounded mutation score reaches [min] percent. No mutants count as met. */
    fun meets(min: BigDecimal): Boolean = total == 0L || BigDecimal(killed) * BigDecimal(100) >= min * BigDecimal(total)

    fun scorePercent(): String = percent(killed, total)

    fun strengthPercent(): String = percent(killed, total - noCoverage)

    fun describe(): String =
        "mutation score ${scorePercent()}, test strength ${strengthPercent()} " +
            "($killed killed, $survived survived, $noCoverage no coverage, $total total)"

    companion object {
        fun of(statuses: List<String>) =
            MutationCounts(
                total = statuses.size.toLong(),
                killed = statuses.count { it in KILLED_STATUSES }.toLong(),
                noCoverage = statuses.count { it == "NO_COVERAGE" }.toLong(),
            )

        private fun percent(
            part: Long,
            whole: Long,
        ): String = if (whole == 0L) "n/a" else String.format(Locale.ROOT, "%.1f%%", part * 100.0 / whole)
    }
}
