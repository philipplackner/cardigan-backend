plugins {
    alias(libs.plugins.convention.spring.module)
    alias(libs.plugins.convention.spring.jpa)
}

dependencies {
    testImplementation(libs.spring.boot.starter.data.jpa.test)
}
