package org.itroboc.app

import org.itroboc.core.BoardEditState
import org.itroboc.core.BoardState
import org.itroboc.core.CardId
import org.itroboc.core.Seat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TdSessionStateTest {
    @Test
    fun `default total boards is 30`() {
        val state = TdSessionState()
        assertEquals(30, state.totalBoardsInGrid)
    }

    @Test
    fun `can update grid size`() {
        val state = TdSessionState().updateGridSize(24)
        assertEquals(24, state.totalBoardsInGrid)
    }

    @Test
    fun `updating grid size preserves existing boards`() {
        val board1 = BoardEditState(1, BoardState().addCard(Seat.NORTH, CardId.parse("SA")))
        val board31 = BoardEditState(31, BoardState().addCard(Seat.NORTH, CardId.parse("HA")))
        
        val state = TdSessionState(
            boards = mapOf(1 to board1, 31 to board31),
            totalBoardsInGrid = 30
        )
        
        val newState = state.updateGridSize(24)
        
        assertEquals(24, newState.totalBoardsInGrid)
        assertEquals(2, newState.boards.size)
        assertEquals(board1, newState.boards[1])
        assertEquals(board31, newState.boards[31])
    }
}
