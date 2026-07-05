package org.itroboc.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EditBoardReducerTest {

    @Test
    fun `adding a new card to selected incomplete hand succeeds`() {
        val state = BoardEditState(boardNumber = 1, selectedSeat = Seat.NORTH)
        val card = CardId(Suit.SPADES, Rank.ACE)
        val update = EditBoardReducer.applyScannedCard(state, card, "sig1")

        assertEquals(1, update.state.boardState.handOf(Seat.NORTH).count())
        assertEquals(card, update.lastScannedCard)
        assertNotNull(update.message)
    }

    @Test
    fun `adding a card already in selected hand does nothing`() {
        val board = BoardState().addCard(Seat.NORTH, CardId(Suit.SPADES, Rank.ACE))
        val state = BoardEditState(boardNumber = 1, boardState = board, selectedSeat = Seat.NORTH)
        val card = CardId(Suit.SPADES, Rank.ACE)
        val update = EditBoardReducer.applyScannedCard(state, card, "sig1")

        assertEquals(1, update.state.boardState.totalCardCount())
        assertEquals(state, update.state)
        assertEquals(card, update.lastScannedCard)
    }

    @Test
    fun `adding a card already in another hand does nothing but sets candidate`() {
        val board = BoardState().addCard(Seat.EAST, CardId(Suit.SPADES, Rank.ACE))
        val state = BoardEditState(boardNumber = 1, boardState = board, selectedSeat = Seat.NORTH)
        val card = CardId(Suit.SPADES, Rank.ACE)
        val update = EditBoardReducer.applyScannedCard(state, card, "sig1")

        assertEquals(1, update.state.boardState.totalCardCount())
        assertEquals(card, update.state.duplicateOverrideCandidate?.card)
    }

    @Test
    fun `adding to a complete hand keeps cards intact and auto-advances`() {
        var board = BoardState()
        val cards = Rank.entries.map { CardId(Suit.SPADES, it) }
        cards.forEach { board = board.addCard(Seat.NORTH, it) }
        
        val state = BoardEditState(boardNumber = 1, boardState = board, selectedSeat = Seat.NORTH)
        val newCard = CardId(Suit.HEARTS, Rank.ACE)
        val update = EditBoardReducer.applyScannedCard(state, newCard, "sig2")

        assertEquals(13, update.state.boardState.totalCardCount())
        assertEquals(Seat.EAST, update.state.selectedSeat)
        assertEquals(board, update.state.boardState)
        assertEquals("Hand North already complete.\nAuto-advancing to East.", update.message)
    }

    @Test
    fun `auto-advance works after 13th card`() {
        var board = BoardState()
        val cards = Rank.entries.drop(1).map { CardId(Suit.SPADES, it) } // 12 cards
        cards.forEach { board = board.addCard(Seat.NORTH, it) }

        val state = BoardEditState(boardNumber = 1, boardState = board, selectedSeat = Seat.NORTH)
        val lastCard = CardId(Suit.SPADES, Rank.ACE)
        val update = EditBoardReducer.applyScannedCard(state, lastCard, "sig-last")

        assertEquals(13, update.state.boardState.handOf(Seat.NORTH).count())
        assertEquals(Seat.EAST, update.state.selectedSeat)
    }

    @Test
    fun `auto-fill works for fourth hand`() {
        var board = BoardState()
        // Fill North, East, South
        Suit.entries.take(3).forEachIndexed { suitIdx, suit ->
            val seat = Seat.entries[suitIdx]
            Rank.entries.forEach { rank ->
                board = board.addCard(seat, CardId(suit, rank))
            }
        }

        val state = BoardEditState(boardNumber = 1, boardState = board, selectedSeat = Seat.WEST)
        val update = EditBoardReducer.tryAutoFillFourthHand(state)

        assertEquals(52, update.state.boardState.totalCardCount())
        assertEquals(13, update.state.boardState.handOf(Seat.WEST).count())
        assertNotNull(update.message)
    }

    @Test
    fun `auto-fill completes a partial fourth hand from remaining cards`() {
        var board = BoardState()
        // Fill North, East, South
        Suit.entries.take(3).forEachIndexed { suitIdx, suit ->
            val seat = Seat.entries[suitIdx]
            Rank.entries.forEach { rank ->
                board = board.addCard(seat, CardId(suit, rank))
            }
        }
        board = board.addCard(Seat.WEST, CardId(Suit.CLUBS, Rank.ACE))

        val state = BoardEditState(boardNumber = 1, boardState = board, selectedSeat = Seat.WEST)
        val update = EditBoardReducer.tryAutoFillFourthHand(state)

        assertEquals(52, update.state.boardState.totalCardCount())
        assertEquals(13, update.state.boardState.handOf(Seat.WEST).count())
        assertEquals("West auto-filled from remaining 12 cards.\nBoard complete.", update.message)
    }

    @Test
    fun `auto-fill does not run when selected hand is already complete`() {
        var board = BoardState()
        Suit.entries.forEachIndexed { suitIdx, suit ->
            val seat = Seat.entries[suitIdx]
            Rank.entries.forEach { rank ->
                board = board.addCard(seat, CardId(suit, rank))
            }
        }

        val state = BoardEditState(boardNumber = 1, boardState = board, selectedSeat = Seat.WEST)
        val update = EditBoardReducer.tryAutoFillFourthHand(state)

        assertEquals(state, update.state)
        assertEquals(null, update.message)
    }

    @Test
    fun `clear hand clears selected hand only`() {
        var board = BoardState()
        board = board.addCard(Seat.NORTH, CardId(Suit.SPADES, Rank.ACE))
        board = board.addCard(Seat.EAST, CardId(Suit.HEARTS, Rank.ACE))
        
        val state = BoardEditState(boardNumber = 1, boardState = board, selectedSeat = Seat.NORTH)
        val update = EditBoardReducer.clearSelectedHand(state)

        assertEquals(0, update.state.boardState.handOf(Seat.NORTH).count())
        assertEquals(1, update.state.boardState.handOf(Seat.EAST).count())
    }

    @Test
    fun `clear board clears everything`() {
        var board = BoardState()
        board = board.addCard(Seat.NORTH, CardId(Suit.SPADES, Rank.ACE))
        
        val state = BoardEditState(boardNumber = 1, boardState = board, selectedSeat = Seat.NORTH)
        val update = EditBoardReducer.clearBoard(state)

        assertEquals(0, update.state.boardState.totalCardCount())
    }

    @Test
    fun `adding a card already in another hand sets duplicate override candidate`() {
        val board = BoardState().addCard(Seat.EAST, CardId(Suit.SPADES, Rank.ACE))
        val state = BoardEditState(boardNumber = 1, boardState = board, selectedSeat = Seat.NORTH)
        val card = CardId(Suit.SPADES, Rank.ACE)
        val update = EditBoardReducer.applyScannedCard(state, card, "sig1")

        assertEquals(1, update.state.boardState.totalCardCount())
        assertNotNull(update.state.duplicateOverrideCandidate)
        assertEquals(card, update.state.duplicateOverrideCandidate?.card)
        assertEquals(Seat.EAST, update.state.duplicateOverrideCandidate?.existingSeat)
        assertEquals(Seat.NORTH, update.state.duplicateOverrideCandidate?.targetSeat)
    }

    @Test
    fun `confirming duplicate override moves card and updates history`() {
        val card = CardId(Suit.SPADES, Rank.ACE)
        val board = BoardState().addCard(Seat.EAST, card)
        val state = BoardEditState(
            boardNumber = 1,
            boardState = board,
            selectedSeat = Seat.NORTH,
            duplicateOverrideCandidate = DuplicateOverrideCandidate(card, "sig1", Seat.EAST, Seat.NORTH)
        )
        
        val update = EditBoardReducer.confirmDuplicateOverride(state)

        assertEquals(1, update.state.boardState.totalCardCount())
        assertEquals(Seat.NORTH, update.state.boardState.seatContaining(card))
        assertEquals(1, update.state.addHistory.size)
        assertEquals(card, update.state.addHistory[0].card)
    }

    @Test
    fun `undo remove last added card for selected hand`() {
        val card1 = CardId(Suit.SPADES, Rank.ACE)
        val card2 = CardId(Suit.HEARTS, Rank.KING)
        val state = BoardEditState(boardNumber = 1, selectedSeat = Seat.NORTH)
        
        val update1 = EditBoardReducer.applyScannedCard(state, card1, "sig1")
        val update2 = EditBoardReducer.applyScannedCard(update1.state, card2, "sig2")
        
        val undoUpdate = EditBoardReducer.undoAddForSelectedHand(update2.state)
        
        assertEquals(1, undoUpdate.state.boardState.handOf(Seat.NORTH).count())
        assertEquals(card1, undoUpdate.state.boardState.handOf(Seat.NORTH).cards().first())
        assertEquals(1, undoUpdate.state.addHistory.size)
    }

    @Test
    fun `remove card from selected hand works`() {
        val card1 = CardId(Suit.SPADES, Rank.ACE)
        val card2 = CardId(Suit.HEARTS, Rank.KING)
        val state = BoardEditState(boardNumber = 1, selectedSeat = Seat.NORTH)
        
        val update1 = EditBoardReducer.applyScannedCard(state, card1, "sig1")
        val update2 = EditBoardReducer.applyScannedCard(update1.state, card2, "sig2")
        
        val removeUpdate = EditBoardReducer.removeCardFromSelectedHand(update2.state, card1)
        
        assertEquals(1, removeUpdate.state.boardState.handOf(Seat.NORTH).count())
        assertEquals(card2, removeUpdate.state.boardState.handOf(Seat.NORTH).cards().first())
    }

    @Test
    fun `manual add inserts unassigned card into selected hand`() {
        val card = CardId(Suit.SPADES, Rank.ACE)
        val state = BoardEditState(boardNumber = 1, selectedSeat = Seat.NORTH)

        val update = EditBoardReducer.addManualCardToSelectedHand(state, card)

        assertEquals(true, update.state.boardState.handOf(Seat.NORTH).contains(card))
        assertEquals(1, update.state.addHistory.size)
        assertEquals(card, update.lastScannedCard)
        assertEquals("Added $card to North manually.", update.message)
    }

    @Test
    fun `manual add clears pending duplicate candidate`() {
        val existingCandidateCard = CardId(Suit.SPADES, Rank.ACE)
        val addedCard = CardId(Suit.HEARTS, Rank.KING)
        val state = BoardEditState(
            boardNumber = 1,
            selectedSeat = Seat.NORTH,
            duplicateOverrideCandidate = DuplicateOverrideCandidate(
                card = existingCandidateCard,
                signature = "sig1",
                existingSeat = Seat.EAST,
                targetSeat = Seat.NORTH,
            ),
        )

        val update = EditBoardReducer.addManualCardToSelectedHand(state, addedCard)

        assertEquals(true, update.state.boardState.handOf(Seat.NORTH).contains(addedCard))
        assertEquals(listOf(AddedCardRecord(Seat.NORTH, addedCard)), update.state.addHistory)
        assertEquals(null, update.state.duplicateOverrideCandidate)
    }

    @Test
    fun `manual add does nothing when card already in selected hand`() {
        val card = CardId(Suit.SPADES, Rank.ACE)
        val state = BoardEditState(
            boardNumber = 1,
            boardState = BoardState().addCard(Seat.NORTH, card),
            selectedSeat = Seat.NORTH,
        )

        val update = EditBoardReducer.addManualCardToSelectedHand(state, card)

        assertEquals(state.boardState, update.state.boardState)
        assertEquals("$card already in North.", update.message)
        assertEquals(card, update.lastScannedCard)
    }

    @Test
    fun `manual add moves card from another hand immediately`() {
        val card = CardId(Suit.SPADES, Rank.ACE)
        val state = BoardEditState(
            boardNumber = 1,
            boardState = BoardState().addCard(Seat.EAST, card),
            selectedSeat = Seat.NORTH,
            addHistory = listOf(AddedCardRecord(Seat.EAST, card)),
            duplicateOverrideCandidate = DuplicateOverrideCandidate(card, "sig1", Seat.EAST, Seat.NORTH),
        )

        val update = EditBoardReducer.addManualCardToSelectedHand(state, card)

        assertEquals(Seat.NORTH, update.state.boardState.seatContaining(card))
        assertEquals(listOf(AddedCardRecord(Seat.NORTH, card)), update.state.addHistory)
        assertEquals(null, update.state.duplicateOverrideCandidate)
        assertEquals("Moved $card from East to North.", update.message)
        assertEquals(card, update.lastScannedCard)
    }

    @Test
    fun `manual add blocks when selected hand already has 13 cards`() {
        var board = BoardState()
        Rank.entries.forEach { rank ->
            board = board.addCard(Seat.NORTH, CardId(Suit.SPADES, rank))
        }
        val state = BoardEditState(boardNumber = 1, boardState = board, selectedSeat = Seat.NORTH)

        val update = EditBoardReducer.addManualCardToSelectedHand(state, CardId(Suit.HEARTS, Rank.ACE))

        assertEquals(board, update.state.boardState)
        assertEquals(Seat.NORTH, update.state.selectedSeat)
        assertEquals("North already has 13 cards.\nRemove one first.", update.message)
    }

    @Test
    fun `manual remove after manual add removes matching history`() {
        val card = CardId(Suit.SPADES, Rank.ACE)
        val state = BoardEditState(boardNumber = 1, selectedSeat = Seat.NORTH)

        val addUpdate = EditBoardReducer.addManualCardToSelectedHand(state, card)
        val removeUpdate = EditBoardReducer.removeCardFromSelectedHand(addUpdate.state, card)

        assertEquals(false, removeUpdate.state.boardState.handOf(Seat.NORTH).contains(card))
        assertEquals(emptyList(), removeUpdate.state.addHistory)
    }

    @Test
    fun `manual add does not auto-fill immediately`() {
        var board = BoardState()
        Rank.entries.drop(1).forEach { rank ->
            board = board.addCard(Seat.NORTH, CardId(Suit.SPADES, rank))
        }
        Rank.entries.forEach { rank ->
            board = board.addCard(Seat.EAST, CardId(Suit.HEARTS, rank))
            board = board.addCard(Seat.SOUTH, CardId(Suit.DIAMONDS, rank))
        }

        val state = BoardEditState(boardNumber = 1, boardState = board, selectedSeat = Seat.NORTH)
        val update = EditBoardReducer.addManualCardToSelectedHand(state, CardId(Suit.SPADES, Rank.ACE))

        assertEquals(13, update.state.boardState.handOf(Seat.NORTH).count())
        assertEquals(0, update.state.boardState.handOf(Seat.WEST).count())
        assertEquals(39, update.state.boardState.totalCardCount())
    }

    @Test
    fun `manual repair setup still auto-fills when seat selection changes`() {
        var board = BoardState()
        Rank.entries.drop(1).forEach { rank ->
            board = board.addCard(Seat.NORTH, CardId(Suit.SPADES, rank))
        }
        Rank.entries.forEach { rank ->
            board = board.addCard(Seat.EAST, CardId(Suit.HEARTS, rank))
            board = board.addCard(Seat.SOUTH, CardId(Suit.DIAMONDS, rank))
        }

        val repairedState = EditBoardReducer.addManualCardToSelectedHand(
            BoardEditState(boardNumber = 1, boardState = board, selectedSeat = Seat.NORTH),
            CardId(Suit.SPADES, Rank.ACE),
        ).state

        val update = EditBoardReducer.tryAutoFillFourthHand(
            repairedState.copy(selectedSeat = Seat.WEST),
        )

        assertEquals(52, update.state.boardState.totalCardCount())
        assertEquals(13, update.state.boardState.handOf(Seat.WEST).count())
        assertEquals("West auto-filled from remaining 13 cards.\nBoard complete.", update.message)
    }

    @Test
    fun `swapping hands clears history`() {
        val card = CardId(Suit.SPADES, Rank.ACE)
        val state = BoardEditState(boardNumber = 1, selectedSeat = Seat.NORTH)
        val update = EditBoardReducer.applyScannedCard(state, card, "sig1")
        
        val swapUpdate = EditBoardReducer.swapSelectedHandWith(update.state, Seat.SOUTH)
        
        assertEquals(1, swapUpdate.state.boardState.handOf(Seat.SOUTH).count())
        assertEquals(0, swapUpdate.state.addHistory.size)
    }

    @Test
    fun `clear hand clears history for that hand`() {
        val cardN = CardId(Suit.SPADES, Rank.ACE)
        val cardE = CardId(Suit.HEARTS, Rank.ACE)
        val state = BoardEditState(boardNumber = 1, selectedSeat = Seat.NORTH)
        
        val update1 = EditBoardReducer.applyScannedCard(state, cardN, "sigN")
        val update2 = EditBoardReducer.applyScannedCard(update1.state.copy(selectedSeat = Seat.EAST), cardE, "sigE")
        
        val clearUpdate = EditBoardReducer.clearSelectedHand(update2.state.copy(selectedSeat = Seat.NORTH))
        
        assertEquals(0, clearUpdate.state.boardState.handOf(Seat.NORTH).count())
        assertEquals(1, clearUpdate.state.addHistory.size)
        assertEquals(Seat.EAST, clearUpdate.state.addHistory[0].seat)
    }

    @Test
    fun `undo removes last added card for selected hand across mixed history`() {
        val northFirst = CardId(Suit.SPADES, Rank.ACE)
        val eastCard = CardId(Suit.HEARTS, Rank.KING)
        val northSecond = CardId(Suit.SPADES, Rank.QUEEN)

        val state = BoardEditState(boardNumber = 1, selectedSeat = Seat.NORTH)
        val update1 = EditBoardReducer.applyScannedCard(state, northFirst, "sigN1")
        val update2 = EditBoardReducer.applyScannedCard(
            update1.state.copy(selectedSeat = Seat.EAST),
            eastCard,
            "sigE1",
        )
        val update3 = EditBoardReducer.applyScannedCard(
            update2.state.copy(selectedSeat = Seat.NORTH),
            northSecond,
            "sigN2",
        )

        val undoUpdate = EditBoardReducer.undoAddForSelectedHand(update3.state)

        assertEquals(true, undoUpdate.state.boardState.handOf(Seat.NORTH).contains(northFirst))
        assertEquals(false, undoUpdate.state.boardState.handOf(Seat.NORTH).contains(northSecond))
        assertEquals(true, undoUpdate.state.boardState.handOf(Seat.EAST).contains(eastCard))
        assertEquals(2, undoUpdate.state.addHistory.size)
        assertEquals(listOf(northFirst, eastCard), undoUpdate.state.addHistory.map { it.card })
    }

    @Test
    fun `confirm duplicate override clears stale candidate when existing seat no longer contains card`() {
        val card = CardId(Suit.SPADES, Rank.ACE)
        val state = BoardEditState(
            boardNumber = 1,
            selectedSeat = Seat.NORTH,
            duplicateOverrideCandidate = DuplicateOverrideCandidate(
                card = card,
                signature = "sig1",
                existingSeat = Seat.EAST,
                targetSeat = Seat.NORTH,
            ),
        )

        val update = EditBoardReducer.confirmDuplicateOverride(state)

        assertEquals(0, update.state.boardState.totalCardCount())
        assertEquals(null, update.state.duplicateOverrideCandidate)
    }

    @Test
    fun `confirm duplicate override clears stale candidate when target already contains card`() {
        val card = CardId(Suit.SPADES, Rank.ACE)
        val board = BoardState().addCard(Seat.NORTH, card)
        val state = BoardEditState(
            boardNumber = 1,
            boardState = board,
            selectedSeat = Seat.NORTH,
            duplicateOverrideCandidate = DuplicateOverrideCandidate(
                card = card,
                signature = "sig1",
                existingSeat = Seat.EAST,
                targetSeat = Seat.NORTH,
            ),
        )

        val update = EditBoardReducer.confirmDuplicateOverride(state)

        assertEquals(1, update.state.boardState.totalCardCount())
        assertEquals(Seat.NORTH, update.state.boardState.seatContaining(card))
        assertEquals(null, update.state.duplicateOverrideCandidate)
    }

    @Test
    fun `confirm duplicate override clears stale candidate when target hand is complete`() {
        val duplicateCard = CardId(Suit.SPADES, Rank.ACE)
        var board = BoardState().addCard(Seat.EAST, duplicateCard)
        Rank.entries.forEach { rank ->
            board = board.addCard(Seat.NORTH, CardId(Suit.HEARTS, rank))
        }
        val state = BoardEditState(
            boardNumber = 1,
            boardState = board,
            selectedSeat = Seat.NORTH,
            duplicateOverrideCandidate = DuplicateOverrideCandidate(
                card = duplicateCard,
                signature = "sig1",
                existingSeat = Seat.EAST,
                targetSeat = Seat.NORTH,
            ),
        )

        val update = EditBoardReducer.confirmDuplicateOverride(state)

        assertEquals(14, update.state.boardState.totalCardCount())
        assertEquals(Seat.EAST, update.state.boardState.seatContaining(duplicateCard))
        assertEquals(null, update.state.duplicateOverrideCandidate)
    }

    @Test
    fun `swap clears pending duplicate candidate`() {
        val card = CardId(Suit.SPADES, Rank.ACE)
        val board = BoardState().addCard(Seat.NORTH, card)
        val state = BoardEditState(
            boardNumber = 1,
            boardState = board,
            selectedSeat = Seat.NORTH,
            duplicateOverrideCandidate = DuplicateOverrideCandidate(
                card = CardId(Suit.HEARTS, Rank.ACE),
                signature = "sigH1",
                existingSeat = Seat.EAST,
                targetSeat = Seat.NORTH,
            ),
        )

        val update = EditBoardReducer.swapSelectedHandWith(state, Seat.SOUTH)

        assertEquals(null, update.state.duplicateOverrideCandidate)
        assertEquals(Seat.SOUTH, update.state.boardState.seatContaining(card))
    }

    @Test
    fun `remove card from selected hand clears candidate and matching history only`() {
        val northCard = CardId(Suit.SPADES, Rank.ACE)
        val eastCard = CardId(Suit.HEARTS, Rank.ACE)
        var board = BoardState()
            .addCard(Seat.NORTH, northCard)
            .addCard(Seat.EAST, eastCard)
        val state = BoardEditState(
            boardNumber = 1,
            boardState = board,
            selectedSeat = Seat.NORTH,
            addHistory = listOf(
                AddedCardRecord(Seat.NORTH, northCard),
                AddedCardRecord(Seat.EAST, eastCard),
            ),
            duplicateOverrideCandidate = DuplicateOverrideCandidate(
                card = CardId(Suit.DIAMONDS, Rank.ACE),
                signature = "sigD1",
                existingSeat = Seat.WEST,
                targetSeat = Seat.NORTH,
            ),
        )

        val update = EditBoardReducer.removeCardFromSelectedHand(state, northCard)

        assertEquals(false, update.state.boardState.handOf(Seat.NORTH).contains(northCard))
        assertEquals(true, update.state.boardState.handOf(Seat.EAST).contains(eastCard))
        assertEquals(listOf(AddedCardRecord(Seat.EAST, eastCard)), update.state.addHistory)
        assertEquals(null, update.state.duplicateOverrideCandidate)
    }
}
