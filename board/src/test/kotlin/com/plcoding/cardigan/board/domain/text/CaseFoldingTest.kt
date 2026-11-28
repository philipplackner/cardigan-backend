package com.plcoding.cardigan.board.domain.text

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotEqualTo
import org.junit.jupiter.api.Test
import java.util.Locale

class CaseFoldingTest {
    @Test
    fun `ASCII letters fold to lowercase`() {
        assertThat(foldCase("Bug")).isEqualTo(foldCase("bug"))
        assertThat(foldCase("Bug")).isEqualTo("bug")
    }

    @Test
    fun `sharp s folds to double s`() {
        assertThat(foldCase("Straße")).isEqualTo("strasse")
        assertThat(foldCase("STRASSE")).isEqualTo("strasse")
    }

    @Test
    fun `capital sharp s folds to double s`() {
        assertThat(foldCase("STRAẞE")).isEqualTo("strasse")
    }

    @Test
    fun `all three Greek sigmas fold to one`() {
        assertThat(foldCase("Σ")).isEqualTo("σ")
        assertThat(foldCase("ς")).isEqualTo("σ")
        assertThat(foldCase("ΟΔΟΣ")).isEqualTo(foldCase("οδος"))
    }

    @Test
    fun `Turkish letters fold by the default mappings in a Turkish locale`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        try {
            assertThat(foldCase("I")).isEqualTo("i")
            assertThat(foldCase("İ")).isEqualTo("i̇")
            assertThat(foldCase("TITLE")).isEqualTo("title")
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `dotless i stays different from i`() {
        assertThat(foldCase("ı")).isNotEqualTo(foldCase("I"))
    }

    @Test
    fun `ligature folds to three code points`() {
        val folded = foldCase("ﬃ")

        assertThat(folded).isEqualTo("ffi")
        assertThat(folded.codePointCount(0, folded.length)).isEqualTo(3)
    }

    @Test
    fun `folded text folds to itself`() {
        listOf("bug", "strasse", "σ", "i̇", "ffi", "äpfel", "😀", "").forEach {
            assertThat(foldCase(it), it).isEqualTo(it)
        }
    }

    @Test
    fun `characters without case are unchanged`() {
        assertThat(foldCase("12 😀 漢字 -_")).isEqualTo("12 😀 漢字 -_")
    }

    @Test
    fun `order follows code points of the folded names`() {
        val names = listOf("😀", "ｚ", "Äpfel", "Zebra", "apple")

        assertThat(names.sortedWith(compareBy(codePointOrder) { foldCase(it) }))
            .containsExactly("apple", "Zebra", "Äpfel", "ｚ", "😀")
    }

    @Test
    fun `supplementary character sorts after the BMP, unlike UTF-16 order`() {
        assertThat("😀".compareTo("ｚ")).isLessThan(0)
        assertThat(codePointOrder.compare("😀", "ｚ")).isGreaterThan(0)
        assertThat(codePointOrder.compare("a😀", "aｚ")).isGreaterThan(0)
    }

    @Test
    fun `two supplementary characters with the same high surrogate are ordered`() {
        assertThat(codePointOrder.compare("😀", "😁")).isLessThan(0)
    }

    @Test
    fun `prefix sorts first and equal strings compare equal`() {
        assertThat(codePointOrder.compare("bug", "bugs")).isLessThan(0)
        assertThat(codePointOrder.compare("bugs", "bug")).isGreaterThan(0)
        assertThat(codePointOrder.compare("", "a")).isLessThan(0)
        assertThat(codePointOrder.compare("😀", "😀")).isEqualTo(0)
    }
}
