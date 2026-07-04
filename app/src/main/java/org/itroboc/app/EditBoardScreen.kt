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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import kotlinx.coroutines.delay
import org.itroboc.core.*
import org.itroboc.vision.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@ComposePreview(device = Devices.AUTOMOTIVE_1024p, widthDp = 1024, heightDp = 600, showBackground = true)
@Composable
fun EditBoardScreenPreview() {
    MaterialTheme {
        EditBoardScreen(
            boardEditState = BoardEditState(boardNumber = 7),
            deckProfile = BuiltInDeckProfiles.defaultProfile(),
            orientationMode = BarcodeOrientationMode.BRM,
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
    val isBoardComplete = BoardProgressSummary.from(boardState).boardComplete
    val currentIsBoardComplete by rememberUpdatedState(isBoardComplete)
    val currentBoardEditState by rememberUpdatedState(boardEditState)
    val currentDeckProfile by rememberUpdatedState(deckProfile)

    var showClearBoardDialog by remember { mutableStateOf(false) }
    var showScissorsScreen by remember { mutableStateOf(false) }
    var showSwapScreen by remember { mutableStateOf(false) }
    var lastResultMessage by remember { mutableStateOf<String?>(null) }
    var lastScannedCard by remember { mutableStateOf<CardId?>(null) }
    
    // EBT-4: Debounce state
    var lastRawSignature by remember { mutableStateOf<String?>(null) }
    var lastScanTimeMillis by remember { mutableLongStateOf(0L) }
    val debounceWindowMillis = 1000L

    // EBT-T4: Unknown signature throttling
    var unknownSignatureCount by remember { mutableIntStateOf(0) }
    var lastUnknownMessageTimeMillis by remember { mutableLongStateOf(0L) }
    val unknownThrottleMillis = 3000L

    // EBT-8: Scan rate measurement using inter-scan deltas.
    var scanDeltas by remember { mutableStateOf<List<Long>>(emptyList()) }
    var scansPerSecond by remember { mutableDoubleStateOf(0.0) }
    var scansIdleCount by remember { mutableLongStateOf(0L) }
    val initialNow = System.currentTimeMillis()
    var lastScanTimestamp by remember { mutableStateOf(initialNow) }

    LaunchedEffect(Unit) {
        while (true) {
            if (currentIsBoardComplete) {
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
            delay(500L)
        }
    }

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
    val frameDecoder = remember { AdminEditCameraFrameDecoder(decoder = Grid13VerdictDecoder()) }

    fun onSeatClick(seat: Seat) {
        val nextBoardEditState = boardEditState.copy(selectedSeat = seat)
        val update = EditBoardReducer.tryAutoFillFourthHand(nextBoardEditState)
        onBoardEditStateChange(update.state)
        if (update.message != null) {
            lastResultMessage = update.message
        }
    }

    fun handleScan(signature: String) {
        if (currentIsBoardComplete) {
            return
        }

        val now = System.currentTimeMillis()
        if (signature == lastRawSignature && (now - lastScanTimeMillis) < debounceWindowMillis) {
            return
        }
        lastRawSignature = signature
        lastScanTimeMillis = now

        val update = EditBoardReducer.applyScannedCard(currentBoardEditState, currentDeckProfile.lookup(signature) ?: run {
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

    fun onClearClick() {
        val selectedHand = boardState.handOf(selectedSeat)
        if (selectedHand.count() > 0) {
            val update = EditBoardReducer.clearSelectedHand(boardEditState)
            onBoardEditStateChange(update.state)
            lastResultMessage = update.message
            lastScannedCard = null
        } else {
            showClearBoardDialog = true
        }
    }

    fun handleCameraScan(scanOutcome: CameraScanOutcome) {
        val now = System.currentTimeMillis()
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

    // 3 rows Cockpit Layout using Row/Column: 4+3+4
    Column(modifier = Modifier.fillMaxSize()) {
        // Top Row: Board controls | North hand | Last Scanned | Status
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            BoardControlsArea(
                boardNumber = boardNumber,
                selectedSeat = selectedSeat,
                boardState = boardState,
                onBack = onBack,
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
                duplicateOverrideCandidate = boardEditState.duplicateOverrideCandidate,
                modifier = Modifier.weight(0.7f)
            )
            StatusArea(
                boardState = boardState,
                orientationMode = orientationMode,
                message = lastResultMessage,
                sps = scansPerSecond,
                modifier = Modifier.weight(2.3f)
            )
        }

        // Middle Row: West hand | Camera Area | East hand
        Row(modifier = Modifier.weight(3f).fillMaxWidth()) {
            WestArea(
                handState = boardState.handOf(Seat.WEST),
                isSelected = selectedSeat == Seat.WEST,
                onClick = { onSeatClick(Seat.WEST) },
                onClear = { onClearClick() },
                onSwap = { showSwapScreen = true },
                canSwap = boardState.handOf(selectedSeat).count() > 0,
                modifier = Modifier.weight(1f)
            )
            CentralArea(
                hasCameraPermission = hasCameraPermission,
                showScissorsScreen = showScissorsScreen,
                showSwapScreen = showSwapScreen,
                isBoardComplete = isBoardComplete,
                boardState = boardState,
                boardNumber = boardNumber,
                pendingScanRequest = pendingScanRequest,
                frameDecoder = frameDecoder,
                onScanProcessed = ::handleCameraScan,
                modifier = Modifier.weight(3f)
            )
            EastArea(
                handState = boardState.handOf(Seat.EAST),
                isSelected = selectedSeat == Seat.EAST,
                onClick = { onSeatClick(Seat.EAST) },
                onScissors = { showScissorsScreen = true },
                canScissors = boardState.handOf(selectedSeat).count() > 0,
                onUndo = {
                    val update = EditBoardReducer.undoAddForSelectedHand(boardEditState)
                    onBoardEditStateChange(update.state)
                    lastResultMessage = update.message
                },
                canUndo = boardEditState.addHistory.any { it.seat == selectedSeat },
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
                modifier = Modifier.weight(1.5f)
            )
            SureArea(
                onImSure = {
                    val update = EditBoardReducer.confirmDuplicateOverride(boardEditState)
                    onBoardEditStateChange(update.state)
                    lastResultMessage = update.message
                },
                canImSure = boardEditState.duplicateOverrideCandidate?.targetSeat == selectedSeat,
                modifier = Modifier.weight(1.5f)
            )
        }
    }

    if (showScissorsScreen) {
        ScissorsScreen(
            seat = selectedSeat,
            handState = boardState.handOf(selectedSeat),
            onDismiss = { showScissorsScreen = false },
            onRemoveCard = { card ->
                val update = EditBoardReducer.removeCardFromSelectedHand(boardEditState, card)
                onBoardEditStateChange(update.state)
                lastResultMessage = update.message
            }
        )
    }

    if (showSwapScreen) {
        SwapScreen(
            currentSeat = selectedSeat,
            onDismiss = { showSwapScreen = false },
            onSwapWith = { targetSeat ->
                val update = EditBoardReducer.swapSelectedHandWith(boardEditState, targetSeat)
                onBoardEditStateChange(update.state)
                lastResultMessage = update.message
                showSwapScreen = false
            }
        )
    }

    if (showClearBoardDialog) {
        AlertDialog(
            onDismissRequest = { showClearBoardDialog = false },
            title = { Text("Clear entire board?") },
            text = { Text("This removes all cards from Board $boardNumber.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val update = EditBoardReducer.clearBoard(boardEditState)
                        onBoardEditStateChange(update.state)
                        lastResultMessage = update.message
                        lastScannedCard = null
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

}

@Composable
internal fun CentralArea(
    hasCameraPermission: Boolean,
    showScissorsScreen: Boolean,
    showSwapScreen: Boolean,
    isBoardComplete: Boolean,
    boardState: BoardState,
    boardNumber: Int,
    pendingScanRequest: AtomicBoolean,
    frameDecoder: AdminEditCameraFrameDecoder,
    onScanProcessed: (CameraScanOutcome) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(8.dp)
            .background(Color.DarkGray),
        contentAlignment = Alignment.Center
    ) {
        if (!hasCameraPermission) {
            Text("Camera permission required", color = Color.White)
        } else if (showScissorsScreen || showSwapScreen) {
            Text("Modal screen is coming", color = Color.White)
        } else if (isBoardComplete) {
            BoardCompleteView(boardState = boardState, boardNumber = boardNumber)
        } else {
            CameraPreview(
                consumeScanRequest = { pendingScanRequest.get() },
                frameDecoder = frameDecoder,
                onScanProcessed = onScanProcessed
            )
            BarcodeGuideOverlay(guideSpec = adminScanGuideSpec)
        }
    }
}

@Composable
fun BoardCompleteView(boardState: BoardState, boardNumber: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1B5E20).copy(alpha = 0.9f))
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Board Complete",
            color = Color.White,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(24.dp))
        val pbn = TdSessionExchange.exportCompleteBoard(
            boardState = boardState,
            boardNumber = boardNumber,
        )
        Surface(
            color = Color.Black.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = pbn,
                color = Color(0xFF81C784),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(16.dp),
                lineHeight = 24.sp
            )
        }
    }
}

@Composable
fun BoardControlsArea(
    boardNumber: Int,
    selectedSeat: Seat,
    boardState: org.itroboc.core.BoardState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Board: $boardNumber",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, fontSize = 32.sp,
        )

        Button(
            onClick = onBack,
            modifier = modifier.fillMaxWidth().aspectRatio(3f),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
        ) {
            Text("Back", fontSize = 32.sp)
        }
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
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(22.dp)
                )
                Text(
                    text = " " + if (suitCards.cards.isEmpty()) "—" else suitCards.cards.joinToString("") { it.rank.symbol.toString() },
                    fontSize = 24.sp,
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
    onClear: () -> Unit,
    onSwap: () -> Unit,
    canSwap: Boolean,
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
            Button(
                onClick = onClear,
                modifier = Modifier.padding(8.dp).fillMaxWidth().aspectRatio(3f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
            ) {
                val clearLabel = "Clear" // old version: if (isHandEmpty) "Clear board" else "Clear hand"
                // val isHandEmpty = boardState.handOf(selectedSeat).count() == 0
                Text(clearLabel, fontSize = 32.sp)
            }

            Spacer(modifier = Modifier.weight(1f))

            HandContent(handState = handState)

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onSwap,
                enabled = canSwap,
                modifier = Modifier.padding(8.dp).fillMaxWidth().aspectRatio(3f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
            ) {
                Text("Swap", fontSize = 32.sp)
            }
        }
    }
}

@Composable
fun EastArea(
    handState: HandState,
    isSelected: Boolean,
    onClick: () -> Unit,
    onScissors: () -> Unit,
    canScissors: Boolean,
    onUndo: () -> Unit,
    canUndo: Boolean,
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
            Button(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier.padding(8.dp).fillMaxWidth().aspectRatio(3f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
            ) {
                Text("Undo", fontSize = 32.sp)
            }

            Spacer(modifier = Modifier.weight(1f))

            HandContent(handState = handState)

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onScissors,
                enabled = canScissors,
                modifier = Modifier.padding(8.dp).fillMaxWidth().aspectRatio(3f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
            ) {
                Text("Scissors", fontSize = 32.sp)
            }
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
    sps: Double,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column {
            val totalCount = boardState.totalCardCount()
            Text(
                text = "Cards: $totalCount/52",
                fontSize = 40.sp,
                color = Color(0xFF4CAF50),
                style = MaterialTheme.typography.titleMedium,
            )

            val fpsText = if (sps > 0.0) "FPS %4.1f".format(sps) else "IDLE %.0f".format(- sps)
            Text(
                text = "Mode: ${orientationMode.label} | " + fpsText,
                fontSize = 24.sp,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )

            if (message != null) {
                Text(
                    text = message,
                    fontSize = 24.sp,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Black,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}


@Composable
fun LastScannedCardArea(
    cardId: CardId?,
    duplicateOverrideCandidate: DuplicateOverrideCandidate?,
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
                "Last scanned",
                color = Color(0xFF4CAF50),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            val displayCard = cardId ?: duplicateOverrideCandidate?.card
            if (displayCard != null) {
                Text(
                    text = "${displayCard.suit.prettySymbol}${displayCard.rank.symbol}",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 50.sp,
                    color = if (displayCard.suit == Suit.HEARTS || displayCard.suit == Suit.DIAMONDS) Color.Red else Color.Black,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}


@Composable
fun SureArea(
    onImSure: () -> Unit,
    canImSure: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onImSure,
        enabled = canImSure,
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
    ) {
        Text("I'm sure", fontSize = 32.sp)
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
        var reusableRoiPixels = ByteArray(0)

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

                    val roi = centeredBarcodeRoi(
                        imageWidth = imageProxy.width,
                        imageHeight = imageProxy.height,
                        guideSpec = adminScanGuideSpec,
                    )
                    val requiredSize = roi.width * roi.height
                    if (reusableRoiPixels.size != requiredSize) {
                        reusableRoiPixels = ByteArray(requiredSize)
                    }

                    val scanOutcome = frameDecoder.decode(imageProxy, reusableRoiPixels)
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
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Barcode",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, fontSize = 30.sp,
        )
        Spacer(modifier = Modifier.height(10.dp))
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
                        fontSize = 28.sp,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) Color.Unspecified else Color.Gray
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
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
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Feed mode",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, fontSize = 30.sp,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(1.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = true, onClick = null)
                Text("stream", fontSize = 28.sp, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(0.5f)) {
                RadioButton(selected = false, onClick = null, enabled = false)
                Text(
                    "snap",
                    fontSize = 28.sp,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
fun ScissorsScreen(
    seat: Seat,
    handState: HandState,
    onDismiss: () -> Unit,
    onRemoveCard: (CardId) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Scissors for ${seat.displayName}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Click on cards to remove them",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Normal,
            )

            Spacer(modifier = Modifier.height(60.dp))

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                handState.cardsBySuitInBridgeOrder().forEach { suitCards ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = suitCards.suit.prettySymbol,
                            color = if (suitCards.suit == Suit.HEARTS || suitCards.suit == Suit.DIAMONDS) Color.Red else Color.Black,
                            fontSize = 50.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(44.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        suitCards.cards.forEach { card ->
                            Text(
                                text = card.rank.symbol.toString(),
                                fontSize = 50.sp,
                                color = Color.Black,
                                modifier = Modifier
                                    .clickable { onRemoveCard(card) }
                                    .padding(horizontal = 12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(60.dp))

            Button(onClick = onDismiss) {
                Text( fontSize = 32.sp, text = "Close" )
            }

        }
    }
}

@Composable
fun SwapScreen(
    currentSeat: Seat,
    onDismiss: () -> Unit,
    onSwapWith: (Seat) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Swap ${currentSeat.displayName} with...",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            Seat.entries.filter { it != currentSeat }.forEach { seat ->
                Button(
                    onClick = { onSwapWith(seat) },
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .padding(vertical = 8.dp)
                        .height(80.dp)
                ) {
                    Text(text = seat.displayName, fontSize = 40.sp)
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(onClick = onDismiss) {
                Text( fontSize = 32.sp, text = "Cancel" )
            }
        }
    }
}
