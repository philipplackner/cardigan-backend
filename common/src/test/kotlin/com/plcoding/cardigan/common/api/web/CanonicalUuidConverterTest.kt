package com.plcoding.cardigan.common.api.web

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.Test
import java.util.UUID

class CanonicalUuidConverterTest {
    private val converter = CanonicalUuidConverter()
    private val id = "0b6a1f43-58c2-4d0e-9a35-3f1c9e2b7d10"

    @Test
    fun `canonical lowercase ID converts`() {
        assertThat(converter.convert(id)).isEqualTo(UUID.fromString(id))
    }

    @Test
    fun `canonical uppercase ID converts to the same UUID`() {
        assertThat(converter.convert(id.uppercase())).isEqualTo(UUID.fromString(id))
    }

    @Test
    fun `text that is no UUID fails`() {
        assertFailure { converter.convert("abc") }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `short form that UUID fromString accepts fails`() {
        assertThat(UUID.fromString("1-2-3-4-5")).isEqualTo(UUID.fromString("00000001-0002-0003-0004-000000000005"))

        assertFailure { converter.convert("1-2-3-4-5") }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `ID with dropped leading zeros fails`() {
        assertFailure {
            converter.convert(
                "b6a1f43-58c2-4d0e-9a35-3f1c9e2b7d10",
            )
        }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `surrounding whitespace fails`() {
        assertFailure { converter.convert(" $id") }.isInstanceOf<IllegalArgumentException>()
    }
}
