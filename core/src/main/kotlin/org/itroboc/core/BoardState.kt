package org.itroboc.core

data class BoardState(
    private val hands: Map<Seat, HandState> = Seat.entries.associateWith { HandState() },
) {
    init {
        require(hands.keys == Seat.entries.toSet()) {
            "Board state must contain all four seats"
        }
    }

    fun handOf(seat: Seat): HandState = hands.getValue(seat)

    fun addCard(seat: Seat, card: CardId): BoardState {
        require(allCards().none { it == card }) { "Card already present on board: $card" }
        return copy(hands = hands + (seat to hands.getValue(seat).addCard(card)))
    }

    fun totalCardCount(): Int = allCards().size

    fun seatContaining(card: CardId): Seat? =
        Seat.entries.firstOrNull { seat -> handOf(seat).contains(card) }

    fun validateComplete() {
        Seat.entries.forEach { seat ->
            handOf(seat).validateComplete()
        }
        require(allCards().size == FULL_BOARD_SIZE) {
            "A complete board must contain $FULL_BOARD_SIZE unique cards"
        }
    }

    internal fun handsInPbnOrder(startSeat: Seat = Seat.NORTH): List<HandState> {
        val orderedSeats = generateSequence(startSeat) { seat -> seat.next() }
            .take(Seat.entries.size)
            .toList()
        return orderedSeats.map(::handOf)
    }

    private fun allCards(): Set<CardId> = hands.values.flatMap { it.cards() }.toSet()

    companion object {
        const val FULL_BOARD_SIZE: Int = 52
    }
}
