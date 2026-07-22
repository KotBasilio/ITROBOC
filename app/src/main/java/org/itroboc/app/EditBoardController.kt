package org.itroboc.app

import androidx.compose.runtime.*
import org.itroboc.core.*
import org.itroboc.vision.*
import kotlinx.coroutines.*

internal class EditBoardController(
    private var onBoardEditStateChange: (BoardEditState) -> Unit,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val beetleMind = BeetleMind(nowMillis = nowMillis)

    // --- Inputs updated every recomposition ---
    var boardEditState by mutableStateOf(BoardEditState(0))
    var deckProfile by mutableStateOf(BuiltInDeckProfiles.defaultProfile())
    var orientationMode by mutableStateOf(BarcodeOrientationMode.BRM)
    var requiredConsensusFrames by mutableIntStateOf(TdSessionState.DEFAULT_CONSENSUS_FRAMES)

    // --- Performance State ---
    var scanDeltas by mutableStateOf<List<Long>>(emptyList())
    var scansPerSecond by mutableDoubleStateOf(0.0)
    var scansIdleCount by mutableLongStateOf(0L)
    var lastScanTimestamp by mutableLongStateOf(nowMillis())

    // --- Scan State ---
    var lastResultMessage by mutableStateOf<String?>(null)
    var lastScannedCard by mutableStateOf<CardId?>(null)
    var thoughts by mutableStateOf(beetleMind.thought.presentation)

    fun update(
        state: BoardEditState,
        profile: DeckProfile,
        mode: BarcodeOrientationMode,
        requiredConsensusFrames: Int,
        onBoardEditStateChange: (BoardEditState) -> Unit,
    ) {
        boardEditState = state
        deckProfile = profile
        orientationMode = mode
        this.requiredConsensusFrames = requiredConsensusFrames
        this.onBoardEditStateChange = onBoardEditStateChange
    }

    fun dream(topic: BeetleDream) {
        publishMindOutput(beetleMind.dream(topic))
    }

    fun handleCameraScan(scanOutcome: CameraScanOutcome) {
        val now = nowMillis()
        val delta = now - lastScanTimestamp
        scanDeltas = scanDeltas + delta
        lastScanTimestamp = now

        val evidence = scanOutcome.toBeetleEvidence(
            profile = deckProfile,
            orientationMode = orientationMode,
        ) ?: return
        publishMindOutput(
            beetleMind.observe(
                evidence = evidence,
                requiredConsensusFrames = requiredConsensusFrames,
            ),
        )
    }

    private fun publishMindOutput(output: BeetleMindOutput) {
        thoughts = output.thought.presentation
        output.acceptedCardScan?.let(::applyAcceptedCardScan)
    }

    private fun applyAcceptedCardScan(scan: AcceptedCardScan) {
        val isBoardComplete = BoardProgressSummary.from(boardEditState.boardState).boardComplete
        if (isBoardComplete) return

        val update = EditBoardReducer.applyScannedCard(
            editState = boardEditState,
            card = scan.cardId,
            signature = scan.rawSignature,
        )
        
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

    fun onManualAddCard(card: CardId) {
        publishMindOutput(beetleMind.reset())
        val update = EditBoardReducer.addManualCardToSelectedHand(boardEditState, card)
        onBoardEditStateChange(update.state)
        lastResultMessage = update.message
        if (update.lastScannedCard != null) {
            lastScannedCard = update.lastScannedCard
        }
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

internal fun CameraScanOutcome.toBeetleEvidence(
    profile: DeckProfile,
    orientationMode: BarcodeOrientationMode,
): BeetleEvidence? = when (this) {
    is CameraScanOutcome.Decoded -> when (val result = decodeResult) {
        is BarcodeDecodeResult.Found -> {
            val signature = result.signature.viewedAs(orientationMode) ?: return null
            val cardId = profile.lookup(signature)
            if (cardId == null) {
                BeetleEvidence.Unknown(
                    rawSignature = signature,
                    confidence = result.signature.confidence,
                )
            } else {
                BeetleEvidence.Known(
                    rawSignature = signature,
                    cardId = cardId,
                    confidence = result.signature.confidence,
                )
            }
        }
        is BarcodeDecodeResult.Ambiguous -> BeetleEvidence.Ambiguous(
            candidates = result.candidates.mapNotNull { candidate ->
                candidate.viewedAs(orientationMode)?.let { signature ->
                    BeetleSignatureCandidate(
                        rawSignature = signature,
                        confidence = candidate.confidence,
                    )
                }
            },
        )
        is BarcodeDecodeResult.NotFound -> BeetleEvidence.NotFound(result.reason)
    }
    is CameraScanOutcome.ConversionFailed -> BeetleEvidence.ConversionFailure(reason)
}

@Composable
internal fun rememberEditBoardController(
    boardEditState: BoardEditState,
    deckProfile: DeckProfile,
    orientationMode: BarcodeOrientationMode,
    requiredConsensusFrames: Int,
    onBoardEditStateChange: (BoardEditState) -> Unit
): EditBoardController {
    val controller = remember { EditBoardController(onBoardEditStateChange) }
    controller.update(boardEditState, deckProfile, orientationMode, requiredConsensusFrames, onBoardEditStateChange)
    
    LaunchedEffect(controller) {
        controller.runMetricsLoop()
    }
    
    return controller
}
