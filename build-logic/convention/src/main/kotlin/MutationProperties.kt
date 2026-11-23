import org.gradle.api.GradleException
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.ProviderFactory

internal const val MUTATION_REPORT_TASK = "mutationReport"
internal const val MUTATION_MODULES_PROPERTY = "mutation.modules"
internal const val MUTATION_THREADS_PROPERTY = "mutation.threads"
internal const val MUTATION_MIN_SCORE_PROPERTY = "mutation.minScore"

/**
 * Whether this build runs mutation testing. True only when a requested task name, taken literally,
 * ends in `mutationReport`, or its last segment starts with `pitest`.
 */
internal fun Gradle.isMutationRequested(): Boolean =
    startParameter.taskNames.any { name ->
        val lastSegment = name.substringAfterLast(':')
        lastSegment == MUTATION_REPORT_TASK || lastSegment.startsWith("pitest")
    }

/**
 * Whether module [name] is in the raw `mutation.modules` value. Lenient on purpose: the root
 * plugin validates the value and fails on anything invalid.
 */
internal fun isSelectedForMutation(raw: String?, name: String): Boolean =
    raw == null || raw.split(",").any { it.trim().removePrefix(":") == name }

/** Parses `mutation.threads`. Returns null when the property is absent. */
internal fun ProviderFactory.mutationThreads(): Int? {
    val raw = gradleProperty(MUTATION_THREADS_PROPERTY).orNull ?: return null
    val value = raw.trim().toIntOrNull()
    if (value == null || value < 1) {
        throw GradleException("Invalid $MUTATION_THREADS_PROPERTY '$raw': expected a positive whole number.")
    }
    return value
}

