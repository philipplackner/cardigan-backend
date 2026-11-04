plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kotlin.allopen.gradlePlugin)
    compileOnly(libs.kotlin.noarg.gradlePlugin)
    compileOnly(libs.spring.boot.gradlePlugin)
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
    }
}
