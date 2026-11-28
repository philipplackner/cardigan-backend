plugins {
    alias(libs.plugins.convention.spring.module)
    alias(libs.plugins.convention.spring.jpa)
    `java-test-fixtures`
    alias(libs.plugins.convention.kover)
    alias(libs.plugins.convention.pitest)
    alias(libs.plugins.convention.ktlint)
    alias(libs.plugins.convention.detekt)
}

dependencies {
    implementation(libs.bucket4j.core)
    implementation(libs.caffeine)

    testImplementation(libs.spring.boot.starter.data.jpa.test)
}
