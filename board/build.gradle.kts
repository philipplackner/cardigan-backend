plugins {
    alias(libs.plugins.convention.spring.module)
    alias(libs.plugins.convention.spring.jpa)
}

dependencies {
    implementation(libs.icu4j)

    testImplementation(libs.spring.boot.starter.data.jpa.test)
}
