package org.itroboc.core

enum class Seat(val symbol: Char) {
    NORTH('N'),
    EAST('E'),
    SOUTH('S'),
    WEST('W');

    fun next(): Seat = when (this) {
        NORTH -> EAST
        EAST -> SOUTH
        SOUTH -> WEST
        WEST -> NORTH
    }
}
