package org.itroboc.core

data class HandState(private val cards: Set<CardId> = emptySet()) {
    fun addCard(card: CardId): HandState {
        require(card !in cards) { "Card already present in hand: $card" }
        return HandState(cards + card)
    }

    fun contains(card: CardId): Boolean = card in cards

    fun count(): Int = cards.size

    fun isComplete(): Boolean = cards.size == FULL_HAND_SIZE

    fun cards(): Set<CardId> = cards

    fun validateComplete() {
        require(isComplete()) {
            "A bridge hand must contain $FULL_HAND_SIZE cards, found ${cards.size}"
        }
    }

    internal fun toPbnHand(): String =
        Suit.entries.joinToString(".") { suit ->
            cards
                .asSequence()
                .filter { it.suit == suit }
                .sortedBy { it.rank.pbnOrder }
                .joinToString(separator = "") { it.rank.symbol.toString() }
        }

    companion object {
        const val FULL_HAND_SIZE: Int = 13
    }
}
