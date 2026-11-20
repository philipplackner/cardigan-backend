package com.plcoding.cardigan.board.domain.model

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import org.junit.jupiter.api.Test

class BoardKeyTest {

    private fun check(raw: String): FieldErrorCode? = checkBoardKey(normalizeBoardKey(raw))

    @Test
    fun `lowercase ASCII letters become uppercase`() {
        assertThat(normalizeBoardKey("crd")).isEqualTo("CRD")
        assertThat(check("crd")).isNull()
    }

    @Test
    fun `surrounding whitespace is removed, Unicode spaces included`() {
        assertThat(normalizeBoardKey(" \u00a0crd\u3000\t")).isEqualTo("CRD")
    }

    @Test
    fun `whitespace-only key is REQUIRED`() {
        assertThat(check("   ")).isEqualTo(FieldErrorCode.REQUIRED)
        assertThat(check("")).isEqualTo(FieldErrorCode.REQUIRED)
    }

    @Test
    fun `single letter is TOO_SHORT`() {
        assertThat(check("C")).isEqualTo(FieldErrorCode.TOO_SHORT)
    }

    @Test
    fun `single wrong character is TOO_SHORT, length before format`() {
        assertThat(check("1")).isEqualTo(FieldErrorCode.TOO_SHORT)
    }

    @Test
    fun `two and five letters are the valid limits`() {
        assertThat(check("AB")).isNull()
        assertThat(check("ABCDE")).isNull()
    }

    @Test
    fun `six letters are TOO_LONG`() {
        assertThat(check("ABCDEF")).isEqualTo(FieldErrorCode.TOO_LONG)
    }

    @Test
    fun `too long with a wrong character is TOO_LONG`() {
        assertThat(check("ÄBCDEF")).isEqualTo(FieldErrorCode.TOO_LONG)
    }

    @Test
    fun `digit is INVALID_FORMAT`() {
        assertThat(check("CR1")).isEqualTo(FieldErrorCode.INVALID_FORMAT)
    }

    @Test
    fun `space inside is INVALID_FORMAT`() {
        assertThat(check("C D")).isEqualTo(FieldErrorCode.INVALID_FORMAT)
    }

    @Test
    fun `tab inside is INVALID_FORMAT, not INVALID_CHARACTER`() {
        assertThat(check("C\tD")).isEqualTo(FieldErrorCode.INVALID_FORMAT)
    }

    @Test
    fun `uppercase non-ASCII letter is INVALID_FORMAT`() {
        assertThat(check("ÄB")).isEqualTo(FieldErrorCode.INVALID_FORMAT)
    }

    @Test
    fun `lowercase non-ASCII letter is not uppercased and is INVALID_FORMAT`() {
        assertThat(normalizeBoardKey("äb")).isEqualTo("äB")
        assertThat(check("äb")).isEqualTo(FieldErrorCode.INVALID_FORMAT)
    }

    @Test
    fun `letters that only uppercase to ASCII stay as they are`() {
        assertThat(normalizeBoardKey("ıſ")).isEqualTo("ıſ")
        assertThat(check("ıſ")).isEqualTo(FieldErrorCode.INVALID_FORMAT)
    }

    @Test
    fun `lone surrogate is INVALID_FORMAT`() {
        assertThat(check("C\ud800D")).isEqualTo(FieldErrorCode.INVALID_FORMAT)
    }

    @Test
    fun `emoji of two UTF-16 units in a key of five code points is INVALID_FORMAT, not TOO_LONG`() {
        val key = "AB\ud83d\ude00CD"

        assertThat(key.length).isEqualTo(6)
        assertThat(check(key)).isEqualTo(FieldErrorCode.INVALID_FORMAT)
    }

    @Test
    fun `decomposed letter counts as one code point after normalization`() {
        assertThat(check("A\u0308BCDE")).isEqualTo(FieldErrorCode.INVALID_FORMAT)
    }

    @Test
    fun `INVALID_CHARACTER is never returned`() {
        val inputs = listOf("\u0000", "A\u0000", "A\u0001B", "A\nB", "A\r\nB", "\ud800", "A\udc00B", "ABCDE\u0007", "\u001f\u001f")

        inputs.forEach { assertThat(check(it), it).isNotEqualTo(FieldErrorCode.INVALID_CHARACTER) }
    }
}
