package org.itroboc.core

data class HandProgressSummary(
    val seat: Seat,
    val currentCount: Int,
    val remainingCount: Int,
    val isComplete: Boolean,
    val cardsBySuit: List<SuitCards>,
) {
    companion object {
        fun from(seat: Seat, handState: HandState): HandProgressSummary {
            val currentCount = handState.count()

            return HandProgressSummary(
                seat = seat,
                currentCount = currentCount,
                remainingCount = HandState.FULL_HAND_SIZE - currentCount,
                isComplete = handState.isComplete(),
                cardsBySuit = handState.cardsBySuitInBridgeOrder(),
            )
        }
    }
}
