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
        assertEquals("Hand North already complete. Auto-advancing to East.", update.message)
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
    fun `auto-fill does not run if selected hand is nonempty`() {
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

        assertEquals(39 + 1, update.state.boardState.totalCardCount())
        assertEquals(state, update.state)
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
}
