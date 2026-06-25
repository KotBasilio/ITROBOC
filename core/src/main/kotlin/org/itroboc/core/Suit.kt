package org.itroboc.core

enum class Suit(val symbol: Char) {
    SPADES('S'),
    HEARTS('H'),
    DIAMONDS('D'),
    CLUBS('C');

    val prettySymbol: String
        get() = when (this) {
            SPADES -> "♠"
            HEARTS -> "♥"
            DIAMONDS -> "♦"
            CLUBS -> "♣"
        }

    companion object {
        fun fromSymbol(symbol: Char): Suit =
            entries.firstOrNull { it.symbol == symbol }
                ?: throw IllegalArgumentException("Unknown suit symbol: $symbol")
    }
}
