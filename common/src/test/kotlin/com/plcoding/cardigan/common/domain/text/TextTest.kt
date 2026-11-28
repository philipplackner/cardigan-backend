package com.plcoding.cardigan.common.domain.text

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.ThrowingSupplier
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.text.Normalizer
import java.time.Duration

class TextTest {
    @Nested
    inner class NormalizeText {
        @Test
        fun `surrounding spaces are removed`() {
            assertThat(normalizeText("  Release ")).isEqualTo("Release")
        }

        @Test
        fun `whitespace-only text becomes empty`() {
            assertThat(normalizeText(" 　\n")).isEqualTo("")
        }

        @Test
        fun `CRLF and lone CR become LF`() {
            assertThat(normalizeText("a\r\nb\rc")).isEqualTo("a\nb\nc")
        }

        @Test
        fun `CR only text becomes empty`() {
            assertThat(normalizeText("\r\r")).isEqualTo("")
        }

        @Test
        fun `decomposed text is composed`() {
            assertThat(normalizeText("é")).isEqualTo("é")
        }

        @Test
        fun `NBSP and NEL around text are removed`() {
            assertThat(normalizeText(" \u0085a ")).isEqualTo("a")
        }

        @Test
        fun `inner whitespace is kept`() {
            assertThat(normalizeText(" a   b ")).isEqualTo("a   b")
        }

        @ParameterizedTest
        @ValueSource(chars = ['\u001c', '\u001d', '\u001e', '\u001f'])
        fun `separators U+001C to U+001F are not white space and stay`(c: Char) {
            assertThat(normalizeText(" a$c ")).isEqualTo("a$c")
        }

        @Test
        fun `zero-width space is not white space and stays`() {
            assertThat(normalizeText("​a")).isEqualTo("​a")
        }

        @Test
        fun `exactly the Unicode White_Space characters are removed`() {
            val whiteSpace = Regex("\\p{IsWhite_Space}")
            for (code in Char.MIN_VALUE.code..Char.MAX_VALUE.code) {
                val c = code.toChar()
                if (c.isSurrogate() || c == '\r') continue
                val text = "${c}a$c"
                val expected =
                    if (whiteSpace.matches(
                            c.toString(),
                        )
                    ) {
                        "a"
                    } else {
                        Normalizer.normalize(text, Normalizer.Form.NFC)
                    }
                assertThat(normalizeText(text), "U+%04X".format(code)).isEqualTo(expected)
            }
        }

        @Test
        fun `a long inner whitespace run is kept without quadratic work`() {
            val text = "a" + " ".repeat(250_000) + "b"

            val normalized =
                assertTimeoutPreemptively(Duration.ofSeconds(2), ThrowingSupplier { normalizeText(" $text ") })

            assertThat(normalized).isEqualTo(text)
        }

        @Test
        fun `lone surrogate survives normalization`() {
            assertThat(normalizeText(" a\ud83d ")).isEqualTo("a\ud83d")
        }
    }

    @Nested
    inner class NormalizePassword {
        @Test
        fun `surrounding spaces are kept`() {
            assertThat(normalizePassword(" Secret Pass 1 ")).isEqualTo(" Secret Pass 1 ")
        }

        @Test
        fun `decomposed and composed forms match`() {
            assertThat(normalizePassword("caf" + "é")).isEqualTo(normalizePassword("café"))
        }

        @Test
        fun `line breaks are not converted`() {
            assertThat(normalizePassword("a\r\nb")).isEqualTo("a\r\nb")
        }
    }

    @Nested
    inner class CodePointLength {
        @Test
        fun `emoji counts as one`() {
            assertThat(codePointLength("👍")).isEqualTo(1)
        }

        @Test
        fun `joined emoji counts each code point`() {
            assertThat(codePointLength("👨‍👩‍👧")).isEqualTo(5)
        }

        @Test
        fun `non-Latin script counts per code point`() {
            assertThat(codePointLength("看板")).isEqualTo(2)
        }

        @Test
        fun `line break counts as one`() {
            assertThat(codePointLength("a\nb")).isEqualTo(3)
        }
    }

    @Nested
    inner class InvalidCharacters {
        @ParameterizedTest
        @ValueSource(strings = ["a\tb", "a\nb", "a\rb", "\u0000", "a\u001fb", "a\u0001"])
        fun `control characters are invalid in single-line text`(text: String) {
            assertThat(hasInvalidCharacter(text, allowLineBreaks = false)).isTrue()
        }

        @ParameterizedTest
        @ValueSource(strings = ["a\tb", "a\nb", "a\rb"])
        fun `tab and line breaks are valid in multi-line text`(text: String) {
            assertThat(hasInvalidCharacter(text, allowLineBreaks = true)).isFalse()
        }

        @ParameterizedTest
        @ValueSource(strings = ["\u0000", "a\u000bb", "a\u001cb", "a\u001fb"])
        fun `other control characters are invalid in multi-line text`(text: String) {
            assertThat(hasInvalidCharacter(text, allowLineBreaks = true)).isTrue()
        }

        @ParameterizedTest
        @ValueSource(strings = ["\ud83d", "a\ud83d", "\ud83da", "\ude00", "a\ude00b", "\ude00\ud83d"])
        fun `unpaired surrogates are invalid`(text: String) {
            assertThat(hasInvalidCharacter(text, allowLineBreaks = true)).isTrue()
        }

        @ParameterizedTest
        @ValueSource(strings = ["a b", "😀", "\u007f", "\u0085", " ", "看板"])
        fun `printable text, surrogate pairs and characters above U+001F are valid`(text: String) {
            assertThat(hasInvalidCharacter(text, allowLineBreaks = false)).isFalse()
        }
    }

    @Nested
    inner class CheckText {
        private fun check(
            raw: String,
            min: Int = 0,
            max: Int = 50,
            multiline: Boolean = false,
        ) = checkText(normalizeText(raw), min, max, multiline)

        @Test
        fun `whitespace-only text is REQUIRED when a minimum applies`() {
            assertThat(check("   ", min = 1)).isEqualTo(FieldErrorCode.REQUIRED)
        }

        @Test
        fun `empty text is valid without a minimum`() {
            assertThat(check("", min = 0)).isNull()
        }

        @Test
        fun `length at the maximum is valid`() {
            assertThat(check("a".repeat(50))).isNull()
        }

        @Test
        fun `one over the maximum is TOO_LONG`() {
            assertThat(check("a".repeat(51))).isEqualTo(FieldErrorCode.TOO_LONG)
        }

        @Test
        fun `maximum reached only after trimming is valid`() {
            assertThat(check("  " + "a".repeat(50) + "  ")).isNull()
        }

        @Test
        fun `length at the minimum is valid`() {
            assertThat(check("ab", min = 2)).isNull()
        }

        @Test
        fun `one under the minimum is TOO_SHORT`() {
            assertThat(check("a", min = 2)).isEqualTo(FieldErrorCode.TOO_SHORT)
        }

        @Test
        fun `emoji at the maximum counts per code point`() {
            assertThat(check("👍".repeat(50))).isNull()
            assertThat(check("👍".repeat(51))).isEqualTo(FieldErrorCode.TOO_LONG)
        }

        @Test
        fun `decomposed text is counted after composing`() {
            assertThat(check("é".repeat(50))).isNull()
        }

        @Test
        fun `invalid character wins over TOO_LONG`() {
            assertThat(check("a\tb" + "c".repeat(60))).isEqualTo(FieldErrorCode.INVALID_CHARACTER)
        }

        @Test
        fun `invalid character wins over TOO_SHORT`() {
            assertThat(check("\u0001", min = 2)).isEqualTo(FieldErrorCode.INVALID_CHARACTER)
        }

        @Test
        fun `NUL is invalid even in multi-line text`() {
            assertThat(check("a\u0000b", multiline = true)).isEqualTo(FieldErrorCode.INVALID_CHARACTER)
        }

        @Test
        fun `line breaks are valid in multi-line text and count once`() {
            assertThat(check("a\r\nb", max = 3, multiline = true)).isNull()
        }

        @Test
        fun `line break inside single-line text is invalid`() {
            assertThat(check("a\nb")).isEqualTo(FieldErrorCode.INVALID_CHARACTER)
        }

        @Test
        fun `lone surrogate is invalid`() {
            assertThat(check("a\ud83d")).isEqualTo(FieldErrorCode.INVALID_CHARACTER)
        }
    }

    @Nested
    inner class CanonicalUuid {
        @ParameterizedTest
        @ValueSource(strings = ["0b6a1f43-58c2-4d0e-9a35-3f1c9e2b7d10", "0B6A1F43-58C2-4D0E-9A35-3F1C9E2B7D10"])
        fun `canonical UUIDs in any case parse`(value: String) {
            assertThat(parseCanonicalUuid(value).toString()).isEqualTo("0b6a1f43-58c2-4d0e-9a35-3f1c9e2b7d10")
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "nope",
                "1-1-1-1-1",
                "0b6a1f4358c24d0e9a353f1c9e2b7d10",
                "0b6a1f43-58c2-4d0e-9a35-3f1c9e2b7d1g",
                "",
            ],
        )
        fun `other strings don't parse`(value: String) {
            assertThat(parseCanonicalUuid(value)).isNull()
        }
    }
}
