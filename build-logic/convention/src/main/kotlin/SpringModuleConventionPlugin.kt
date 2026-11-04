import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

class SpringModuleConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("cardigan.kotlin.jvm")
            pluginManager.apply("org.jetbrains.kotlin.plugin.spring")
            pluginManager.apply("java-library")

            pluginManager.withPlugin("java-test-fixtures") {
                dependencies {
                    "testFixturesImplementation"(platform(libs.library("spring-boot-bom")))
                }
            }

            dependencies {
                "implementation"(platform(libs.library("spring-boot-bom")))

                "implementation"(libs.library("kotlin-reflect"))
                "implementation"(libs.library("jackson-module-kotlin"))
                "implementation"(libs.library("spring-boot-starter"))

                if (path != ":common") {
                    "implementation"(project(":common"))
                    "testImplementation"(testFixtures(project(":common")))
                }
            }
        }
    }
}
