plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.pitest) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.convention.kover.report)
    alias(libs.plugins.convention.pitest.report)
    alias(libs.plugins.convention.ktlint)
}

allprojects {
    group = "com.plcoding"
    version = "0.0.1-SNAPSHOT"
}

val detektReportMerge = tasks.register<dev.detekt.gradle.report.ReportMergeTask>("detektReportMerge") {
    description = "Merges the SARIF reports of every analyzed module and build-logic."
    output = layout.buildDirectory.file("reports/detekt/merge.sarif")
    input.from(
        file("build-logic/convention/build/reports/detekt/main.sarif"),
        file("build-logic/convention/build/reports/detekt/test.sarif"),
    )
}

val detekt = tasks.register("detekt") {
    description = "Runs detekt on build-logic. Every module's own detekt task runs next to it."
    group = "verification"
    dependsOn(gradle.includedBuild("build-logic").task(":convention:detekt"))
    finalizedBy(detektReportMerge)
}
detektReportMerge.configure { mustRunAfter(detekt) }

val staticAnalysis = tasks.register<StaticAnalysisTask>("staticAnalysis") {
    description = "Runs detekt and the Konsist checks, and fails at the end when either found something."
    group = "verification"
    dependsOn(detekt, detektReportMerge, ":tooling:konsist")
    detektReport = detektReportMerge.flatMap { it.output }
    konsistResults.from(fileTree("tooling/build/test-results/konsist") { include("*.xml") })
}

subprojects {
    pluginManager.withPlugin("dev.detekt") {
        staticAnalysis.configure { dependsOn(tasks.named("detekt")) }
        tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
            if (!name.startsWith("detekt") || name == "detekt" || name.endsWith("SourceSet")) return@configureEach
            val task = this
            finalizedBy(detektReportMerge)
            // A plain provider adds the file without a task dependency, so `:user:detekt` doesn't run every module.
            detektReportMerge.configure {
                input.from(provider { task.reports.sarif.outputLocation.get().asFile })
                mustRunAfter(task)
            }
        }
    }
}
