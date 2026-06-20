package org.itroboc.core

enum class Rank(val symbol: Char, val pbnOrder: Int) {
    ACE('A', 0),
    KING('K', 1),
    QUEEN('Q', 2),
    JACK('J', 3),
    TEN('T', 4),
    NINE('9', 5),
    EIGHT('8', 6),
    SEVEN('7', 7),
    SIX('6', 8),
    FIVE('5', 9),
    FOUR('4', 10),
    THREE('3', 11),
    TWO('2', 12);

    companion object {
        fun fromSymbol(symbol: Char): Rank =
            entries.firstOrNull { it.symbol == symbol }
                ?: throw IllegalArgumentException("Unknown rank symbol: $symbol")
    }
}
