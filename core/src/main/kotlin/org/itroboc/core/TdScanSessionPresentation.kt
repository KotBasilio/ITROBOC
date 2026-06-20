package org.itroboc.core

data class TdScanSessionPresentation(
    val seat: Seat,
    val handProgress: HandProgressSummary,
    val boardProgress: BoardProgressSummary,
    val rawBatchReport: TdBatchScanReport,
    val handStatusText: String,
    val latestBatchMessages: List<String>,
) {
    companion object {
        fun from(seat: Seat, batchReport: TdBatchScanReport): TdScanSessionPresentation {
            val handProgress = HandProgressSummary.from(
                seat = seat,
                handState = batchReport.accumulator.boardState.handOf(seat),
            )
            val boardProgress = BoardProgressSummary.from(
                boardState = batchReport.accumulator.boardState,
                recentReports = batchReport.reports,
            )

            return TdScanSessionPresentation(
                seat = seat,
                handProgress = handProgress,
                boardProgress = boardProgress,
                rawBatchReport = batchReport,
                handStatusText = "${seat.displayName}: ${handProgress.currentCount}/${HandState.FULL_HAND_SIZE}",
                latestBatchMessages = buildLatestBatchMessages(handProgress, batchReport),
            )
        }

        private fun buildLatestBatchMessages(
            handProgress: HandProgressSummary,
            batchReport: TdBatchScanReport,
        ): List<String> {
            val messages = mutableListOf<String>()
            val addedCards = batchReport.reports.mapNotNull { report ->
                (report.result as? TdScanResult.Added)?.card
            }

            if (addedCards.isNotEmpty()) {
                messages += "Added ${addedCards.joinToString(", ") { it.toString() }}"
            }

            if (batchReport.summary.alreadyInThisHand > 0) {
                messages += pluralizedCountMessage(
                    count = batchReport.summary.alreadyInThisHand,
                    singular = "already known card skipped",
                    plural = "already known cards skipped",
                )
            }

            if (batchReport.summary.unknown > 0) {
                messages += pluralizedCountMessage(
                    count = batchReport.summary.unknown,
                    singular = "unknown signature",
                    plural = "unknown signatures",
                )
            }

            if (batchReport.summary.alreadyOnBoard > 0) {
                messages += pluralizedCountMessage(
                    count = batchReport.summary.alreadyOnBoard,
                    singular = "cross-hand conflict detected",
                    plural = "cross-hand conflicts detected",
                )
            }

            if (batchReport.summary.handAlreadyComplete > 0) {
                messages += "Hand already complete"
            }

            messages += if (handProgress.isComplete) {
                "Hand complete"
            } else {
                "Missing ${handProgress.remainingCount} cards"
            }

            return messages
        }

        private fun pluralizedCountMessage(
            count: Int,
            singular: String,
            plural: String,
        ): String = if (count == 1) {
            "1 $singular"
        } else {
            "$count $plural"
        }
    }
}
