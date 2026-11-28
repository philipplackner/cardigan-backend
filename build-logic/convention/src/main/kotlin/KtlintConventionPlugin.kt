import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

/**
 * Opts a project in to `ktlintCheck` and `ktlintFormat`. ktlint runs only when one of those tasks is
 * called: `check` and `build` never run it. On the root project, both tasks also cover build-logic.
 */
class KtlintConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")

            val buildDir = layout.buildDirectory.get().asFile
            extensions.configure<KtlintExtension> {
                version.set(libs.version("ktlint"))
                outputToConsole.set(true)
                verbose.set(true)
                ignoreFailures.set(false)
                reporters {
                    reporter(ReporterType.SARIF)
                    reporter(ReporterType.CHECKSTYLE)
                }
                filter {
                    exclude { it.file.startsWith(buildDir) }
                }
            }

            // ktlint-gradle hooks a check task per source set into check, also for source sets added later.
            pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
                afterEvaluate {
                    tasks.named("check") {
                        setDependsOn(dependsOn.filterNot { it.isKtlintTask() })
                    }
                }
            }

            if (this == rootProject) {
                val buildLogic = gradle.includedBuild("build-logic")
                tasks.named("ktlintCheck") { dependsOn(buildLogic.task(":convention:ktlintCheck")) }
                tasks.named("ktlintFormat") { dependsOn(buildLogic.task(":convention:ktlintFormat")) }
            }
        }
    }
}

private fun Any.isKtlintTask(): Boolean =
    when (this) {
        is TaskProvider<*> -> name.startsWith("ktlint")
        is Task -> name.startsWith("ktlint")
        is String -> startsWith("ktlint")
        else -> false
    }
