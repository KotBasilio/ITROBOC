package org.itroboc.core

enum class Suit(val symbol: Char) {
    SPADES('S'),
    HEARTS('H'),
    DIAMONDS('D'),
    CLUBS('C');

    companion object {
        fun fromSymbol(symbol: Char): Suit =
            entries.firstOrNull { it.symbol == symbol }
                ?: throw IllegalArgumentException("Unknown suit symbol: $symbol")
    }
}
