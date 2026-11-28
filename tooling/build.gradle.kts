plugins {
    alias(libs.plugins.convention.kotlin.jvm)
    alias(libs.plugins.convention.detekt)
    alias(libs.plugins.convention.ktlint)
}

dependencies {
    compileOnly(libs.detekt.api)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.detekt.test)
    testImplementation(libs.detekt.core)
    testImplementation(libs.konsist)
}

// detekt brings kotlin-compiler and Konsist kotlin-compiler-embeddable; Konsist fails when it sees both.
val konsistRuntimeClasspath =
    configurations.create("konsistRuntimeClasspath") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }

dependencies {
    konsistRuntimeClasspath(platform(libs.spring.boot.bom))
    konsistRuntimeClasspath(libs.konsist)
    konsistRuntimeClasspath(libs.junit.jupiter)
    konsistRuntimeClasspath(libs.assertk)
    konsistRuntimeClasspath(libs.junit.platform.launcher)
}

val konsistPackage = "com.plcoding.cardigan.tooling.konsist.*"

tasks.test {
    filter.excludeTestsMatching(konsistPackage)
    inputs
        .files(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("detektConfig")
    val requested = gradle.startParameter.taskNames.any { it == ":tooling:test" || it == "tooling:test" }
    onlyIf("only runs when :tooling:test is requested") { requested }
}

tasks.register<Test>("konsist") {
    description = "Runs the Konsist architecture checks."
    group = "verification"
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().output + konsistRuntimeClasspath
    filter.includeTestsMatching(konsistPackage)
    inputs
        .files(listOf("common", "user", "board").map { rootProject.layout.projectDirectory.dir("$it/src") })
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("checkedSources")
    val requestedTasks = gradle.startParameter.taskNames
    val staticAnalysis = requestedTasks.any { it == "staticAnalysis" || it == ":staticAnalysis" }
    val requested = staticAnalysis || requestedTasks.any { it == "konsist" || it == ":tooling:konsist" }
    onlyIf("only runs when konsist or staticAnalysis is requested") { requested }
    ignoreFailures = staticAnalysis
}

tasks.named("check") {
    setDependsOn(dependsOn.filterNot { it is NamedDomainObjectProvider<*> && it.name == "test" })
}
