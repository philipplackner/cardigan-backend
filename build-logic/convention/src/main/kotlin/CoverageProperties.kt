import org.gradle.api.GradleException
import java.math.BigDecimal

internal const val MODULES_PROPERTY = "coverage.modules"
internal const val MIN_LINE_PROPERTY = "coverage.minLine"
internal const val MIN_BRANCH_PROPERTY = "coverage.minBranch"
internal const val COMPARE_BRANCH_PROPERTY = "coverage.compareBranch"

/**
 * Parses a module selection property such as `coverage.modules` into a sorted list of distinct
 * module names. Returns all [optedIn] modules when [raw] is null. Fails on an empty list, an empty
 * entry or a module that hasn't opted in.
 */
internal fun parseModuleSelection(property: String, raw: String?, optedIn: Set<String>): List<String> {
    if (raw == null) return optedIn.sorted()
    val valid = optedIn.sorted().joinToString(", ")
    if (raw.isBlank()) {
        throw GradleException("Invalid $property '$raw': no module given. Valid modules: $valid.")
    }
    val names = raw.split(",").map { it.trim().removePrefix(":") }
    val invalid = names.filter { it !in optedIn }
    if (invalid.isNotEmpty()) {
        val listed = invalid.joinToString(", ") { "'$it'" }
        throw GradleException("Invalid $property '$raw': unknown or empty module name $listed. Valid modules: $valid.")
    }
    return names.distinct().sorted()
}

internal fun parsePercent(property: String, raw: String?): BigDecimal? {
    if (raw == null) return null
    val value = raw.trim().toBigDecimalOrNull()
    if (value == null || value < BigDecimal.ZERO || value > BigDecimal(100)) {
        throw GradleException("Invalid $property '$raw': expected a number from 0 to 100.")
    }
    return value
}
