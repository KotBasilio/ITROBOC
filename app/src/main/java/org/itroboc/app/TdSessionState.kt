package org.itroboc.app

import org.itroboc.core.BoardState
import org.itroboc.core.Seat

data class BoardEditState(
    val boardNumber: Int,
    val boardState: BoardState = BoardState(),
    val selectedSeat: Seat = Seat.NORTH
)

data class TdSessionState(
    val boards: Map<Int, BoardEditState> = emptyMap()
) {
    fun getOrInitBoard(boardNumber: Int): BoardEditState {
        return boards[boardNumber] ?: BoardEditState(boardNumber)
    }

    fun updateBoard(boardEditState: BoardEditState): TdSessionState {
        return copy(boards = boards + (boardEditState.boardNumber to boardEditState))
    }
}
