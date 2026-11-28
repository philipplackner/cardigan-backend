import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Collects the results of detekt (merged SARIF report) and Konsist (JUnit XML results), which ran with
 * `ignoreFailures`, and fails with one message when either found something.
 */
abstract class StaticAnalysisTask : DefaultTask() {
    @get:Internal
    abstract val detektReport: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val konsistResults: ConfigurableFileCollection

    init {
        doNotTrackState("Reports the static analysis result on every run")
    }

    @TaskAction
    fun report() {
        val sarif = detektReport.get().asFile
        if (!sarif.exists()) throw GradleException("detekt wrote no merged report at ${sarif.absolutePath}.")
        val detektFindings = countSarifResults(sarif)
        val konsistFailures =
            konsistResults.files
                .filter { it.extension == "xml" }
                .flatMap(::failedTestCases)
                .sorted()

        logger.lifecycle("detekt findings: $detektFindings (${sarif.absolutePath})")
        logger.lifecycle("Konsist failures: ${konsistFailures.size}")

        val problems =
            buildList {
                if (detektFindings > 0) add("detekt found $detektFindings finding(s). Report: ${sarif.absolutePath}")
                if (konsistFailures.isNotEmpty()) {
                    add("Konsist checks failed:\n" + konsistFailures.joinToString("\n") { "  - $it" })
                }
            }
        if (problems.isNotEmpty()) throw GradleException("Static analysis failed.\n" + problems.joinToString("\n"))
    }

    private fun countSarifResults(file: File): Int {
        val root = JsonSlurper().parse(file) as Map<*, *>
        val runs = root["runs"] as? List<*> ?: return 0
        return runs.sumOf { run -> ((run as Map<*, *>)["results"] as? List<*>)?.size ?: 0 }
    }

    private fun failedTestCases(file: File): List<String> {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                isValidating = false
                isNamespaceAware = false
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            }
        val testCases =
            factory
                .newDocumentBuilder()
                .parse(file)
                .documentElement
                .getElementsByTagName("testcase")
        return (0 until testCases.length)
            .map { testCases.item(it) as Element }
            .filter { it.getElementsByTagName("failure").length > 0 || it.getElementsByTagName("error").length > 0 }
            .map { "${it.getAttribute("classname")} > ${it.getAttribute("name")}" }
    }
}
