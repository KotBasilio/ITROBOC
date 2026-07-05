package org.itroboc.app

import org.itroboc.core.BoardEditState
import org.itroboc.core.BoardState
import org.itroboc.core.CardId
import org.itroboc.core.DeckProfile
import org.itroboc.core.Rank
import org.itroboc.core.Seat
import org.itroboc.core.Suit
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
            requiredConsensusFrames = TdSessionState.DEFAULT_CONSENSUS_FRAMES,
            onBoardEditStateChange = firstSink::add,
        )
        controller.update(
            state = BoardEditState(boardNumber = 1),
            profile = testDeckProfile(),
            mode = BarcodeOrientationMode.BFM,
            requiredConsensusFrames = TdSessionState.DEFAULT_CONSENSUS_FRAMES,
            onBoardEditStateChange = secondSink::add,
        )

        repeat(4) {
            controller.handleCameraScan(foundOutcome("bfm1549"))
            time.current += 50L
        }

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
            requiredConsensusFrames = TdSessionState.DEFAULT_CONSENSUS_FRAMES,
            onBoardEditStateChange = appliedStates::add,
        )

        repeat(4) {
            controller.handleCameraScan(foundOutcome("bfm1549"))
            time.current += 50L
        }
        controller.handleCameraScan(foundOutcome("bfm1549"))

        assertEquals(1, appliedStates.size)
        assertEquals("Added SA to North via bfm1549.", controller.lastResultMessage)
    }

    @Test
    fun `unknown signatures never stabilize`() {
        val time = FakeTime(5_000L)
        val appliedStates = mutableListOf<BoardEditState>()
        val controller = EditBoardController(
            onBoardEditStateChange = appliedStates::add,
            nowMillis = time::now,
        )
        controller.update(
            state = BoardEditState(boardNumber = 1),
            profile = DeckProfile(emptyMap()),
            mode = BarcodeOrientationMode.BFM,
            requiredConsensusFrames = TdSessionState.DEFAULT_CONSENSUS_FRAMES,
            onBoardEditStateChange = appliedStates::add,
        )

        repeat(4) {
            controller.handleCameraScan(foundOutcome("bfm1549"))
            time.current += 50L
        }
        assertTrue(appliedStates.isEmpty())
        assertEquals("bfm1549?", controller.thoughts)
        assertEquals(null, controller.lastResultMessage)

        time.current = 6_000L
        repeat(4) {
            controller.handleCameraScan(foundOutcome("bfm154A"))
            time.current += 50L
        }
        assertTrue(appliedStates.isEmpty())
        assertEquals("bfm154A?", controller.thoughts)
        assertEquals(null, controller.lastResultMessage)

        time.current = 9_100L
        repeat(4) {
            controller.handleCameraScan(foundOutcome("bfm154B"))
            time.current += 50L
        }
        assertTrue(appliedStates.isEmpty())
        assertEquals("bfm154B?", controller.thoughts)
        assertEquals(null, controller.lastResultMessage)
    }

    @Test
    fun `found scans require consensus before mutating board state`() {
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
            requiredConsensusFrames = TdSessionState.DEFAULT_CONSENSUS_FRAMES,
            onBoardEditStateChange = appliedStates::add,
        )

        repeat(3) {
            controller.handleCameraScan(foundOutcome("bfm1549"))
            time.current += 50L
        }

        assertTrue(appliedStates.isEmpty())
        assertEquals("♠A???", controller.thoughts)

        controller.handleCameraScan(foundOutcome("bfm1549"))

        assertEquals(1, appliedStates.size)
        assertEquals("♠A.", controller.thoughts)
    }

    @Test
    fun `manual add bypasses stabilization and updates immediately`() {
        val appliedStates = mutableListOf<BoardEditState>()
        val controller = EditBoardController(
            onBoardEditStateChange = appliedStates::add,
        )
        controller.update(
            state = BoardEditState(boardNumber = 1),
            profile = testDeckProfile(),
            mode = BarcodeOrientationMode.BFM,
            requiredConsensusFrames = TdSessionState.DEFAULT_CONSENSUS_FRAMES,
            onBoardEditStateChange = appliedStates::add,
        )

        controller.onManualAddCard(CardId.parse("SA"))

        assertEquals(1, appliedStates.size)
        assertEquals("Added SA to North manually.", controller.lastResultMessage)
        assertEquals(CardId.parse("SA"), controller.lastScannedCard)
        assertEquals("0", controller.thoughts)
    }

    @Test
    fun `manual add moves card from another hand immediately`() {
        var latestState = BoardEditState(
            boardNumber = 1,
            boardState = BoardState().addCard(Seat.EAST, CardId.parse("SA")),
        )
        val controller = EditBoardController(
            onBoardEditStateChange = {
                latestState = it
            },
        )
        controller.update(
            state = latestState,
            profile = testDeckProfile(),
            mode = BarcodeOrientationMode.BFM,
            requiredConsensusFrames = TdSessionState.DEFAULT_CONSENSUS_FRAMES,
            onBoardEditStateChange = { latestState = it },
        )

        controller.onManualAddCard(CardId.parse("SA"))

        assertEquals(Seat.NORTH, latestState.boardState.seatContaining(CardId.parse("SA")))
        assertEquals("Moved SA from East to North.", controller.lastResultMessage)
    }

    @Test
    fun `manual add blocks when selected hand is full and keeps seat`() {
        var board = BoardState()
        Rank.entries.forEach { rank ->
            board = board.addCard(Seat.NORTH, CardId(Suit.SPADES, rank))
        }
        val state = BoardEditState(boardNumber = 1, boardState = board, selectedSeat = Seat.NORTH)
        val appliedStates = mutableListOf<BoardEditState>()
        val controller = EditBoardController(
            onBoardEditStateChange = appliedStates::add,
        )
        controller.update(
            state = state,
            profile = testDeckProfile(),
            mode = BarcodeOrientationMode.BFM,
            requiredConsensusFrames = TdSessionState.DEFAULT_CONSENSUS_FRAMES,
            onBoardEditStateChange = appliedStates::add,
        )

        controller.onManualAddCard(CardId.parse("HA"))

        assertEquals(1, appliedStates.size)
        assertEquals(state.boardState, appliedStates.single().boardState)
        assertEquals("North already has 13 cards.\nRemove one first.", controller.lastResultMessage)
        assertEquals(Seat.NORTH, controller.boardEditState.selectedSeat)
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

    @Test
    fun `lower consensus frame requirement lands faster`() {
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
            requiredConsensusFrames = 2,
            onBoardEditStateChange = appliedStates::add,
        )

        controller.handleCameraScan(foundOutcome("bfm1549"))
        assertTrue(appliedStates.isEmpty())
        assertEquals("♠A?", controller.thoughts)

        time.current += 50L
        controller.handleCameraScan(foundOutcome("bfm1549"))

        assertEquals(1, appliedStates.size)
        assertEquals("♠A.", controller.thoughts)
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
