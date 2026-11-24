import info.solidsoft.gradle.pitest.PitestPluginExtension
import info.solidsoft.gradle.pitest.PitestTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
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
            // The aggregator merges every project that has PIT, so unselected modules must not get it.
            if (!isSelectedForMutation(providers.gradleProperty(MUTATION_MODULES_PROPERTY).orNull, name)) return

            pluginManager.apply("info.solidsoft.pitest")

            val threads = providers.mutationThreads() ?: Runtime.getRuntime().availableProcessors()
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
                failWhenNoMutations.set(false)
            }

            // Running modules one at a time keeps the thread count a total for the machine.
            val runLimit = gradle.sharedServices.registerIfAbsent("pitestRunLimit", PitestRunLimit::class.java) {
                maxParallelUsages.set(1)
            }

            tasks.named<PitestTask>("pitest") {
                dependsOn(scan)
                usesService(runLimit)
                outputs.upToDateWhen { false }
                outputs.cacheIf { false }
                // PIT writes no mutations.xml when a module has no mutants, so an old one must not survive.
                val reportDir = reportDir
                doFirst { reportDir.get().asFile.deleteRecursively() }
            }
        }
    }
}

/** Lets at most one module's PIT run execute at a time. */
abstract class PitestRunLimit : BuildService<BuildServiceParameters.None>

/** Methods the Kotlin compiler generates for data classes. */
private val KOTLIN_GENERATED_METHODS = listOf("component*", "copy", "copy\$default", "equals", "hashCode", "toString")

internal const val INTEGRATION_TEST_TAG = "integration"
