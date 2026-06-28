package org.itroboc.app

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.tooling.preview.Devices
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.itroboc.core.*
import org.itroboc.vision.BarcodeDecodeResult
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@ComposePreview(device = Devices.AUTOMOTIVE_1024p, widthDp = 1024, heightDp = 600, showBackground = true)
@Composable
fun EditBoardScreenPreview() {
    MaterialTheme {
        EditBoardScreen(
            boardEditState = BoardEditState(boardNumber = 7),
            deckProfile = BuiltInDeckProfiles.defaultProfile(),
            orientationMode = BarcodeOrientationMode.BFM,
            onOrientationModeChange = {},
            onBoardEditStateChange = {},
            onBack = {}
        )
    }
}

@Composable
fun EditBoardScreen(
    boardEditState: BoardEditState,
    deckProfile: DeckProfile,
    orientationMode: BarcodeOrientationMode,
    onOrientationModeChange: (BarcodeOrientationMode) -> Unit,
    onBoardEditStateChange: (BoardEditState) -> Unit,
    onBack: () -> Unit
) {
    val boardNumber = boardEditState.boardNumber
    val boardState = boardEditState.boardState
    val selectedSeat = boardEditState.selectedSeat

    var showClearBoardDialog by remember { mutableStateOf(false) }
    var lastResultMessage by remember { mutableStateOf<String?>(null) }
    var lastScannedCard by remember { mutableStateOf<CardId?>(null) }

    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    val pendingScanRequest = remember { AtomicBoolean(true) }
    val frameDecoder = remember { AdminEditCameraFrameDecoder() }

    fun performAutoFillIfPossible(currentEditState: BoardEditState, seat: Seat): BoardEditState {
        val currentBoardState = currentEditState.boardState
        val selectedHand = currentBoardState.handOf(seat)
        val otherSeats = Seat.entries.filter { it != seat }
        val otherHandsComplete = otherSeats.all { currentBoardState.handOf(it).isComplete() }

        if (selectedHand.count() == 0 && otherHandsComplete) {
            val allCards = Suit.entries.flatMap { s -> Rank.entries.map { r -> CardId(s, r) } }.toSet()
            val assignedCards = currentBoardState.allCards()
            val remainingCards = allCards - assignedCards

            if (remainingCards.size == 13) {
                var autoFilledBoard = currentBoardState
                remainingCards.forEach { card ->
                    autoFilledBoard = autoFilledBoard.addCard(seat, card)
                }
                lastResultMessage = "${seat.displayName} auto-filled from remaining 13 cards. Board complete."
                return currentEditState.copy(boardState = autoFilledBoard)
            }
        }
        return currentEditState
    }

    fun onSeatClick(seat: Seat) {
        val nextBoardEditState = boardEditState.copy(selectedSeat = seat)
        val autoFilledState = performAutoFillIfPossible(nextBoardEditState, seat)
        onBoardEditStateChange(autoFilledState)
    }

    fun handleScan(signature: String) {
        val accumulator = TdScanAccumulator(deckProfile, boardState)
        val report = accumulator.scan(selectedSeat, signature)
        
        val newBoardState = report.accumulator.boardState
        val isHandCompleteNow = newBoardState.handOf(selectedSeat).isComplete()
        
        var nextSeat = selectedSeat
        var autoAdvanceMessage = ""
        
        if (isHandCompleteNow && (report.result is TdScanResult.Added || report.result is TdScanResult.HandAlreadyComplete)) {
            nextSeat = selectedSeat.next()
            autoAdvanceMessage = " Auto-advancing to ${nextSeat.displayName}."
        }

        val updatedEditState = boardEditState.copy(
            boardState = newBoardState,
            selectedSeat = nextSeat
        )
        
        val finalizedState = if (nextSeat != selectedSeat) {
            performAutoFillIfPossible(updatedEditState, nextSeat)
        } else {
            updatedEditState
        }

        onBoardEditStateChange(finalizedState)
        
        lastResultMessage = when (val result = report.result) {
            is TdScanResult.Added -> {
                lastScannedCard = result.card
                "Added ${result.card} to ${selectedSeat.displayName} via $signature.$autoAdvanceMessage"
            }
            is TdScanResult.AlreadyInThisHand -> {
                lastScannedCard = result.card
                "Already in ${selectedSeat.displayName}: ${result.card} (via $signature)."
            }
            is TdScanResult.AlreadyOnBoard -> {
                lastScannedCard = result.card
                "Already in ${result.existingSeat.displayName}: ${result.card} (via $signature). No change."
            }
            is TdScanResult.UnknownSignature -> "Unknown signature: ${result.signature}."
            is TdScanResult.HandAlreadyComplete -> "Hand ${result.seat.displayName} already complete.$autoAdvanceMessage"
        }
    }

    fun onClearClick() {
        val selectedHand = boardState.handOf(selectedSeat)
        if (selectedHand.count() > 0) {
            // Clear only hand
            val newHands = Seat.entries.associateWith { seat ->
                if (seat == selectedSeat) HandState() else boardState.handOf(seat)
            }
            onBoardEditStateChange(boardEditState.copy(boardState = BoardState(newHands)))
        } else {
            // Show clear board dialog
            showClearBoardDialog = true
        }
    }

    if (showClearBoardDialog) {
        AlertDialog(
            onDismissRequest = { showClearBoardDialog = false },
            title = { Text("Clear entire board?") },
            text = { Text("This removes all cards from Board $boardNumber.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onBoardEditStateChange(boardEditState.copy(boardState = BoardState()))
                        showClearBoardDialog = false
                    }
                ) {
                    Text("Clear board")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearBoardDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 3x3 Cockpit Layout using Row/Column
    Column(modifier = Modifier.fillMaxSize()) {
        // Top Row: Board controls | North hand | Last Scanned | Status
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            BoardControlsArea(
                boardNumber = boardNumber,
                selectedSeat = selectedSeat,
                boardState = boardState,
                onClear = { onClearClick() },
                modifier = Modifier.weight(1f)
            )
            HandArea(
                seat = Seat.NORTH,
                handState = boardState.handOf(Seat.NORTH),
                isSelected = selectedSeat == Seat.NORTH,
                onClick = { onSeatClick(Seat.NORTH) },
                modifier = Modifier.weight(1f)
            )
            LastScannedCardArea(
                cardId = lastScannedCard,
                modifier = Modifier.weight(1f)
            )
            StatusArea(
                boardState = boardState,
                orientationMode = orientationMode,
                message = lastResultMessage,
                modifier = Modifier.weight(2f)
            )
        }

        // Middle Row: West hand | Camera Area | East hand
        Row(modifier = Modifier.weight(3f).fillMaxWidth()) {
            WestArea(
                handState = boardState.handOf(Seat.WEST),
                isSelected = selectedSeat == Seat.WEST,
                onClick = { onSeatClick(Seat.WEST) },
                onBack = onBack,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .weight(3f)
                    .fillMaxHeight()
                    .padding(8.dp)
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    CameraPreview(
                        consumeScanRequest = { pendingScanRequest.get() },
                        frameDecoder = frameDecoder,
                        onScanProcessed = { scanOutcome ->
                            when (scanOutcome) {
                                is CameraScanOutcome.Decoded -> when (val decodeResult = scanOutcome.decodeResult) {
                                    is BarcodeDecodeResult.Found -> {
                                        val signature = decodeResult.signature.signatureFor(orientationMode)
                                        if (signature != null) {
                                            handleScan(signature)
                                        }
                                    }
                                    else -> {}
                                }
                                else -> {}
                            }
                        }
                    )
                    BarcodeGuideOverlay(guideSpec = adminScanGuideSpec)
                } else {
                    Text("Camera permission required", color = Color.White)
                }
            }
            HandArea(
                seat = Seat.EAST,
                handState = boardState.handOf(Seat.EAST),
                isSelected = selectedSeat == Seat.EAST,
                onClick = { onSeatClick(Seat.EAST) },
                modifier = Modifier.weight(1f)
            )
        }

        // Bottom Row: Feed Mode | South hand | Orientation | PBN
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            FeedModeArea(
                modifier = Modifier.weight(1f)
            )
            HandArea(
                seat = Seat.SOUTH,
                handState = boardState.handOf(Seat.SOUTH),
                isSelected = selectedSeat == Seat.SOUTH,
                onClick = { onSeatClick(Seat.SOUTH) },
                modifier = Modifier.weight(1f)
            )
            OrientationArea(
                currentMode = orientationMode,
                onModeChange = onOrientationModeChange,
                modifier = Modifier.weight(1f)
            )
            PBNArea(
                boardState = boardState,
                boardNumber = boardNumber,
                modifier = Modifier.weight(2f)
            )
        }
    }
}

@Composable
fun BoardControlsArea(
    boardNumber: Int,
    selectedSeat: Seat,
    boardState: org.itroboc.core.BoardState,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isHandEmpty = boardState.handOf(selectedSeat).count() == 0
    val clearLabel = if (isHandEmpty) "Clear board" else "Clear hand"

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Board: $boardNumber",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onClear,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
        ) {
            Text(clearLabel)
        }
    }
}

@Composable
fun BoardBackButtonArea(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onBack,
        modifier = modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
    ) {
        Text("Back")
    }
}


@Composable
fun HandContent(
    handState: HandState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        handState.cardsBySuitInBridgeOrder().forEach { suitCards ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = suitCards.suit.prettySymbol,
                    color = if (suitCards.suit == Suit.HEARTS || suitCards.suit == Suit.DIAMONDS) Color.Red else Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(18.dp)
                )
                Text(
                    text = if (suitCards.cards.isEmpty()) "—" else suitCards.cards.joinToString(" ") { it.rank.symbol.toString() },
                    fontSize = 14.sp,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
fun WestArea(
    handState: HandState,
    isSelected: Boolean,
    onClick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (handState.isComplete()) Color(0xFFC8E6C9) else Color(0xFFFFF9C4)
    val borderColor = if (isSelected) Color(0xFF2196F3) else Color.LightGray
    val borderWidth = if (isSelected) 3.dp else 1.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp)
            .background(backgroundColor)
            .border(borderWidth, borderColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BoardBackButtonArea(
                onBack = onBack,
                modifier = Modifier.padding(8.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            HandContent(handState = handState)

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}


@Composable
fun HandArea(
    seat: Seat,
    handState: HandState,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (handState.isComplete()) Color(0xFFC8E6C9) else Color(0xFFFFF9C4)
    val borderColor = if (isSelected) Color(0xFF2196F3) else Color.LightGray
    val borderWidth = if (isSelected) 3.dp else 1.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp)
            .background(backgroundColor)
            .border(borderWidth, borderColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        HandContent(handState = handState)
    }
}

@Composable
fun StatusArea(
    boardState: BoardState,
    orientationMode: BarcodeOrientationMode,
    message: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column {
            Text(
                "Status",
                color = Color(0xFF4CAF50),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            val totalCount = boardState.totalCardCount()
            Text(
                text = "Board progress: $totalCount/52 | Mode: ${orientationMode.label}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )

            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}


@Composable
fun LastScannedCardArea(cardId: CardId?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column {
            Text(
                "Last scanned card",
                color = Color(0xFF4CAF50),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            if (cardId != null) {
                Text(
                    text = "${cardId.suit.prettySymbol}${cardId.rank.symbol}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (cardId.suit == Suit.HEARTS || cardId.suit == Suit.DIAMONDS) Color.Red else Color.Black,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}


@Composable
fun PBNArea(
    boardState: BoardState,
    boardNumber: Int,
    modifier: Modifier = Modifier
) {
    val isComplete = boardState.totalCardCount() == 52
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column {
            Text(
                "PBN Preview",
                color = Color(0xFF4CAF50),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            if (isComplete) {
                val pbn = PbnExporter.export(boardState, PbnExportOptions(boardNumber = boardNumber))
                Text(
                    text = pbn,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Text(
                    text = "Complete board to see PBN.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}


@Composable
private fun CameraPreview(
    consumeScanRequest: () -> Boolean,
    frameDecoder: AdminEditCameraFrameDecoder,
    onScanProcessed: (CameraScanOutcome) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // Use rememberUpdatedState to avoid stale closures in the analyzer
    val currentConsumeScanRequest by rememberUpdatedState(consumeScanRequest)
    val currentOnScanProcessed by rememberUpdatedState(onScanProcessed)

    DisposableEffect(lifecycleOwner, cameraProviderFuture, analysisExecutor) {
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                imageProxy.use {
                    if (!currentConsumeScanRequest()) {
                        return@setAnalyzer
                    }

                    val scanOutcome = frameDecoder.decode(imageProxy)
                    mainExecutor.execute {
                        currentOnScanProcessed(scanOutcome)
                    }
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("CameraPreview", "Use case binding failed", e)
            }
        }, mainExecutor)

        onDispose {
            runCatching {
                cameraProviderFuture.get().unbindAll()
            }.onFailure { error ->
                Log.w("CameraPreview", "Failed to unbind camera on dispose", error)
            }
            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun BarcodeGuideOverlay(guideSpec: AdminScanGuideSpec) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 2.dp.toPx()
        val guideBounds = centeredGuideRectBounds(
            containerWidth = size.width,
            containerHeight = size.height,
            guideSpec = guideSpec,
        )

        drawRoundRect(
            color = Color.White,
            topLeft = Offset(guideBounds.left, guideBounds.top),
            size = Size(guideBounds.width, guideBounds.height),
            cornerRadius = CornerRadius(8.dp.toPx()),
            style = Stroke(width = strokeWidth)
        )
    }
}

@Composable
fun OrientationArea(
    currentMode: BarcodeOrientationMode,
    onModeChange: (BarcodeOrientationMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Barcode",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Row(modifier = Modifier.selectableGroup()) {
            BarcodeOrientationMode.entries.forEach { mode ->
                val enabled = mode != BarcodeOrientationMode.AUTO
                Row(
                    modifier = Modifier.selectable(
                        selected = currentMode == mode,
                        onClick = { if (enabled) onModeChange(mode) },
                        role = Role.RadioButton,
                        enabled = enabled
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentMode == mode,
                        onClick = null,
                        enabled = enabled
                    )
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) Color.Unspecified else Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun FeedModeArea(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Feed mode",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Row {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = true, onClick = null)
                Text("stream", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(0.5f)
            ) {
                RadioButton(selected = false, onClick = null, enabled = false)
                Text("snap", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        }
    }
}
