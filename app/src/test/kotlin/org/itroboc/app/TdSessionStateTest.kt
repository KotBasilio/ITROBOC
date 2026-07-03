package org.itroboc.app

import org.itroboc.core.BoardEditState
import org.itroboc.core.BoardState
import org.itroboc.core.CardId
import org.itroboc.core.Seat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TdSessionStateTest {
    @Test
    fun `default total boards is 30`() {
        val state = TdSessionState()
        assertEquals(30, state.totalBoardsInGrid)
    }

    @Test
    fun `valid resize succeeds when no non empty board is hidden`() {
        val state = TdSessionState().updateGridSize(24)
        assertEquals(24, state.totalBoardsInGrid)
    }

    @Test
    fun `invalid grid size is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            TdSessionState().updateGridSize(25)
        }
    }

    @Test
    fun `shrinking below highest non empty board is rejected`() {
        val board31 = BoardEditState(31, BoardState().addCard(Seat.NORTH, CardId.parse("HA")))
        val state = TdSessionState(
            boards = mapOf(31 to board31),
            totalBoardsInGrid = 33,
        )

        assertFailsWith<IllegalArgumentException> {
            state.updateGridSize(24)
        }
    }

    @Test
    fun `growing grid size preserves existing boards`() {
        val board1 = BoardEditState(1, BoardState().addCard(Seat.NORTH, CardId.parse("SA")))
        val board31 = BoardEditState(31, BoardState().addCard(Seat.NORTH, CardId.parse("HA")))
        val state = TdSessionState(
            boards = mapOf(1 to board1, 31 to board31),
            totalBoardsInGrid = 33,
        )

        val newState = state.updateGridSize(39)

        assertEquals(39, newState.totalBoardsInGrid)
        assertEquals(board1, newState.boards[1])
        assertEquals(board31, newState.boards[31])
    }

    @Test
    fun `keeping grid size large enough preserves existing boards`() {
        val board1 = BoardEditState(1, BoardState().addCard(Seat.NORTH, CardId.parse("SA")))
        val board31 = BoardEditState(31, BoardState().addCard(Seat.NORTH, CardId.parse("HA")))
        val state = TdSessionState(
            boards = mapOf(1 to board1, 31 to board31),
            totalBoardsInGrid = 33,
        )

        val newState = state.updateGridSize(33)

        assertEquals(33, newState.totalBoardsInGrid)
        assertEquals(board1, newState.boards[1])
        assertEquals(board31, newState.boards[31])
    }

    @Test
    fun `highestNonEmptyBoardNumber returns the correct board number`() {
        val board1 = BoardEditState(1, BoardState().addCard(Seat.NORTH, CardId.parse("SA")))
        val board5 = BoardEditState(5, BoardState().addCard(Seat.SOUTH, CardId.parse("HA")))
        val board10 = BoardEditState(10, BoardState())

        val state = TdSessionState(
            boards = mapOf(1 to board1, 5 to board5, 10 to board10)
        )

        assertEquals(5, state.highestNonEmptyBoardNumber)
    }

    @Test
    fun `highestNonEmptyBoardNumber returns 0 for empty session`() {
        val state = TdSessionState()
        assertEquals(0, state.highestNonEmptyBoardNumber)
    }
}
