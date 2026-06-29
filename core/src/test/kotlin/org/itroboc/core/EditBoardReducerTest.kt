package org.itroboc.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
    fun `adding a card already in another hand does nothing`() {
        val board = BoardState().addCard(Seat.EAST, CardId(Suit.SPADES, Rank.ACE))
        val state = BoardEditState(boardNumber = 1, boardState = board, selectedSeat = Seat.NORTH)
        val card = CardId(Suit.SPADES, Rank.ACE)
        val update = EditBoardReducer.applyScannedCard(state, card, "sig1")

        assertEquals(1, update.state.boardState.totalCardCount())
        assertEquals(state, update.state)
    }

    @Test
    fun `adding to a complete hand does nothing`() {
        var board = BoardState()
        val cards = Rank.entries.map { CardId(Suit.SPADES, it) }
        cards.forEach { board = board.addCard(Seat.NORTH, it) }
        
        val state = BoardEditState(boardNumber = 1, boardState = board, selectedSeat = Seat.NORTH)
        val newCard = CardId(Suit.HEARTS, Rank.ACE)
        val update = EditBoardReducer.applyScannedCard(state, newCard, "sig2")

        assertEquals(13, update.state.boardState.totalCardCount())
        assertEquals(state, update.state)
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
}
