plugins {
    alias(libs.plugins.convention.spring.module)
    `java-test-fixtures`
}

dependencies {
    testFixturesApi(libs.spring.boot.starter.test)
    testFixturesApi(libs.spring.boot.testcontainers)
    testFixturesApi(libs.testcontainers.postgresql)
}
