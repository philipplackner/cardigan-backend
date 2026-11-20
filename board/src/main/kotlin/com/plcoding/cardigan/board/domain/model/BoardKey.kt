package com.plcoding.cardigan.board.domain.model

import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import com.plcoding.cardigan.common.domain.text.codePointLength
import com.plcoding.cardigan.common.domain.text.normalizeText

const val BOARD_KEY_MIN_LENGTH = 2
const val BOARD_KEY_MAX_LENGTH = 5

/**
 * Normalizes a board key as entered: `normalizeText`, then the ASCII letters `a` to `z` to
 * uppercase. Other letters are left alone, so `ä` stays `ä` and fails [checkBoardKey] instead of
 * turning into `Ä`.
 */
fun normalizeBoardKey(raw: String): String =
    buildString {
        normalizeText(raw).forEach { append(if (it in 'a'..'z') it.uppercaseChar() else it) }
    }

/**
 * Checks an already normalized key and returns the first broken rule, or null when the key is
 * valid. Length comes before the characters, and every wrong character is `INVALID_FORMAT`, control
 * characters and unpaired surrogates included. It never returns `INVALID_CHARACTER`.
 */
fun checkBoardKey(normalized: String): FieldErrorCode? {
    val length = codePointLength(normalized)
    return when {
        length == 0 -> FieldErrorCode.REQUIRED
        length < BOARD_KEY_MIN_LENGTH -> FieldErrorCode.TOO_SHORT
        length > BOARD_KEY_MAX_LENGTH -> FieldErrorCode.TOO_LONG
        normalized.any { it !in 'A'..'Z' } -> FieldErrorCode.INVALID_FORMAT
        else -> null
    }
}
