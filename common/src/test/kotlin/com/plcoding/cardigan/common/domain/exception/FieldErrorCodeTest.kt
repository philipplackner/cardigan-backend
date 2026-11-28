package com.plcoding.cardigan.common.domain.exception

import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.jupiter.api.Test

class FieldErrorCodeTest {
    @Test
    fun `declaration order is the shared precedence order`() {
        assertThat(FieldErrorCode.entries.map { it.name }).containsExactly(
            "REQUIRED",
            "INVALID_CHARACTER",
            "TOO_SHORT",
            "TOO_LONG",
            "INVALID_FORMAT",
            "PASSWORD_TOO_WEAK",
            "INVALID_VALUE",
            "OUT_OF_RANGE",
            "ALREADY_USED",
        )
    }
}
