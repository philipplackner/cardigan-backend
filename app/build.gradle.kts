plugins {
    alias(libs.plugins.convention.spring.application)
    alias(libs.plugins.convention.spring.jpa)
    alias(libs.plugins.convention.kover)
    alias(libs.plugins.convention.ktlint)
    alias(libs.plugins.convention.detekt)
}

dependencies {
    implementation(projects.common)
    implementation(projects.user)
    implementation(projects.board)

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)

    testImplementation(testFixtures(projects.user))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootDir
}

tasks.register<Test>("updateOpenApiSnapshot") {
    description = "Rewrites app/src/test/resources/openapi/openapi.json from the current code."
    group = "verification"
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("com.plcoding.cardigan.OpenApiSnapshotTest.generated*") }
    systemProperty("openapi.update", "true")
    outputs.upToDateWhen { false }
}
