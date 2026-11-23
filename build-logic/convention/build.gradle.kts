plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kotlin.allopen.gradlePlugin)
    compileOnly(libs.kotlin.noarg.gradlePlugin)
    compileOnly(libs.spring.boot.gradlePlugin)
    compileOnly(libs.kover.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kotlinJvm") {
            id = "cardigan.kotlin.jvm"
            implementationClass = "KotlinJvmConventionPlugin"
        }
        register("springModule") {
            id = "cardigan.spring.module"
            implementationClass = "SpringModuleConventionPlugin"
        }
        register("springJpa") {
            id = "cardigan.spring.jpa"
            implementationClass = "SpringJpaConventionPlugin"
        }
        register("springApplication") {
            id = "cardigan.spring.application"
            implementationClass = "SpringApplicationConventionPlugin"
        }
        register("kover") {
            id = "cardigan.kover"
            implementationClass = "KoverConventionPlugin"
        }
        register("koverReport") {
            id = "cardigan.kover.report"
            implementationClass = "KoverReportConventionPlugin"
        }
    }
}
