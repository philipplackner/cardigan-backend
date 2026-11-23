import info.solidsoft.gradle.pitest.AggregateReportTask
import info.solidsoft.gradle.pitest.PitestTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import java.io.File

/**
 * Applied to the root project. Registers `mutationReport`, which runs PIT on the selected modules
 * that apply `cardigan.pitest` and merges their reports.
 */
class PitestReportConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            check(this == rootProject) { "cardigan.pitest.report must be applied to the root project" }

            val requested = gradle.isMutationRequested()
            val optedInPaths = sortedMapOf<String, String>()
            subprojects {
                val module = this
                module.pluginManager.withPlugin("cardigan.pitest") {
                    optedInPaths[module.name] = module.path
                }
            }

            val combinedReport = layout.buildDirectory.file("reports/pitest/index.html")
            val report = tasks.register<MutationReportTask>(MUTATION_REPORT_TASK) {
                group = "verification"
                description = "Runs mutation testing and writes the combined report. Options: " +
                    "-P$MUTATION_MODULES_PROPERTY, -P$MUTATION_THREADS_PROPERTY, " +
                    "-P$MUTATION_INCREMENTAL_PROPERTY, -P$MUTATION_MIN_SCORE_PROPERTY."
                htmlReport.set(combinedReport)
            }

            gradle.taskGraph.whenReady {
                if (!requested && allTasks.any { it.name == MUTATION_REPORT_TASK }) {
                    throw GradleException(
                        "Mutation testing is off for this build. Run with the full task name, e.g. ./gradlew mutationReport."
                    )
                }
            }

            if (!requested) return

            pluginManager.apply("info.solidsoft.pitest.aggregator")
            providers.mutationThreads()
            providers.mutationIncremental()
            val minScore = parsePercent(
                MUTATION_MIN_SCORE_PROPERTY,
                providers.gradleProperty(MUTATION_MIN_SCORE_PROPERTY).orNull,
            )

            gradle.projectsEvaluated {
                val selected = parseModuleSelection(
                    MUTATION_MODULES_PROPERTY,
                    target.providers.gradleProperty(MUTATION_MODULES_PROPERTY).orNull,
                    optedInPaths.keys,
                ).map { target.project(optedInPaths.getValue(it)) }

                val pitestTasks = selected.map { it.tasks.named<PitestTask>("pitest") }
                val reportDirs = selected.map { it.layout.buildDirectory.dir("reports/pitest") }

                val aggregate = target.tasks.named<AggregateReportTask>("pitestReportAggregate") {
                    dependsOn(pitestTasks)
                    reportDir.set(target.layout.buildDirectory.dir("reports/pitest"))
                    reportFile.set(combinedReport)
                    mutationFiles.setFrom(reportDirs.map { dir -> dir.map { it.file("mutations.xml") } })
                    lineCoverageFiles.setFrom(reportDirs.map { dir -> dir.map { it.file("linecoverage.xml") } })
                    sourceDirs.setFrom(pitestTasks.map { task -> task.map { it.sourceDirs } })
                    additionalClasspath.setFrom(
                        pitestTasks.map { task -> task.map { it.additionalClasspath.filter(File::isDirectory) } }
                    )
                }

                report.configure {
                    dependsOn(aggregate)
                    mutationFiles.setFrom(reportDirs.map { dir -> dir.map { it.file("mutations.xml") } })
                    minScore?.let { this.minScore.set(it) }
                }
            }
        }
    }
}
