package org.itroboc.core

data class HandProgressSummary(
    val seat: Seat,
    val currentCount: Int,
    val remainingCount: Int,
    val isComplete: Boolean,
    val cardsBySuit: List<SuitCards>,
) {
    companion object {
        fun from(seat: Seat, handState: HandState): HandProgressSummary =
            HandProgressSummary(
                seat = seat,
                currentCount = handState.count(),
                remainingCount = HandState.FULL_HAND_SIZE - handState.count(),
                isComplete = handState.isComplete(),
                cardsBySuit = handState.cardsBySuitInBridgeOrder(),
            )
    }
}
