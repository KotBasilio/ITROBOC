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
                state = editState.copy(duplicateOverrideCandidate = null),
                message = "OK in ${selectedSeat.displayName}: $card.",
                lastScannedCard = card
            )
        }

        val existingSeat = boardState.seatContaining(card)
        if (existingSeat != null) {
            return EditBoardUpdate(
                state = editState.copy(
                    duplicateOverrideCandidate = DuplicateOverrideCandidate(
                        card = card,
                        signature = signature,
                        existingSeat = existingSeat,
                        targetSeat = selectedSeat
                    )
                ),
                message = "Skipped: $card -- ${existingSeat.displayName} has it. Use I'm sure to move it to ${selectedSeat.displayName}.",
                lastScannedCard = card
            )
        }

        if (hand.isComplete()) {
            val nextSeat = selectedSeat.next()
            return EditBoardUpdate(
                state = editState.copy(selectedSeat = nextSeat),
                message = "Hand ${selectedSeat.displayName} already complete. Auto-advancing to ${nextSeat.displayName}."
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
            selectedSeat = nextSeat,
            addHistory = editState.addHistory + AddedCardRecord(selectedSeat, card),
            duplicateOverrideCandidate = null
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

    fun undoAddForSelectedHand(editState: BoardEditState): EditBoardUpdate {
        val lastInHand = editState.addHistory.lastOrNull { it.seat == editState.selectedSeat }
            ?: return EditBoardUpdate(editState, "Nothing to undo for ${editState.selectedSeat.displayName}.")

        val updatedBoard = editState.boardState.removeCard(lastInHand.seat, lastInHand.card)
        val updatedHistory = editState.addHistory.toMutableList().apply {
            removeAt(lastIndexOf(lastInHand))
        }

        return EditBoardUpdate(
            state = editState.copy(
                boardState = updatedBoard,
                addHistory = updatedHistory,
                duplicateOverrideCandidate = null
            ),
            message = "Undid: Removed ${lastInHand.card} from ${lastInHand.seat.displayName}."
        )
    }

    fun removeCardFromSelectedHand(editState: BoardEditState, card: CardId): EditBoardUpdate {
        if (!editState.boardState.handOf(editState.selectedSeat).contains(card)) {
            return EditBoardUpdate(editState, "Card $card not in ${editState.selectedSeat.displayName}.")
        }

        val updatedBoard = editState.boardState.removeCard(editState.selectedSeat, card)
        val updatedHistory = editState.addHistory.filterNot { it.seat == editState.selectedSeat && it.card == card }

        return EditBoardUpdate(
            state = editState.copy(
                boardState = updatedBoard,
                addHistory = updatedHistory,
                duplicateOverrideCandidate = null
            ),
            message = "Removed $card from ${editState.selectedSeat.displayName}."
        )
    }

    fun swapSelectedHandWith(editState: BoardEditState, targetSeat: Seat): EditBoardUpdate {
        val updatedBoard = editState.boardState.swapHands(editState.selectedSeat, targetSeat)
        return EditBoardUpdate(
            state = editState.copy(
                boardState = updatedBoard,
                addHistory = emptyList(), // Clear history as per plan
                duplicateOverrideCandidate = null
            ),
            message = "Swapped ${editState.selectedSeat.displayName} and ${targetSeat.displayName} hands."
        )
    }

    fun confirmDuplicateOverride(editState: BoardEditState): EditBoardUpdate {
        val candidate = editState.duplicateOverrideCandidate
            ?: return EditBoardUpdate(editState, "No duplicate override pending.")

        val isStale = !editState.boardState.handOf(candidate.existingSeat).contains(candidate.card) ||
                editState.boardState.handOf(candidate.targetSeat).contains(candidate.card) ||
                editState.boardState.handOf(candidate.targetSeat).isComplete()

        if (isStale) {
            return EditBoardUpdate(
                state = editState.copy(duplicateOverrideCandidate = null),
                message = "No longer actionable: duplicate context changed."
            )
        }

        val updatedBoard = editState.boardState
            .removeCard(candidate.existingSeat, candidate.card)
            .addCard(candidate.targetSeat, candidate.card)

        val updatedHistory = editState.addHistory.filterNot { 
            it.seat == candidate.existingSeat && it.card == candidate.card 
        } + AddedCardRecord(candidate.targetSeat, candidate.card)

        return EditBoardUpdate(
            state = editState.copy(
                boardState = updatedBoard,
                addHistory = updatedHistory,
                duplicateOverrideCandidate = null
            ),
            message = "Moved ${candidate.card} from ${candidate.existingSeat.displayName} to ${candidate.targetSeat.displayName}. Marked previous ${candidate.existingSeat.displayName} hit as false positive."
        )
    }

    fun clearSelectedHand(editState: BoardEditState): EditBoardUpdate {
        val newHands = Seat.entries.associateWith { seat ->
            if (seat == editState.selectedSeat) HandState() else editState.boardState.handOf(seat)
        }
        val updatedHistory = editState.addHistory.filterNot { it.seat == editState.selectedSeat }
        return EditBoardUpdate(
            state = editState.copy(
                boardState = BoardState(newHands),
                addHistory = updatedHistory,
                duplicateOverrideCandidate = null
            ),
            message = "Cleared ${editState.selectedSeat.displayName}."
        )
    }

    fun clearBoard(editState: BoardEditState): EditBoardUpdate {
        return EditBoardUpdate(
            state = editState.copy(
                boardState = BoardState(),
                addHistory = emptyList(),
                duplicateOverrideCandidate = null
            ),
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
