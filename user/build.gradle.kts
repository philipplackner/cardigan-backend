plugins {
    alias(libs.plugins.convention.spring.module)
    alias(libs.plugins.convention.spring.jpa)
    `java-test-fixtures`
}

dependencies {
    testFixturesImplementation(projects.common)

    testImplementation(libs.spring.boot.starter.data.jpa.test)
}
