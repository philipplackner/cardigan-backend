import org.gradle.api.Plugin
import org.gradle.api.Project

class SpringApplicationConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("cardigan.spring.module")
            pluginManager.apply("org.springframework.boot")
        }
    }
}
