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
}
