package com.plcoding.cardigan.board.domain.text

import com.ibm.icu.lang.UCharacter

/**
 * Full Unicode default case folding, independent of any locale. Two texts are equal ignoring case
 * exactly when their folded forms are equal. Folding can turn one code point into up to three.
 */
fun foldCase(text: String): String = UCharacter.foldCase(text, true)

/**
 * Orders strings by their Unicode code points. `String.compareTo` compares UTF-16 code units, which
 * puts supplementary characters such as emoji before U+E000–U+FFFF.
 */
val codePointOrder: Comparator<String> = Comparator { a, b ->
    var i = 0
    while (i < a.length && i < b.length) {
        val left = a.codePointAt(i)
        val right = b.codePointAt(i)
        if (left != right) return@Comparator left.compareTo(right)
        i += Character.charCount(left)
    }
    a.length.compareTo(b.length)
}
