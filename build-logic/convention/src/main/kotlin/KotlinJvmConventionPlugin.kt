import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class KotlinJvmConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")

            extensions.configure<KotlinJvmProjectExtension> {
                jvmToolchain(libs.version("java").toInt())
                compilerOptions {
                    freeCompilerArgs.addAll(
                        "-Xjsr305=strict",
                        "-Xannotation-default-target=param-property",
                        // Bean Validation needs constraints on type arguments, such as List<@ValidUuid String>.
                        "-Xemit-jvm-type-annotations",
                    )
                }
            }

            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
            }

            dependencies {
                "testImplementation"(libs.library("junit-jupiter"))
                "testImplementation"(libs.library("assertk"))
                "testRuntimeOnly"(libs.library("junit-platform-launcher"))
            }
        }
    }
}
