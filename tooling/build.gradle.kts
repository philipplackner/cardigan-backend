plugins {
    alias(libs.plugins.convention.kotlin.jvm)
}

dependencies {
    compileOnly(libs.detekt.api)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.detekt.test)
    testImplementation(libs.detekt.core)
    testImplementation(libs.konsist)
}

val konsistPackage = "com.plcoding.cardigan.tooling.konsist.*"

tasks.test {
    filter.excludeTestsMatching(konsistPackage)
    inputs.files(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("detektConfig")
    val requested = gradle.startParameter.taskNames.any { it == ":tooling:test" || it == "tooling:test" }
    onlyIf("only runs when :tooling:test is requested") { requested }
}

tasks.register<Test>("konsist") {
    description = "Runs the Konsist architecture checks."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter.includeTestsMatching(konsistPackage)
    inputs.files(listOf("common", "user", "board").map { rootProject.layout.projectDirectory.dir("$it/src") })
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("checkedSources")
    val requested = gradle.startParameter.taskNames.any {
        it == "konsist" || it == ":tooling:konsist" || it == "staticAnalysis"
    }
    onlyIf("only runs when konsist or staticAnalysis is requested") { requested }
}

tasks.named("check") {
    setDependsOn(dependsOn.filterNot { it is NamedDomainObjectProvider<*> && it.name == "test" })
}
