import info.solidsoft.gradle.pitest.PitestPluginExtension
import info.solidsoft.gradle.pitest.PitestTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

/**
 * Opts a module in to mutation testing. PIT is applied only in builds that request
 * `mutationReport` or a `pitest*` task, so a normal build has no PIT task at all.
 */
class PitestConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            if (!gradle.isMutationRequested()) return

            pluginManager.apply("info.solidsoft.pitest")

            val threads = providers.mutationThreads() ?: Runtime.getRuntime().availableProcessors()
            val incremental = providers.mutationIncremental()
            val sourceSets = extensions.getByType<SourceSetContainer>()
            val mainSourceSet = sourceSets.getByName("main")

            val scan = tasks.register<NonLogicClassScanTask>("pitestNonLogicClassScan") {
                description = "Lists the classes PIT must not mutate because they are wiring or configuration."
                classDirs.from(mainSourceSet.output.classesDirs)
                patterns.set(layout.buildDirectory.file("pitest/excluded-classes.txt"))
            }

            extensions.configure<PitestPluginExtension> {
                pitestVersion.set(libs.version("pitest"))
                junit5PluginVersion.set(libs.version("pitest-junit5"))
                targetClasses.set(listOf("com.plcoding.cardigan.${project.name}.*"))
                mainSourceSets.set(listOf(mainSourceSet))
                testSourceSets.set(listOf(sourceSets.getByName("test")))
                excludedGroups.set(listOf(INTEGRATION_TEST_TAG))
                excludedClasses.set(
                    scan.flatMap { it.patterns }.map { file -> file.asFile.readLines().filter(String::isNotBlank) + "*ApplicationKt" }
                )
                avoidCallsTo.set(listOf("kotlin.jvm.internal"))
                excludedMethods.set(KOTLIN_GENERATED_METHODS)
                mutators.set(listOf("DEFAULTS"))
                this.threads.set(threads)
                outputFormats.set(listOf("HTML", "XML"))
                exportLineCoverage.set(true)
                timestampedReports.set(false)
                if (incremental) {
                    val history = layout.buildDirectory.file("pitest/history.bin")
                    historyInputLocation.set(history)
                    historyOutputLocation.set(history)
                }
            }

            tasks.named<PitestTask>("pitest") {
                dependsOn(scan)
            }
        }
    }
}

/** Methods the Kotlin compiler generates for data classes. */
private val KOTLIN_GENERATED_METHODS = listOf("component*", "copy", "copy\$default", "equals", "hashCode", "toString")

internal const val INTEGRATION_TEST_TAG = "integration"
