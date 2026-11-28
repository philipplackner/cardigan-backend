import info.solidsoft.gradle.pitest.AggregateReportTask
import info.solidsoft.gradle.pitest.PitestTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

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
            val report =
                tasks.register<MutationReportTask>(MUTATION_REPORT_TASK) {
                    group = "verification"
                    description = "Runs mutation testing and writes the combined report. Options: " +
                        "-P$MUTATION_MODULES_PROPERTY, -P$MUTATION_THREADS_PROPERTY, -P$MUTATION_MIN_SCORE_PROPERTY."
                    combinedReportDir.set(layout.buildDirectory.dir("reports/pitest"))
                    htmlReportPath.set(combinedReport.map { it.asFile.relativeTo(rootDir).invariantSeparatorsPath })
                }

            gradle.taskGraph.whenReady {
                if (!requested && allTasks.any { it.name == MUTATION_REPORT_TASK }) {
                    throw GradleException(
                        "Mutation testing is off for this build. Run with the full task name, e.g. ./gradlew mutationReport.",
                    )
                }
            }

            if (!requested) return

            pluginManager.apply("info.solidsoft.pitest.aggregator")
            providers.mutationThreads()
            val minScore =
                parsePercent(
                    MUTATION_MIN_SCORE_PROPERTY,
                    providers.gradleProperty(MUTATION_MIN_SCORE_PROPERTY).orNull,
                )

            gradle.projectsEvaluated {
                val rawModules = target.providers.gradleProperty(MUTATION_MODULES_PROPERTY).orNull
                val selectedNames = parseModuleSelection(MUTATION_MODULES_PROPERTY, rawModules, optedInPaths.keys)
                val orderedPaths =
                    if (rawModules == null) {
                        selectedNames.map(optedInPaths::getValue).sorted()
                    } else {
                        rawModules
                            .split(",")
                            .map { it.trim().removePrefix(":") }
                            .distinct()
                            .map(optedInPaths::getValue)
                    }
                val selected = orderedPaths.map(target::project)

                val pitestTasks = selected.map { it.tasks.named<PitestTask>("pitest") }
                val mutationXml =
                    selected.associate { module ->
                        module.path to module.layout.buildDirectory.file("reports/pitest/mutations.xml")
                    }

                val aggregate =
                    target.tasks.named<AggregateReportTask>("pitestReportAggregate") {
                        dependsOn(pitestTasks)
                        reportFile.set(combinedReport)
                        val xmlFiles = mutationXml.values.toList()
                        // PIT writes no mutations.xml for a module without mutants, and the aggregator fails with none at all.
                        onlyIf("a selected module has mutants") { xmlFiles.any { it.get().asFile.isFile } }
                    }

                report.configure {
                    dependsOn(aggregate)
                    modulePaths.set(orderedPaths)
                    mutationXml.forEach { (path, file) -> mutationFiles.put(path, file) }
                    minScore?.let { this.minScore.set(it) }
                }
            }
        }
    }
}
