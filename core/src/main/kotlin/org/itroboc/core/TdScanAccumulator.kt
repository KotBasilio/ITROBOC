package org.itroboc.core

data class TdScanAccumulator(
    val deckProfile: DeckProfile,
    val boardState: BoardState = BoardState(),
) {
    fun scan(seat: Seat, signature: String): TdScanReport {
        val card = deckProfile.lookup(signature)
            ?: return TdScanReport(this, TdScanResult.UnknownSignature(signature))

        val hand = boardState.handOf(seat)
        if (hand.contains(card)) {
            return TdScanReport(this, TdScanResult.AlreadyInThisHand(card))
        }

        val existingSeat = boardState.seatContaining(card)
        if (existingSeat != null) {
            return TdScanReport(this, TdScanResult.AlreadyOnBoard(card, existingSeat))
        }

        if (hand.isComplete()) {
            return TdScanReport(this, TdScanResult.HandAlreadyComplete(seat))
        }

        val nextAccumulator = copy(boardState = boardState.addCard(seat, card))
        return TdScanReport(nextAccumulator, TdScanResult.Added(card))
    }

    fun scanMany(seat: Seat, signatures: List<String>): TdBatchScanReport {
        val reports = mutableListOf<TdScanReport>()
        var currentAccumulator = this

        signatures.forEach { signature ->
            val report = currentAccumulator.scan(seat, signature)
            reports += report
            currentAccumulator = report.accumulator
        }

        val currentHand = currentAccumulator.boardState.handOf(seat)
        return TdBatchScanReport(
            accumulator = currentAccumulator,
            reports = reports,
            summary = TdBatchScanSummary.from(reports),
            handCount = currentHand.count(),
            handComplete = currentHand.isComplete(),
        )
    }
}

data class TdScanReport(
    val accumulator: TdScanAccumulator,
    val result: TdScanResult,
)

data class TdBatchScanReport(
    val accumulator: TdScanAccumulator,
    val reports: List<TdScanReport>,
    val summary: TdBatchScanSummary,
    val handCount: Int,
    val handComplete: Boolean,
)

data class TdBatchScanSummary(
    val added: Int = 0,
    val unknown: Int = 0,
    val alreadyInThisHand: Int = 0,
    val alreadyOnBoard: Int = 0,
    val handAlreadyComplete: Int = 0,
) {
    companion object {
        fun from(reports: List<TdScanReport>): TdBatchScanSummary =
            reports.fold(TdBatchScanSummary()) { summary, report ->
                when (report.result) {
                    is TdScanResult.Added -> summary.copy(added = summary.added + 1)
                    is TdScanResult.UnknownSignature -> summary.copy(unknown = summary.unknown + 1)
                    is TdScanResult.AlreadyInThisHand ->
                        summary.copy(alreadyInThisHand = summary.alreadyInThisHand + 1)
                    is TdScanResult.AlreadyOnBoard ->
                        summary.copy(alreadyOnBoard = summary.alreadyOnBoard + 1)
                    is TdScanResult.HandAlreadyComplete ->
                        summary.copy(handAlreadyComplete = summary.handAlreadyComplete + 1)
                }
            }
    }
}

sealed interface TdScanResult {
    val severity: TdScanSeverity

    data class Added(val card: CardId) : TdScanResult {
        override val severity: TdScanSeverity = TdScanSeverity.INFO
    }

    data class AlreadyInThisHand(val card: CardId) : TdScanResult {
        override val severity: TdScanSeverity = TdScanSeverity.INFO
    }

    data class AlreadyOnBoard(val card: CardId, val existingSeat: Seat) : TdScanResult {
        override val severity: TdScanSeverity = TdScanSeverity.CONFLICT
    }

    data class UnknownSignature(val signature: String) : TdScanResult {
        override val severity: TdScanSeverity = TdScanSeverity.WARNING
    }

    data class HandAlreadyComplete(val seat: Seat) : TdScanResult {
        override val severity: TdScanSeverity = TdScanSeverity.INFO
    }
}

enum class TdScanSeverity {
    INFO,
    WARNING,
    CONFLICT,
}
