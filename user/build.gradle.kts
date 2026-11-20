plugins {
    alias(libs.plugins.convention.spring.module)
    alias(libs.plugins.convention.spring.jpa)
    `java-test-fixtures`
}

dependencies {
    implementation(libs.bucket4j.core)
    implementation(libs.caffeine)

    testImplementation(libs.spring.boot.starter.data.jpa.test)
}
