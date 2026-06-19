package org.itroboc.core

data class TdScanAccumulator(
    val deckProfile: DeckProfile,
    val boardState: BoardState = BoardState(),
) {
    fun scan(seat: Seat, signature: String): TdScanReport {
        val card = deckProfile.lookup(signature)
            ?: return TdScanReport(this, TdScanResult.UnknownSignature(signature))

        val hand = boardState.handOf(seat)
        if (hand.contains(card)) {
            return TdScanReport(this, TdScanResult.AlreadyInThisHand(card))
        }

        if (hand.isComplete()) {
            return TdScanReport(this, TdScanResult.HandAlreadyComplete(seat))
        }

        val existingSeat = boardState.seatContaining(card)
        if (existingSeat != null) {
            return TdScanReport(this, TdScanResult.AlreadyOnBoard(card, existingSeat))
        }

        val nextAccumulator = copy(boardState = boardState.addCard(seat, card))
        return TdScanReport(nextAccumulator, TdScanResult.Added(card))
    }
}

data class TdScanReport(
    val accumulator: TdScanAccumulator,
    val result: TdScanResult,
)

sealed interface TdScanResult {
    data class Added(val card: CardId) : TdScanResult
    data class AlreadyInThisHand(val card: CardId) : TdScanResult
    data class AlreadyOnBoard(val card: CardId, val existingSeat: Seat) : TdScanResult
    data class UnknownSignature(val signature: String) : TdScanResult
    data class HandAlreadyComplete(val seat: Seat) : TdScanResult
}
