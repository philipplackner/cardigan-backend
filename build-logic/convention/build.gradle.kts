import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
}

dependencies {
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kotlin.allopen.gradlePlugin)
    compileOnly(libs.kotlin.noarg.gradlePlugin)
    compileOnly(libs.spring.boot.gradlePlugin)
    compileOnly(libs.kover.gradlePlugin)
    compileOnly(libs.pitest.gradlePlugin)
    compileOnly(libs.ktlint.gradlePlugin)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertk)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// Same settings as KtlintConventionPlugin, which build-logic can't apply to itself.
ktlint {
    version.set(libs.versions.ktlint.asProvider())
    outputToConsole.set(true)
    verbose.set(true)
    ignoreFailures.set(false)
    reporters {
        reporter(ReporterType.SARIF)
        reporter(ReporterType.CHECKSTYLE)
    }
    kotlinScriptAdditionalPaths {
        include(fileTree(rootDir) { include("settings.gradle.kts") })
    }
    val buildDir = layout.buildDirectory.get().asFile
    filter {
        exclude { it.file.startsWith(buildDir) }
    }
}

tasks.named("check") {
    setDependsOn(dependsOn.filterNot { it is TaskProvider<*> && it.name.startsWith("ktlint") })
}

gradlePlugin {
    plugins {
        register("kotlinJvm") {
            id = "cardigan.kotlin.jvm"
            implementationClass = "KotlinJvmConventionPlugin"
        }
        register("springModule") {
            id = "cardigan.spring.module"
            implementationClass = "SpringModuleConventionPlugin"
        }
        register("springJpa") {
            id = "cardigan.spring.jpa"
            implementationClass = "SpringJpaConventionPlugin"
        }
        register("springApplication") {
            id = "cardigan.spring.application"
            implementationClass = "SpringApplicationConventionPlugin"
        }
        register("kover") {
            id = "cardigan.kover"
            implementationClass = "KoverConventionPlugin"
        }
        register("koverReport") {
            id = "cardigan.kover.report"
            implementationClass = "KoverReportConventionPlugin"
        }
        register("pitest") {
            id = "cardigan.pitest"
            implementationClass = "PitestConventionPlugin"
        }
        register("pitestReport") {
            id = "cardigan.pitest.report"
            implementationClass = "PitestReportConventionPlugin"
        }
        register("ktlint") {
            id = "cardigan.ktlint"
            implementationClass = "KtlintConventionPlugin"
        }
    }
}
