package org.itroboc.app

import androidx.compose.runtime.*
import org.itroboc.core.*
import org.itroboc.vision.*
import kotlinx.coroutines.*

internal class EditBoardController(
    private var onBoardEditStateChange: (BoardEditState) -> Unit,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    // --- Inputs updated every recomposition ---
    var boardEditState by mutableStateOf(BoardEditState(0))
    var deckProfile by mutableStateOf(BuiltInDeckProfiles.defaultProfile())
    var orientationMode by mutableStateOf(BarcodeOrientationMode.BRM)

    // --- Performance State ---
    var scanDeltas by mutableStateOf<List<Long>>(emptyList())
    var scansPerSecond by mutableDoubleStateOf(0.0)
    var scansIdleCount by mutableLongStateOf(0L)
    var lastScanTimestamp by mutableLongStateOf(System.currentTimeMillis())

    // --- Scan State ---
    var lastResultMessage by mutableStateOf<String?>(null)
    var lastScannedCard by mutableStateOf<CardId?>(null)
    
    // Debounce state
    private var lastRawSignature: String? = null
    private var lastScanTimeMillis: Long = 0L
    private val debounceWindowMillis = 1000L

    // Unknown signature throttling
    private var unknownSignatureCount = 0
    private var lastUnknownMessageTimeMillis: Long = 0L
    private val unknownThrottleMillis = 3000L

    fun update(
        state: BoardEditState,
        profile: DeckProfile,
        mode: BarcodeOrientationMode,
        onBoardEditStateChange: (BoardEditState) -> Unit,
    ) {
        boardEditState = state
        deckProfile = profile
        orientationMode = mode
        this.onBoardEditStateChange = onBoardEditStateChange
    }

    fun handleCameraScan(scanOutcome: CameraScanOutcome) {
        val now = nowMillis()
        val delta = now - lastScanTimestamp
        scanDeltas = scanDeltas + delta
        lastScanTimestamp = now

        when (scanOutcome) {
            is CameraScanOutcome.Decoded -> when (val decodeResult = scanOutcome.decodeResult) {
                is BarcodeDecodeResult.Found -> {
                    val signature = decodeResult.signature.viewedAs(orientationMode)
                    if (signature != null) {
                        handleScan(signature)
                    }
                }
                else -> {}
            }
            else -> {}
        }
    }

    private fun handleScan(signature: String) {
        val isBoardComplete = BoardProgressSummary.from(boardEditState.boardState).boardComplete
        if (isBoardComplete) return

        val now = nowMillis()
        if (signature == lastRawSignature && (now - lastScanTimeMillis) < debounceWindowMillis) {
            return
        }
        lastRawSignature = signature
        lastScanTimeMillis = now

        val update = EditBoardReducer.applyScannedCard(boardEditState, deckProfile.lookup(signature) ?: run {
            unknownSignatureCount++
            if (now - lastUnknownMessageTimeMillis > unknownThrottleMillis) {
                if (unknownSignatureCount > 1) {
                    lastResultMessage = "Unknown signatures total: $unknownSignatureCount."
                } else {
                    lastResultMessage = "Unknown signature: $signature"
                }
                lastUnknownMessageTimeMillis = now
            }
            return
        }, signature)
        
        onBoardEditStateChange(update.state)
        lastResultMessage = update.message
        if (update.lastScannedCard != null) {
            lastScannedCard = update.lastScannedCard
        }
    }

    fun onSeatClick(seat: Seat) {
        val nextBoardEditState = boardEditState.copy(selectedSeat = seat)
        val update = EditBoardReducer.tryAutoFillFourthHand(nextBoardEditState)
        onBoardEditStateChange(update.state)
        if (update.message != null) {
            lastResultMessage = update.message
        }
    }

    fun onClearHand() {
        val selectedHand = boardEditState.boardState.handOf(boardEditState.selectedSeat)
        if (selectedHand.count() > 0) {
            val update = EditBoardReducer.clearSelectedHand(boardEditState)
            onBoardEditStateChange(update.state)
            lastResultMessage = update.message
            lastScannedCard = null
        }
    }

    fun onClearBoard() {
        val update = EditBoardReducer.clearBoard(boardEditState)
        onBoardEditStateChange(update.state)
        lastResultMessage = update.message
        lastScannedCard = null
    }

    fun onUndo() {
        val update = EditBoardReducer.undoAddForSelectedHand(boardEditState)
        onBoardEditStateChange(update.state)
        lastResultMessage = update.message
    }

    fun onRemoveCard(card: CardId) {
        val update = EditBoardReducer.removeCardFromSelectedHand(boardEditState, card)
        onBoardEditStateChange(update.state)
        lastResultMessage = update.message
    }

    fun onSwapHands(targetSeat: Seat) {
        val update = EditBoardReducer.swapSelectedHandWith(boardEditState, targetSeat)
        onBoardEditStateChange(update.state)
        lastResultMessage = update.message
    }

    fun onConfirmDuplicate() {
        val update = EditBoardReducer.confirmDuplicateOverride(boardEditState)
        onBoardEditStateChange(update.state)
        lastResultMessage = update.message
    }

    suspend fun runMetricsLoop() {
        while (true) {
            updateMetrics()
            delay(500L)
        }
    }

    internal fun updateMetrics() {
        val isBoardComplete = BoardProgressSummary.from(boardEditState.boardState).boardComplete
        if (isBoardComplete) {
            scanDeltas = emptyList()
        }

        var cumulative = 0L
        val pruned = mutableListOf<Long>()
        for (delta in scanDeltas.asReversed()) {
            cumulative += delta
            pruned.add(delta)
            if (cumulative > 1000L) break
        }
        scanDeltas = pruned.asReversed()

        if (scanDeltas.isNotEmpty()) {
            val avgDelta = scanDeltas.average()
            scansPerSecond = if (avgDelta > 0) 1000.0 / avgDelta else 0.0
            scansIdleCount = 0
        } else {
            scansPerSecond = - scansIdleCount.toDouble()
            scansIdleCount++
        }
    }
}

@Composable
internal fun rememberEditBoardController(
    boardEditState: BoardEditState,
    deckProfile: DeckProfile,
    orientationMode: BarcodeOrientationMode,
    onBoardEditStateChange: (BoardEditState) -> Unit
): EditBoardController {
    val controller = remember { EditBoardController(onBoardEditStateChange) }
    controller.update(boardEditState, deckProfile, orientationMode, onBoardEditStateChange)
    
    LaunchedEffect(controller) {
        controller.runMetricsLoop()
    }
    
    return controller
}
