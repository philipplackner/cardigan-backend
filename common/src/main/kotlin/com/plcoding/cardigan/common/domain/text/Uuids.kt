package com.plcoding.cardigan.common.domain.text

import java.util.UUID

private val canonicalUuid = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

/**
 * Parses a UUID in the canonical 8-4-4-4-12 form, in any letter case. Returns null for anything
 * else, including the short forms `UUID.fromString` accepts.
 */
fun parseCanonicalUuid(value: String): UUID? = if (canonicalUuid.matches(value)) UUID.fromString(value) else null
