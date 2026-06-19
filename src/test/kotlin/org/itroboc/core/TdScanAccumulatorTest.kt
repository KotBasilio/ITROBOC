package org.itroboc.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TdScanAccumulatorTest {
    private val profile = DeckProfile(
        mapOf(
            "sig-sa" to CardId.parse("SA"),
            "sig-sk" to CardId.parse("SK"),
            "sig-ha" to CardId.parse("HA"),
        ),
    )

    @Test
    fun `adds a looked-up card to the requested seat`() {
        val report = TdScanAccumulator(profile).scan(Seat.NORTH, "sig-sa")

        assertEquals(TdScanResult.Added(CardId.parse("SA")), report.result)
        assertEquals(true, report.accumulator.boardState.handOf(Seat.NORTH).contains(CardId.parse("SA")))
    }

    @Test
    fun `reports unknown signature without changing state`() {
        val accumulator = TdScanAccumulator(profile)

        val report = accumulator.scan(Seat.NORTH, "sig-missing")

        assertEquals(TdScanResult.UnknownSignature("sig-missing"), report.result)
        assertEquals(accumulator, report.accumulator)
    }

    @Test
    fun `reports duplicates already in the same hand`() {
        val afterFirstScan = TdScanAccumulator(profile)
            .scan(Seat.NORTH, "sig-sa")
            .accumulator

        val report = afterFirstScan.scan(Seat.NORTH, "sig-sa")

        assertEquals(TdScanResult.AlreadyInThisHand(CardId.parse("SA")), report.result)
        assertEquals(afterFirstScan, report.accumulator)
    }

    @Test
    fun `reports duplicates already on another seat`() {
        val afterFirstScan = TdScanAccumulator(profile)
            .scan(Seat.EAST, "sig-sa")
            .accumulator

        val report = afterFirstScan.scan(Seat.WEST, "sig-sa")

        assertEquals(TdScanResult.AlreadyOnBoard(CardId.parse("SA"), Seat.EAST), report.result)
        assertEquals(afterFirstScan, report.accumulator)
    }

    @Test
    fun `reports when the current hand is already complete`() {
        val completeNorthHand = listOf(
            "SA", "SK", "SQ", "SJ", "ST",
            "H9", "H8", "H7",
            "D6", "D5",
            "C4", "C3", "C2",
        ).fold(HandState()) { hand, card -> hand.addCard(CardId.parse(card)) }

        val accumulator = TdScanAccumulator(
            deckProfile = profile,
            boardState = BoardState(
                mapOf(
                    Seat.NORTH to completeNorthHand,
                    Seat.EAST to HandState(),
                    Seat.SOUTH to HandState(),
                    Seat.WEST to HandState(),
                ),
            ),
        )

        val report = accumulator.scan(Seat.NORTH, "sig-ha")

        assertEquals(TdScanResult.HandAlreadyComplete(Seat.NORTH), report.result)
        assertEquals(accumulator, report.accumulator)
    }

    @Test
    fun `returns strongly typed results`() {
        val report = TdScanAccumulator(profile).scan(Seat.NORTH, "sig-sa")

        assertIs<TdScanResult.Added>(report.result)
    }

    @Test
    fun `batch adds several new cards`() {
        val report = TdScanAccumulator(profile).scanMany(
            seat = Seat.NORTH,
            signatures = listOf("sig-sa", "sig-sk", "sig-ha"),
        )

        assertEquals(
            listOf(
                TdScanResult.Added(CardId.parse("SA")),
                TdScanResult.Added(CardId.parse("SK")),
                TdScanResult.Added(CardId.parse("HA")),
            ),
            report.reports.map { it.result },
        )
        assertEquals(TdBatchScanSummary(added = 3), report.summary)
        assertEquals(3, report.handCount)
        assertEquals(false, report.handComplete)
    }

    @Test
    fun `batch ignores duplicates safely`() {
        val seededAccumulator = TdScanAccumulator(profile)
            .scan(Seat.NORTH, "sig-sa")
            .accumulator

        val report = seededAccumulator.scanMany(
            seat = Seat.NORTH,
            signatures = listOf("sig-sa", "sig-sk", "sig-sa"),
        )

        assertEquals(
            listOf(
                TdScanResult.AlreadyInThisHand(CardId.parse("SA")),
                TdScanResult.Added(CardId.parse("SK")),
                TdScanResult.AlreadyInThisHand(CardId.parse("SA")),
            ),
            report.reports.map { it.result },
        )
        assertEquals(
            TdBatchScanSummary(added = 1, alreadyInThisHand = 2),
            report.summary,
        )
        assertEquals(2, report.handCount)
    }

    @Test
    fun `batch handles unknown signatures without changing state for those entries`() {
        val report = TdScanAccumulator(profile).scanMany(
            seat = Seat.NORTH,
            signatures = listOf("sig-missing", "sig-sa", "sig-void"),
        )

        assertEquals(
            listOf(
                TdScanResult.UnknownSignature("sig-missing"),
                TdScanResult.Added(CardId.parse("SA")),
                TdScanResult.UnknownSignature("sig-void"),
            ),
            report.reports.map { it.result },
        )
        assertEquals(TdBatchScanSummary(added = 1, unknown = 2), report.summary)
        assertEquals(1, report.handCount)
        assertEquals(true, report.accumulator.boardState.handOf(Seat.NORTH).contains(CardId.parse("SA")))
    }

    @Test
    fun `batch reports correctly when hand becomes complete`() {
        val almostCompleteNorthHand = listOf(
            "SA", "SK", "SQ", "SJ", "ST",
            "H9", "H8", "H7",
            "D6", "D5",
            "C4", "C3",
        ).fold(HandState()) { hand, card -> hand.addCard(CardId.parse(card)) }

        val accumulator = TdScanAccumulator(
            deckProfile = DeckProfile(
                mapOf(
                    "sig-c2" to CardId.parse("C2"),
                    "sig-ha" to CardId.parse("HA"),
                ),
            ),
            boardState = BoardState(
                mapOf(
                    Seat.NORTH to almostCompleteNorthHand,
                    Seat.EAST to HandState(),
                    Seat.SOUTH to HandState(),
                    Seat.WEST to HandState(),
                ),
            ),
        )

        val report = accumulator.scanMany(Seat.NORTH, listOf("sig-c2", "sig-ha"))

        assertEquals(
            listOf(
                TdScanResult.Added(CardId.parse("C2")),
                TdScanResult.HandAlreadyComplete(Seat.NORTH),
            ),
            report.reports.map { it.result },
        )
        assertEquals(
            TdBatchScanSummary(added = 1, handAlreadyComplete = 1),
            report.summary,
        )
        assertEquals(13, report.handCount)
        assertEquals(true, report.handComplete)
    }

    @Test
    fun `batch preserves order of per signature results`() {
        val seededAccumulator = TdScanAccumulator(profile)
            .scan(Seat.EAST, "sig-sk")
            .accumulator

        val report = seededAccumulator.scanMany(
            seat = Seat.NORTH,
            signatures = listOf("sig-missing", "sig-sa", "sig-sk", "sig-sa", "sig-ha"),
        )

        assertEquals(
            listOf(
                TdScanResult.UnknownSignature("sig-missing"),
                TdScanResult.Added(CardId.parse("SA")),
                TdScanResult.AlreadyOnBoard(CardId.parse("SK"), Seat.EAST),
                TdScanResult.AlreadyInThisHand(CardId.parse("SA")),
                TdScanResult.Added(CardId.parse("HA")),
            ),
            report.reports.map { it.result },
        )
        assertEquals(
            TdBatchScanSummary(
                added = 2,
                unknown = 1,
                alreadyInThisHand = 1,
                alreadyOnBoard = 1,
            ),
            report.summary,
        )
        assertEquals(2, report.handCount)
    }
}
