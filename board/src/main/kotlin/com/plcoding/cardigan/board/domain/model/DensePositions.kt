package com.plcoding.cardigan.board.domain.model

import com.plcoding.cardigan.common.domain.exception.FieldOutOfRangeException
import java.math.BigInteger

/**
 * Returns [position] as an index into a container whose [count] items fill the positions 0 to
 * `count - 1` without gaps, such as the columns of a board. Throws
 * [FieldOutOfRangeException] for [field] with the limits 0 and `count - 1` when it lies outside.
 */
fun requireInRange(
    field: String,
    position: BigInteger,
    count: Int,
): Int {
    val max = count - 1
    if (position < BigInteger.ZERO || position > BigInteger.valueOf(max.toLong())) {
        throw FieldOutOfRangeException(field, min = 0, max = max.toLong())
    }
    return position.toInt()
}

/** The positions from [first] to [last], both included, that each change by [delta]. */
data class PositionShift(
    val first: Int,
    val last: Int,
    val delta: Int,
)

/**
 * The shift that makes room when an item moves from position [from] to [to]: the items between
 * them move one down for a move to the right and one up for a move to the left. The moved item
 * itself is not part of the range.
 */
fun shiftForMove(
    from: Int,
    to: Int,
): PositionShift {
    require(from != to) { "A move to the current position shifts nothing" }
    return if (to > from) {
        PositionShift(first = from + 1, last = to, delta = -1)
    } else {
        PositionShift(first = to, last = from - 1, delta = 1)
    }
}
