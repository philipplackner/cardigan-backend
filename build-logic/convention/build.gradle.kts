import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

dependencies {
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kotlin.allopen.gradlePlugin)
    compileOnly(libs.kotlin.noarg.gradlePlugin)
    compileOnly(libs.spring.boot.gradlePlugin)
    compileOnly(libs.kover.gradlePlugin)
    compileOnly(libs.pitest.gradlePlugin)
    compileOnly(libs.ktlint.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)

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

// Same setup as the cardigan.detekt convention plugin, which this build can't apply to itself.
detekt {
    config.setFrom(rootDir.resolve("../config/detekt/detekt.yml"))
    // Repo-relative paths, so its findings line up with the modules' in the merged SARIF report.
    basePath = layout.projectDirectory.dir("../..")
    buildUponDefaultConfig = true
    parallel = true
    failOnSeverity = dev.detekt.gradle.extensions.FailOnSeverity.Info
    // An included build sees the task names requested from the root build only through its parent.
    val requestedTasks = (gradle.parent ?: gradle).startParameter.taskNames
    ignoreFailures = requestedTasks.any { it == "staticAnalysis" || it == ":staticAnalysis" }
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    baseline.set(null as File?)
    reports {
        html.required = true
        sarif.required = true
        markdown.required = false
        checkstyle.required = false
    }
}

val detektTypeResolutionTasks = tasks.withType<dev.detekt.gradle.Detekt>().matching {
    it.name.startsWith("detekt") && it.name != "detekt" && !it.name.endsWith("SourceSet")
}
tasks.named<dev.detekt.gradle.Detekt>("detekt") {
    description = "Runs detekt with type resolution on every source set of build-logic."
    setSource(emptyList<Any>())
    dependsOn(detektTypeResolutionTasks)
}

tasks.named("check") {
    setDependsOn(dependsOn.filterNot { it is TaskProvider<*> && it.name.startsWith("detekt") })
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
        register("detekt") {
            id = "cardigan.detekt"
            implementationClass = "DetektConventionPlugin"
        }
    }
}
