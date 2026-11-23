plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.pitest) apply false
    alias(libs.plugins.convention.kover.report)
    alias(libs.plugins.convention.pitest.report)
}

allprojects {
    group = "com.plcoding"
    version = "0.0.1-SNAPSHOT"
}
