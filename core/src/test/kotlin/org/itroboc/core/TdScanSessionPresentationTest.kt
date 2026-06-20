package org.itroboc.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TdScanSessionPresentationTest {
    private val profile = DeckProfile(
        mapOf(
            "sig-sa" to CardId.parse("SA"),
            "sig-sk" to CardId.parse("SK"),
            "sig-ha" to CardId.parse("HA"),
        ),
    )

    @Test
    fun `presents friendly latest batch messages while keeping raw report`() {
        val batchReport = TdScanAccumulator(profile).scanMany(
            seat = Seat.NORTH,
            signatures = listOf("sig-sa", "sig-sa", "sig-missing", "sig-sk"),
        )

        val presentation = TdScanSessionPresentation.from(Seat.NORTH, batchReport)

        assertEquals("North: 2/13", presentation.handStatusText)
        assertEquals(
            listOf(
                "Added SA, SK",
                "1 already known card skipped",
                "1 unknown signature",
                "Missing 11 cards",
            ),
            presentation.latestBatchMessages,
        )
        assertSame(batchReport, presentation.rawBatchReport)
        assertEquals(2, presentation.handProgress.currentCount)
        assertEquals(2, presentation.boardProgress.totalCards)
    }

    @Test
    fun `presents hand complete and conflict oriented messages`() {
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
                    Seat.EAST to HandState().addCard(CardId.parse("HA")),
                    Seat.SOUTH to HandState(),
                    Seat.WEST to HandState(),
                ),
            ),
        )

        val batchReport = accumulator.scanMany(Seat.NORTH, listOf("sig-c2", "sig-ha", "sig-ha"))
        val presentation = TdScanSessionPresentation.from(Seat.NORTH, batchReport)

        assertEquals("North: 13/13", presentation.handStatusText)
        assertEquals(
            listOf(
                "Added C2",
                "2 cross-hand conflicts detected",
                "Hand complete",
            ),
            presentation.latestBatchMessages,
        )
        assertEquals(2, presentation.boardProgress.duplicateConflictCount)
    }
}
