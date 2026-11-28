plugins {
    alias(libs.plugins.convention.spring.module)
    alias(libs.plugins.convention.spring.jpa)
    alias(libs.plugins.convention.kover)
    alias(libs.plugins.convention.pitest)
    alias(libs.plugins.convention.ktlint)
}

dependencies {
    implementation(libs.icu4j)

    testImplementation(libs.spring.boot.starter.data.jpa.test)
}
