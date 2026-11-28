import org.gradle.api.invocation.Gradle

internal const val STATIC_ANALYSIS_TASK = "staticAnalysis"

/** Whether this build runs `staticAnalysis`, which collects every finding and fails only at the end. */
internal fun Gradle.isStaticAnalysisRequested(): Boolean =
    startParameter.taskNames.any { it == STATIC_ANALYSIS_TASK || it == ":$STATIC_ANALYSIS_TASK" }
