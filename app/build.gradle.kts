plugins {
    alias(libs.plugins.convention.spring.application)
    alias(libs.plugins.convention.spring.jpa)
}

dependencies {
    implementation(projects.common)
    implementation(projects.user)
    implementation(projects.board)

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
}
