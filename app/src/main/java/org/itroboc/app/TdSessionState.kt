package org.itroboc.app

import org.itroboc.core.BoardEditState
import org.itroboc.core.BoardState
import org.itroboc.core.Seat

data class TdSessionState(
    val boards: Map<Int, BoardEditState> = emptyMap(),
    val totalBoardsInGrid: Int = 30,
) {
    fun getOrInitBoard(boardNumber: Int): BoardEditState {
        return boards[boardNumber] ?: BoardEditState(boardNumber)
    }

    fun updateBoard(boardEditState: BoardEditState): TdSessionState {
        return copy(boards = boards + (boardEditState.boardNumber to boardEditState))
    }

    fun updateGridSize(newSize: Int): TdSessionState {
        require(newSize in ALLOWED_GRID_SIZES)
        require(newSize >= highestNonEmptyBoardNumber)
        return copy(totalBoardsInGrid = newSize)
    }

    val highestNonEmptyBoardNumber: Int
        get() = boards.filterValues { it.boardState.totalCardCount() > 0 }
            .keys.maxOrNull() ?: 0

    companion object {
        val ALLOWED_GRID_SIZES = listOf(15, 18, 21, 24, 27, 30, 33, 36, 39)
    }
}
