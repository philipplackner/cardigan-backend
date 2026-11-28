import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.extensions.FailOnSeverity
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Opts a module in to detekt with the shared config and the `cardigan` rule set. `detekt` runs the
 * type-resolution tasks of every source set (`detektMain`, `detektTest`, ...). No detekt task is part of `check`.
 */
class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("dev.detekt")

            val staticAnalysis = gradle.isStaticAnalysisRequested()

            extensions.configure<DetektExtension> {
                config.setFrom(rootDir.resolve("config/detekt/detekt.yml"))
                buildUponDefaultConfig.set(true)
                parallel.set(true)
                failOnSeverity.set(FailOnSeverity.Info)
                ignoreFailures.set(staticAnalysis)
            }

            dependencies {
                "detektPlugins"(project(":tooling"))
            }

            tasks.withType<Detekt>().configureEach {
                baseline.set(null as java.io.File?)
                reports {
                    html.required.set(true)
                    sarif.required.set(true)
                    markdown.required.set(false)
                    checkstyle.required.set(false)
                }
            }

            val typeResolutionTasks = tasks.withType<Detekt>().matching { it.isTypeResolutionTask() }
            tasks.named<Detekt>(DETEKT_TASK) {
                description = "Runs detekt with type resolution on every source set of this module."
                setSource(emptyList<Any>())
                dependsOn(typeResolutionTasks)
            }

            tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) {
                setDependsOn(dependsOn.filterNot { it.isDetektTask() })
            }
        }
    }

    private fun Task.isTypeResolutionTask(): Boolean =
        name.startsWith(DETEKT_TASK) && name != DETEKT_TASK && !name.endsWith("SourceSet")

    private fun Any.isDetektTask(): Boolean =
        (this is TaskProvider<*> && name.startsWith(DETEKT_TASK)) || (this is Detekt)

    private companion object {
        const val DETEKT_TASK = "detekt"
    }
}
