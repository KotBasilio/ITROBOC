package org.itroboc.app

import org.itroboc.core.BoardEditState
import org.itroboc.core.CardId
import org.itroboc.core.DeckProfile
import org.itroboc.vision.BarcodeDecodeResult
import org.itroboc.vision.BarcodeRoi
import org.itroboc.vision.DetectedSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditBoardControllerTest {
    @Test
    fun `handleCameraScan uses latest state change callback`() {
        val time = FakeTime(5_000L)
        val firstSink = mutableListOf<BoardEditState>()
        val secondSink = mutableListOf<BoardEditState>()
        val controller = EditBoardController(
            onBoardEditStateChange = firstSink::add,
            nowMillis = time::now,
        )

        controller.update(
            state = BoardEditState(boardNumber = 1),
            profile = testDeckProfile(),
            mode = BarcodeOrientationMode.BFM,
            onBoardEditStateChange = firstSink::add,
        )
        controller.update(
            state = BoardEditState(boardNumber = 1),
            profile = testDeckProfile(),
            mode = BarcodeOrientationMode.BFM,
            onBoardEditStateChange = secondSink::add,
        )

        controller.handleCameraScan(foundOutcome("bfm1549"))

        assertTrue(firstSink.isEmpty())
        assertEquals(1, secondSink.size)
        assertEquals(CardId.parse("SA"), secondSink.single().boardState.handOf(secondSink.single().selectedSeat).cards().single())
    }

    @Test
    fun `duplicate scans within debounce window are ignored`() {
        val time = FakeTime(5_000L)
        val appliedStates = mutableListOf<BoardEditState>()
        val controller = EditBoardController(
            onBoardEditStateChange = appliedStates::add,
            nowMillis = time::now,
        )
        controller.update(
            state = BoardEditState(boardNumber = 1),
            profile = testDeckProfile(),
            mode = BarcodeOrientationMode.BFM,
            onBoardEditStateChange = appliedStates::add,
        )

        controller.handleCameraScan(foundOutcome("bfm1549"))
        time.current = 1_500L
        controller.handleCameraScan(foundOutcome("bfm1549"))

        assertEquals(1, appliedStates.size)
        assertEquals("Added SA to North via bfm1549.", controller.lastResultMessage)
    }

    @Test
    fun `unknown signatures are throttled and counted cumulatively`() {
        val time = FakeTime(5_000L)
        val controller = EditBoardController(
            onBoardEditStateChange = {},
            nowMillis = time::now,
        )
        controller.update(
            state = BoardEditState(boardNumber = 1),
            profile = DeckProfile(emptyMap()),
            mode = BarcodeOrientationMode.BFM,
            onBoardEditStateChange = {},
        )

        controller.handleCameraScan(foundOutcome("bfm1549"))
        assertEquals("Unknown signature: bfm1549", controller.lastResultMessage)

        time.current = 6_000L
        controller.handleCameraScan(foundOutcome("bfm154A"))
        assertEquals("Unknown signature: bfm1549", controller.lastResultMessage)

        time.current = 9_100L
        controller.handleCameraScan(foundOutcome("bfm154B"))
        assertEquals("Unknown signatures total: 3.", controller.lastResultMessage)
    }

    @Test
    fun `updateMetrics prunes scan deltas to roughly one second window`() {
        val controller = EditBoardController(onBoardEditStateChange = {})
        controller.scanDeltas = listOf(200L, 300L, 400L, 500L)

        controller.updateMetrics()

        assertEquals(listOf(300L, 400L, 500L), controller.scanDeltas)
        assertEquals(2.5, controller.scansPerSecond)
        assertEquals(0L, controller.scansIdleCount)
    }

    private fun testDeckProfile(): DeckProfile = DeckProfile(
        mapOf("bfm1549" to CardId.parse("SA")),
    )

    private fun foundOutcome(rawSignature: String): CameraScanOutcome.Decoded =
        CameraScanOutcome.Decoded(
            frameDebugInfo = FrameDebugInfo(
                width = 100,
                height = 40,
                rotationDegrees = 0,
                timestampNanos = 0L,
                requestedByScan = false,
            ),
            roi = BarcodeRoi(x = 0, y = 0, width = 10, height = 10),
            decodeResult = BarcodeDecodeResult.Found(
                DetectedSignature(
                    rawSignature = rawSignature,
                    confidence = 0.95,
                    debug = null,
                ),
            ),
        )

    private class FakeTime(var current: Long) {
        fun now(): Long = current
    }
}
