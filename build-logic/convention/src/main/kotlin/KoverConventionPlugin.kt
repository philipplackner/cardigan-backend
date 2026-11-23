import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

class KoverConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlinx.kover")

            extensions.configure<KoverProjectExtension> {
                currentProject {
                    sources {
                        excludedSourceSets.add("testFixtures")
                    }
                    instrumentation {
                        disabledForAll.set(!gradle.isCoverageInstrumented())
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
