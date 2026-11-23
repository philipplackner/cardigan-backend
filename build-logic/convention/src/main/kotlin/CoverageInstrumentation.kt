import org.gradle.api.invocation.Gradle

internal val COVERAGE_TASK_NAMES = setOf("coverageReport", "coverageDiff")

/**
 * Whether this build instruments test tasks for coverage. True only when a requested task name,
 * taken literally, ends in `coverageReport` or `coverageDiff`, or its last segment starts with `kover`.
 */
internal fun Gradle.isCoverageInstrumented(): Boolean =
    startParameter.taskNames.any { name ->
        val lastSegment = name.substringAfterLast(':')
        lastSegment in COVERAGE_TASK_NAMES || lastSegment.startsWith("kover")
    }


private val KOVER_REPORT_TASK = Regex("kover(\\w*Report|Verify|CachedVerify|Log)\\w*")

/** Whether [taskName] writes or checks coverage, so it is useless without instrumentation. */
internal fun isCoverageReportTask(taskName: String): Boolean =
    taskName in COVERAGE_TASK_NAMES || KOVER_REPORT_TASK.matches(taskName)
