package org.itroboc.core

data class BoardProgressSummary(
    val handCounts: Map<Seat, Int>,
    val totalCards: Int,
    val completeSeats: Set<Seat>,
    val boardComplete: Boolean,
    val duplicateConflictCount: Int,
) {
    companion object {
        fun from(
            boardState: BoardState,
            recentReports: List<TdScanReport> = emptyList(),
        ): BoardProgressSummary {
            val handCounts = Seat.entries.associateWith { seat -> boardState.handOf(seat).count() }
            val completeSeats = Seat.entries.filterTo(linkedSetOf()) { seat -> boardState.handOf(seat).isComplete() }

            return BoardProgressSummary(
                handCounts = handCounts,
                totalCards = boardState.totalCardCount(),
                completeSeats = completeSeats,
                boardComplete = boardState.totalCardCount() == BoardState.FULL_BOARD_SIZE,
                duplicateConflictCount = recentReports.count { it.result is TdScanResult.AlreadyOnBoard },
            )
        }
    }
}
