import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

/**
 * Applied to the root project. Merges the coverage of every module that applies `cardigan.kover`
 * and registers `coverageReport`.
 */
class KoverReportConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            check(this == rootProject) { "cardigan.kover.report must be applied to the root project" }
            pluginManager.apply("org.jetbrains.kotlinx.kover")

            val optedInPaths = sortedMapOf<String, String>()
            subprojects {
                val module = this
                module.pluginManager.withPlugin("cardigan.kover") {
                    optedInPaths[module.name] = module.path
                    target.dependencies.add("kover", target.dependencies.project(mapOf("path" to module.path)))
                }
            }

            val minLine = parseCoveragePercent(MIN_LINE_PROPERTY, providers.gradleProperty(MIN_LINE_PROPERTY).orNull)
            val minBranch = parseCoveragePercent(MIN_BRANCH_PROPERTY, providers.gradleProperty(MIN_BRANCH_PROPERTY).orNull)

            extensions.configure<KoverProjectExtension> {
                reports {
                    total {
                        filters {
                            excludes {
                                annotatedBy(
                                    "org.springframework.context.annotation.Configuration",
                                    "org.springframework.boot.context.properties.ConfigurationProperties",
                                    "org.springframework.boot.autoconfigure.SpringBootApplication",
                                )
                                classes("*ApplicationKt")
                            }
                        }
                    }
                }
            }

            val xmlReport = layout.buildDirectory.file("reports/kover/report.xml")
            val htmlReport = layout.buildDirectory.file("reports/kover/html/index.html")

            tasks.register<CoverageReportTask>("coverageReport") {
                group = "verification"
                description = "Writes the merged coverage report and prints a summary. " +
                    "Options: -P$MODULES_PROPERTY, -P$MIN_LINE_PROPERTY, -P$MIN_BRANCH_PROPERTY."
                dependsOn("koverXmlReport", "koverHtmlReport")
                this.xmlReport.set(xmlReport)
                this.htmlReport.set(htmlReport)
                minLine?.let { this.minLine.set(it) }
                minBranch?.let { this.minBranch.set(it) }
            }

            gradle.projectsEvaluated {
                val selected = parseCoverageModules(
                    target.providers.gradleProperty(MODULES_PROPERTY).orNull,
                    optedInPaths.keys,
                )
                if (selected.size < optedInPaths.size) {
                    target.extensions.configure<KoverProjectExtension> {
                        reports {
                            total {
                                filtersAppend {
                                    includes {
                                        projects.addAll(selected.map { optedInPaths.getValue(it) })
                                    }
                                }
                            }
                        }
                    }
                }
                target.tasks.named<CoverageReportTask>("coverageReport") {
                    modules.set(selected)
                }
            }

            val instrumented = gradle.isCoverageInstrumented()
            gradle.taskGraph.whenReady {
                val needsInstrumentation = allTasks.any { isCoverageReportTask(it.name) }
                if (!instrumented && needsInstrumentation) {
                    throw GradleException(
                        "Coverage needs instrumentation. Run with the full task name, e.g. ./gradlew coverageReport."
                    )
                }
            }
        }
    }
}
