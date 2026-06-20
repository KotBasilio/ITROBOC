package org.itroboc.core

data class CardId(val suit: Suit, val rank: Rank) {
    override fun toString(): String = "${suit.symbol}${rank.symbol}"

    companion object {
        fun parse(raw: String): CardId {
            require(raw.length == 2) {
                "Canonical card ID must be exactly 2 characters: $raw"
            }

            val normalized = raw.uppercase()
            return CardId(
                suit = Suit.fromSymbol(normalized[0]),
                rank = Rank.fromSymbol(normalized[1]),
            )
        }
    }
}
