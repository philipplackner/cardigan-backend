package com.plcoding.cardigan.common.api

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class ProblemFieldErrorTest {
    private val jsonMapper = JsonMapper.builder().build()

    @Test
    fun `limits are written when set`() {
        val json =
            jsonMapper.writeValueAsString(
                ProblemFieldError("position", FieldErrorCode.OUT_OF_RANGE, min = 1, max = 5),
            )

        assertThat(jsonMapper.readTree(json))
            .isEqualTo(jsonMapper.readTree("""{"field":"position","code":"OUT_OF_RANGE","min":1,"max":5}"""))
    }

    @Test
    fun `limits are left out when not set`() {
        val json = jsonMapper.writeValueAsString(ProblemFieldError("name", FieldErrorCode.REQUIRED))

        assertThat(jsonMapper.readTree(json)).isEqualTo(jsonMapper.readTree("""{"field":"name","code":"REQUIRED"}"""))
    }
}
