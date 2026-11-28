package com.plcoding.cardigan

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.startsWith
import com.plcoding.cardigan.common.domain.exception.ErrorCode

/**
 * Asserts a problem-details response with exactly the documented fields, the fixed texts of [code]
 * and its status. Returns the parsed body.
 */
fun HttpResult.assertProblem(code: ErrorCode): Map<String, Any?> {
    assertThat(status, "status").isEqualTo(code.status)
    assertThat(contentType, "content type").isNotNull().startsWith("application/problem+json")
    val body = json()
    assertThat(
        body.keys,
        "fields",
    ).containsExactlyInAnyOrder("type", "title", "status", "detail", "code", "fieldErrors")
    assertThat(body["code"], "code").isEqualTo(code.name)
    assertThat(body["status"], "status field").isEqualTo(code.status)
    assertThat(body["type"], "type").isEqualTo(code.type)
    assertThat(body["title"], "title").isEqualTo(code.title)
    assertThat(body["detail"], "detail").isEqualTo(code.detail)
    return body
}

@Suppress("UNCHECKED_CAST")
val Map<String, Any?>.fieldErrors: List<Map<String, Any?>>
    get() = this["fieldErrors"] as List<Map<String, Any?>>
