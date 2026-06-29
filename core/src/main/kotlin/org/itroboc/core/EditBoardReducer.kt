package org.itroboc.core

data class EditBoardUpdate(
    val state: BoardEditState,
    val message: String? = null,
    val lastScannedCard: CardId? = null,
)

object EditBoardReducer {

    fun applyScannedCard(
        editState: BoardEditState,
        card: CardId,
        signature: String
    ): EditBoardUpdate {
        val boardState = editState.boardState
        val selectedSeat = editState.selectedSeat

        // 1. Basic TdScanAccumulator logic (duplicate check, hand full check)
        val hand = boardState.handOf(selectedSeat)
        if (hand.contains(card)) {
            return EditBoardUpdate(
                state = editState,
                message = "OK in ${selectedSeat.displayName}: $card.",
                lastScannedCard = card
            )
        }

        val existingSeat = boardState.seatContaining(card)
        if (existingSeat != null) {
            return EditBoardUpdate(
                state = editState,
                message = "Skipped: $card -- ${existingSeat.displayName} has it.",
                lastScannedCard = card
            )
        }

        if (hand.isComplete()) {
            return EditBoardUpdate(
                state = editState,
                message = "Hand ${selectedSeat.displayName} already complete."
            )
        }

        // 2. Add card
        val updatedBoard = boardState.addCard(selectedSeat, card)
        val isHandCompleteNow = updatedBoard.handOf(selectedSeat).isComplete()

        // 3. Auto-advance
        var nextSeat = selectedSeat
        var autoAdvanceMessage = ""
        if (isHandCompleteNow) {
            nextSeat = selectedSeat.next()
            autoAdvanceMessage = " Auto-advancing to ${nextSeat.displayName}."
        }

        val baseUpdate = editState.copy(
            boardState = updatedBoard,
            selectedSeat = nextSeat
        )

        // 4. Auto-fill (if we advanced or if we are just starting on a seat)
        val finalizedUpdate = tryAutoFillFourthHand(baseUpdate)
        
        val finalMessage = if (finalizedUpdate.state.boardState.totalCardCount() == BoardState.FULL_BOARD_SIZE && finalizedUpdate.message != null) {
            // Auto-fill happened
            "Added $card to ${selectedSeat.displayName} via $signature.$autoAdvanceMessage ${finalizedUpdate.message}"
        } else {
            "Added $card to ${selectedSeat.displayName} via $signature.$autoAdvanceMessage"
        }

        return finalizedUpdate.copy(
            message = finalMessage,
            lastScannedCard = card
        )
    }

    fun clearSelectedHand(editState: BoardEditState): EditBoardUpdate {
        val newHands = Seat.entries.associateWith { seat ->
            if (seat == editState.selectedSeat) HandState() else editState.boardState.handOf(seat)
        }
        return EditBoardUpdate(
            state = editState.copy(boardState = BoardState(newHands)),
            message = "Cleared ${editState.selectedSeat.displayName}."
        )
    }

    fun clearBoard(editState: BoardEditState): EditBoardUpdate {
        return EditBoardUpdate(
            state = editState.copy(boardState = BoardState()),
            message = "Cleared board ${editState.boardNumber}."
        )
    }

    fun tryAutoFillFourthHand(editState: BoardEditState): EditBoardUpdate {
        val boardState = editState.boardState
        val seat = editState.selectedSeat
        
        val selectedHand = boardState.handOf(seat)
        val otherSeats = Seat.entries.filter { it != seat }
        val otherHandsComplete = otherSeats.all { boardState.handOf(it).isComplete() }

        if (selectedHand.count() == 0 && otherHandsComplete) {
            val allCards = Suit.entries.flatMap { s -> Rank.entries.map { r -> CardId(s, r) } }.toSet()
            val assignedCards = boardState.allCards()
            val remainingCards = allCards - assignedCards

            if (remainingCards.size == 13) {
                var autoFilledBoard = boardState
                remainingCards.forEach { card ->
                    autoFilledBoard = autoFilledBoard.addCard(seat, card)
                }
                return EditBoardUpdate(
                    state = editState.copy(boardState = autoFilledBoard),
                    message = "${seat.displayName} auto-filled from remaining 13 cards. Board complete."
                )
            }
        }
        return EditBoardUpdate(state = editState)
    }
}
