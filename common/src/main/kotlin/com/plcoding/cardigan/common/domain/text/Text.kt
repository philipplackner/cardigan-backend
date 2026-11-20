package com.plcoding.cardigan.common.domain.text

import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import java.text.Normalizer

private val lineBreaks = Regex("\r\n?")

/**
 * Normalizes user-entered text as the shared `text-length` spec requires: CRLF and lone CR become
 * LF, then NFC, then leading and trailing Unicode White_Space is removed.
 */
fun normalizeText(text: String): String =
    Normalizer.normalize(text.replace(lineBreaks, "\n"), Normalizer.Form.NFC)
        .trim(::isWhiteSpace)

// A hand-written scan instead of a regex: `\p{IsWhite_Space}+$` takes quadratic time on a long inner run.
private fun isWhiteSpace(c: Char): Boolean = when (c) {
    in '\u0009'..'\u000D', ' ', '\u0085', '\u00A0', '\u1680', in '\u2000'..'\u200A',
    '\u2028', '\u2029', '\u202F', '\u205F', '\u3000' -> true
    else -> false
}

/** Normalizes a password to NFC. Whitespace is part of a password and is kept. */
fun normalizePassword(password: String): String = Normalizer.normalize(password, Normalizer.Form.NFC)

/** The length of [text] in Unicode code points, the unit of every length limit. */
fun codePointLength(text: String): Int = text.codePointCount(0, text.length)

/**
 * True when [text] holds an unpaired surrogate or a control character in U+0000–U+001F. Tab, LF and
 * CR are allowed only when [allowLineBreaks] is set. Passwords use `allowLineBreaks = false`.
 */
fun hasInvalidCharacter(text: String, allowLineBreaks: Boolean): Boolean {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            Character.isHighSurrogate(c) -> {
                if (i + 1 >= text.length || !Character.isLowSurrogate(text[i + 1])) return true
                i++
            }
            Character.isLowSurrogate(c) -> return true
            c < ' ' && !(allowLineBreaks && (c == '\t' || c == '\n' || c == '\r')) -> return true
        }
        i++
    }
    return false
}

/**
 * Checks already normalized [text] against the `text-length` rules and returns the first broken
 * rule in `api-errors` precedence, or null when the text is valid. Empty text is `REQUIRED` when
 * [min] is at least 1.
 */
fun checkText(text: String, min: Int, max: Int, multiline: Boolean): FieldErrorCode? {
    val length = codePointLength(text)
    return when {
        length == 0 && min >= 1 -> FieldErrorCode.REQUIRED
        hasInvalidCharacter(text, allowLineBreaks = multiline) -> FieldErrorCode.INVALID_CHARACTER
        length < min -> FieldErrorCode.TOO_SHORT
        length > max -> FieldErrorCode.TOO_LONG
        else -> null
    }
}
