plugins {
    alias(libs.plugins.convention.spring.module)
    `java-test-fixtures`
    alias(libs.plugins.convention.kover)
    alias(libs.plugins.convention.pitest)
    alias(libs.plugins.convention.ktlint)
}

dependencies {
    api(libs.spring.boot.starter.webmvc)
    api(libs.spring.boot.starter.security)
    api(libs.spring.boot.starter.security.oauth2.resource.server)
    api(libs.spring.boot.starter.validation)
    api(libs.springdoc.openapi.webmvc.api)

    testFixturesApi(libs.spring.boot.starter.test)
    testFixturesApi(libs.spring.boot.starter.security.test)
    testFixturesApi(libs.spring.boot.testcontainers)
    testFixturesApi(libs.testcontainers.postgresql)
    testFixturesImplementation(libs.spring.jdbc)
}
