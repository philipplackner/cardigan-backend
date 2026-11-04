import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension

class SpringJpaConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.jpa")

            // Hibernate creates lazy proxies by subclassing, so entities must be open.
            extensions.configure<AllOpenExtension> {
                annotation("jakarta.persistence.Entity")
                annotation("jakarta.persistence.MappedSuperclass")
                annotation("jakarta.persistence.Embeddable")
            }

            dependencies {
                "implementation"(libs.library("spring-boot-starter-data-jpa"))
                "implementation"(libs.library("spring-boot-starter-flyway"))
                "implementation"(libs.library("flyway-database-postgresql"))
                "runtimeOnly"(libs.library("postgresql"))
            }
        }
    }
}
