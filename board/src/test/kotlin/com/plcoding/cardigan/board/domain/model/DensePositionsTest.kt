package com.plcoding.cardigan.board.domain.model

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import com.plcoding.cardigan.common.domain.exception.FieldOutOfRangeException
import org.junit.jupiter.api.Test
import java.math.BigInteger

class DensePositionsTest {

    private fun inRange(position: String, count: Int) = requireInRange("position", BigInteger(position), count)

    @Test
    fun `single item accepts only position 0`() {
        assertThat(inRange("0", count = 1)).isEqualTo(0)
        assertFailure { inRange("1", count = 1) }.isInstanceOf<FieldOutOfRangeException>().all0(max = 0)
    }

    @Test
    fun `three items accept 0 to 2`() {
        assertThat((0..2).map { inRange(it.toString(), count = 3) }).isEqualTo(listOf(0, 1, 2))
    }

    @Test
    fun `position equal to the count is out of range`() {
        assertFailure { inRange("3", count = 3) }.isInstanceOf<FieldOutOfRangeException>().all0(max = 2)
    }

    @Test
    fun `negative position is out of range`() {
        assertFailure { inRange("-1", count = 3) }.isInstanceOf<FieldOutOfRangeException>().all0(max = 2)
    }

    @Test
    fun `value beyond Long is out of range instead of overflowing`() {
        assertFailure { inRange("99999999999999999999", count = 3) }.isInstanceOf<FieldOutOfRangeException>().all0(max = 2)
        assertFailure { inRange("-99999999999999999999", count = 3) }.isInstanceOf<FieldOutOfRangeException>().all0(max = 2)
    }

    @Test
    fun `value that wraps to a valid Int is out of range`() {
        val wrapsToOne = BigInteger.TWO.pow(32).add(BigInteger.ONE)

        assertThat(wrapsToOne.toInt()).isEqualTo(1)
        assertFailure { requireInRange("position", wrapsToOne, 3) }.isInstanceOf<FieldOutOfRangeException>()
    }

    @Test
    fun `exception names the field`() {
        assertFailure { requireInRange("cards[2].position", BigInteger.TEN, 3) }
            .isInstanceOf<FieldOutOfRangeException>()
            .prop(FieldOutOfRangeException::field).isEqualTo("cards[2].position")
    }

    @Test
    fun `move right shifts the items in between one down`() {
        assertThat(shiftForMove(from = 1, to = 3)).isEqualTo(PositionShift(first = 2, last = 3, delta = -1))
    }

    @Test
    fun `move left shifts the items in between one up`() {
        assertThat(shiftForMove(from = 3, to = 1)).isEqualTo(PositionShift(first = 1, last = 2, delta = 1))
    }

    @Test
    fun `move to the first position shifts everything before the item`() {
        assertThat(shiftForMove(from = 4, to = 0)).isEqualTo(PositionShift(first = 0, last = 3, delta = 1))
    }

    @Test
    fun `move to the last position shifts everything after the item`() {
        assertThat(shiftForMove(from = 0, to = 4)).isEqualTo(PositionShift(first = 1, last = 4, delta = -1))
    }

    @Test
    fun `move by one shifts exactly the neighbour`() {
        assertThat(shiftForMove(from = 2, to = 3)).isEqualTo(PositionShift(first = 3, last = 3, delta = -1))
        assertThat(shiftForMove(from = 3, to = 2)).isEqualTo(PositionShift(first = 2, last = 2, delta = 1))
    }

    @Test
    fun `move to the current position is rejected`() {
        assertFailure { shiftForMove(from = 2, to = 2) }.isInstanceOf<IllegalArgumentException>()
    }

    private fun assertk.Assert<FieldOutOfRangeException>.all0(max: Long) {
        prop(FieldOutOfRangeException::min).isEqualTo(0L)
        prop(FieldOutOfRangeException::max).isEqualTo(max)
    }
}
