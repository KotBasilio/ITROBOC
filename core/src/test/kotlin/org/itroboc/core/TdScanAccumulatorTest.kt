package org.itroboc.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TdScanAccumulatorTest {
    private val profile = BuiltInDeckProfiles.demoBridge52()

    @Test
    fun `adds a looked-up card to the requested seat`() {
        val report = TdScanAccumulator(profile).scan(Seat.NORTH, "0x1001")

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
            .scan(Seat.NORTH, "0x1001")
            .accumulator

        val report = afterFirstScan.scan(Seat.NORTH, "0x1001")

        assertEquals(TdScanResult.AlreadyInThisHand(CardId.parse("SA")), report.result)
        assertEquals(afterFirstScan, report.accumulator)
    }

    @Test
    fun `reports duplicates already on another seat`() {
        val afterFirstScan = TdScanAccumulator(profile)
            .scan(Seat.EAST, "0x1001")
            .accumulator

        val report = afterFirstScan.scan(Seat.WEST, "0x1001")

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

        val report = accumulator.scan(Seat.NORTH, "0x100E")

        assertEquals(TdScanResult.HandAlreadyComplete(Seat.NORTH), report.result)
        assertEquals(accumulator, report.accumulator)
    }

    @Test
    fun `reports cross hand duplicate before hand already complete`() {
        val completeNorthHand = listOf(
            "SA", "SK", "SQ", "SJ", "ST",
            "H9", "H8", "H7",
            "D6", "D5",
            "C4", "C3", "C2",
        ).fold(HandState()) { hand, card -> hand.addCard(CardId.parse(card)) }

        val accumulator = TdScanAccumulator(
            deckProfile = DeckProfile(mapOf("sig-ha" to CardId.parse("HA"))),
            boardState = BoardState(
                mapOf(
                    Seat.NORTH to completeNorthHand,
                    Seat.EAST to HandState().addCard(CardId.parse("HA")),
                    Seat.SOUTH to HandState(),
                    Seat.WEST to HandState(),
                ),
            ),
        )

        val report = accumulator.scan(Seat.NORTH, "sig-ha")

        assertEquals(TdScanResult.AlreadyOnBoard(CardId.parse("HA"), Seat.EAST), report.result)
        assertEquals(accumulator, report.accumulator)
    }

    @Test
    fun `returns strongly typed results`() {
        val report = TdScanAccumulator(profile).scan(Seat.NORTH, "0x1001")

        assertIs<TdScanResult.Added>(report.result)
    }

    @Test
    fun `classifies scan result severities for future ui`() {
        val results = listOf(
            TdScanResult.Added(CardId.parse("SA")) to TdScanSeverity.INFO,
            TdScanResult.AlreadyInThisHand(CardId.parse("SA")) to TdScanSeverity.INFO,
            TdScanResult.UnknownSignature("sig-missing") to TdScanSeverity.WARNING,
            TdScanResult.AlreadyOnBoard(CardId.parse("SA"), Seat.EAST) to TdScanSeverity.CONFLICT,
            TdScanResult.HandAlreadyComplete(Seat.NORTH) to TdScanSeverity.INFO,
        )

        results.forEach { (result, expectedSeverity) ->
            assertEquals(expectedSeverity, result.severity)
        }
    }

    @Test
    fun `batch adds several new cards`() {
        val report = TdScanAccumulator(profile).scanMany(
            seat = Seat.NORTH,
            signatures = listOf("0x1001", "0x1002", "0x100E"),
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
            .scan(Seat.NORTH, "0x1001")
            .accumulator

        val report = seededAccumulator.scanMany(
            seat = Seat.NORTH,
            signatures = listOf("0x1001", "0x1002", "0x1001"),
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
            signatures = listOf("0xDEAD", "0x1001", "0xBEEF"),
        )

        assertEquals(
            listOf(
                TdScanResult.UnknownSignature("0xDEAD"),
                TdScanResult.Added(CardId.parse("SA")),
                TdScanResult.UnknownSignature("0xBEEF"),
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
                    "0x1034" to CardId.parse("C2"),
                    "0x100E" to CardId.parse("HA"),
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

        val report = accumulator.scanMany(Seat.NORTH, listOf("0x1034", "0x100E"))

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
            .scan(Seat.EAST, "0x1002")
            .accumulator

        val report = seededAccumulator.scanMany(
            seat = Seat.NORTH,
            signatures = listOf("0xDEAD", "0x1001", "0x1002", "0x1001", "0x100E"),
        )

        assertEquals(
            listOf(
                TdScanResult.UnknownSignature("0xDEAD"),
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

    @Test
    fun `built in demo profile still reports unknown signatures through scan flow`() {
        val report = TdScanAccumulator(BuiltInDeckProfiles.demoBridge52()).scan(Seat.NORTH, "0xFFFF")

        assertEquals(TdScanResult.UnknownSignature("0xFFFF"), report.result)
    }
}
