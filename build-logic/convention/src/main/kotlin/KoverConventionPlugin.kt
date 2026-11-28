import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/**
 * Opts a module in to coverage. Kover is applied only in builds that request a coverage task, so a
 * normal build runs no Kover task at all.
 */
class KoverConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            if (!gradle.isCoverageInstrumented()) return

            pluginManager.apply("org.jetbrains.kotlinx.kover")

            extensions.configure<KoverProjectExtension> {
                currentProject {
                    sources {
                        excludedSourceSets.add("testFixtures")
                    }
                    instrumentation {
                        // Other Test tasks, such as :app:updateOpenApiSnapshot, write files and must never run for coverage.
                        disabledForTestTasks.addAll(provider { tasks.withType<Test>().names.filter { it != "test" } })
                    }
                }
                reports {
                    total {
                        verify {
                            onCheck.set(false)
                        }
                    }
                }
            }
        }
    }
}
