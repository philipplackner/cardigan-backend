import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import java.math.BigDecimal
import java.math.RoundingMode
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Prints the total line and branch coverage of the Kover XML report and fails when an optional
 * minimum isn't met.
 */
abstract class CoverageReportTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val xmlReport: RegularFileProperty

    @get:Internal
    abstract val htmlReport: RegularFileProperty

    @get:Input
    abstract val modules: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val minLine: Property<BigDecimal>

    @get:Input
    @get:Optional
    abstract val minBranch: Property<BigDecimal>

    init {
        doNotTrackState("Prints the coverage summary on every run")
    }

    @TaskAction
    fun report() {
        val counters = readReportCounters(xmlReport.get().asFile)
        val line = counters["LINE"] ?: Counter(0, 0)
        val branch = counters["BRANCH"] ?: Counter(0, 0)

        logger.lifecycle(
            """
            |Coverage for: ${modules.get().joinToString(", ")}
            |  Line:   ${line.summary()}
            |  Branch: ${branch.summary()}
            |XML report:  ${xmlReport.get().asFile.absolutePath}
            |HTML report: ${htmlReport.get().asFile.absolutePath}
            """.trimMargin(),
        )

        val failures =
            listOfNotNull(
                line.thresholdFailure("Line", MIN_LINE_PROPERTY, minLine.orNull),
                branch.thresholdFailure("Branch", MIN_BRANCH_PROPERTY, minBranch.orNull),
            )
        if (failures.isNotEmpty()) throw GradleException(failures.joinToString("\n"))
    }

    private fun readReportCounters(file: java.io.File): Map<String, Counter> {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                isValidating = false
                isNamespaceAware = false
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            }
        val root = factory.newDocumentBuilder().parse(file).documentElement
        val nodes = root.childNodes
        return (0 until nodes.length)
            .map { nodes.item(it) }
            .filterIsInstance<Element>()
            .filter { it.tagName == "counter" }
            .associate {
                it.getAttribute("type") to
                    Counter(
                        covered = it.getAttribute("covered").toLong(),
                        missed = it.getAttribute("missed").toLong(),
                    )
            }
    }

    private data class Counter(
        val covered: Long,
        val missed: Long,
    ) {
        val total get() = covered + missed

        fun summary(): String =
            if (total == 0L) "n/a (0/0)" else "${percent(1, RoundingMode.HALF_UP)}% ($covered/$total)"

        fun thresholdFailure(
            metric: String,
            property: String,
            min: BigDecimal?,
        ): String? {
            if (min == null || total == 0L) return null
            if (BigDecimal(covered) * BigDecimal(100) >= min * BigDecimal(total)) return null
            return "$metric coverage ${percent(2, RoundingMode.FLOOR)}% is below the required " +
                "${min.stripTrailingZeros().toPlainString()}% ($property)."
        }

        // FLOOR in failure messages, so a value just below the minimum never prints as equal to it.
        private fun percent(
            scale: Int,
            rounding: RoundingMode,
        ): BigDecimal = (BigDecimal(covered) * BigDecimal(100)).divide(BigDecimal(total), scale, rounding)
    }
}
