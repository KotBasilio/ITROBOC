package org.itroboc.app

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.itroboc.vision.BarcodeDecodeResult
import org.itroboc.core.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminEditScreen(
    editor: DeckProfileEditor,
    onSave: (DeckProfile) -> Unit,
    onBack: () -> Unit
) {
    var selectedSuit by remember { mutableStateOf(Suit.SPADES) }
    var selectedRank by remember { mutableStateOf(Rank.ACE) }
    val selectedCard = CardId(selectedSuit, selectedRank)
    
    var autoAdvance by remember { mutableStateOf(true) }
    var lastResultMessage by remember { mutableStateOf<String?>(null) }
    var aliasPendingRemoval by remember { mutableStateOf<String?>(null) }
    var isDirty by remember { mutableStateOf(false) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    
    var frameDebugInfo by remember { mutableStateOf<FrameDebugInfo?>(null) }
    var cameraDecodeInfo by remember { mutableStateOf<String?>(null) }
    var scanRequested by remember { mutableStateOf(false) }
    val pendingScanRequest = remember { AtomicBoolean(false) }
    val frameDecoder = remember { AdminEditCameraFrameDecoder() }
    
    // Trigger recomposition when editor state changes
    var updateTrigger by remember { mutableIntStateOf(0) }

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

    fun advanceToNextUnmappedCardOrReportCompletion() {
        val allCards = Suit.entries.flatMap { suit ->
            Rank.entries.map { rank -> CardId(suit, rank) }
        }
        val nextUnmapped = allCards.firstOrNull { !editor.isMapped(it) }
        if (nextUnmapped != null) {
            selectedSuit = nextUnmapped.suit
            selectedRank = nextUnmapped.rank
        } else {
            lastResultMessage = "Profile complete! All 52 cards mapped."
        }
    }

    fun applySignature(rawSignature: String, detectionLabel: String) {
        val result = editor.assign(rawSignature, selectedCard)
        lastResultMessage = when (result) {
            is DeckProfileEditResult.Assigned ->
                "$detectionLabel assigned to ${selectedCard.prettyString}"
            is DeckProfileEditResult.AlreadyAssignedToSelected ->
                "$detectionLabel already exists for ${selectedCard.prettyString}"
            is DeckProfileEditResult.SignatureConflict ->
                "$detectionLabel CONFLICT: already mapped to ${result.existingCard.prettyString}"
            else -> "No card selected"
        }

        if (result is DeckProfileEditResult.Assigned && autoAdvance) {
            advanceToNextUnmappedCardOrReportCompletion()
        }
        if (result is DeckProfileEditResult.Assigned) {
            isDirty = true
        }
        updateTrigger++
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Part: 52-card mapping grid (Stretched to fill height)
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .padding(8.dp)
        ) {
            Text(
                text = "Card Mapping",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            val suits = Suit.entries
            val ranks = Rank.entries

            // Header row
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                suits.forEach { suit ->
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = suit.prettySymbol, fontSize = 20.sp)
                    }
                }
            }

            // Card rows (13 rows)
            ranks.forEach { rank ->
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    suits.forEach { suit ->
                        val card = CardId(suit, rank)
                        val isMapped = editor.isMapped(card)
                        val isSelected = card == selectedCard
                        
                        Box(modifier = Modifier.weight(1f)) {
                            CardButton(
                                card = card,
                                isMapped = isMapped,
                                isSelected = isSelected,
                                onClick = {
                                    selectedSuit = suit
                                    selectedRank = rank
                                }
                            )
                        }
                    }
                }
            }
        }

        // Right Part: Scanner and controls
        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Camera Preview area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .background(Color.DarkGray, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    CameraPreview(
                        consumeScanRequest = { pendingScanRequest.compareAndSet(true, false) },
                        frameDecoder = frameDecoder,
                        onScanProcessed = { scanOutcome ->
                            frameDebugInfo = scanOutcome.frameDebugInfo
                            cameraDecodeInfo = scanOutcome.describe()
                            scanRequested = false

                            when (scanOutcome) {
                                is CameraScanOutcome.Decoded -> when (val decodeResult = scanOutcome.decodeResult) {
                                    is BarcodeDecodeResult.Found -> {
                                        val signature = decodeResult.signature
                                        val detectionLabel = "Detected ${signature.rawSignature} (${signature.confidence.formatAsUiConfidence()})"
                                        applySignature(signature.rawSignature, detectionLabel)
                                    }
                                    is BarcodeDecodeResult.NotFound -> {
                                        lastResultMessage = decodeResult.reason
                                    }
                                    is BarcodeDecodeResult.Ambiguous -> {
                                        lastResultMessage = scanOutcome.describe()
                                    }
                                }
                                is CameraScanOutcome.ConversionFailed -> {
                                    lastResultMessage = scanOutcome.reason
                                }
                            }
                        }
                    )
                    BarcodeGuideOverlay(guideRectSpec = adminEditGuideRectSpec)
                } else {
                    Text(
                        text = "Camera permission is required for scanning.",
                        color = Color.LightGray,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Selected card status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Selected: ${selectedCard.prettyString}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = {
                        val mockSig = "0x${(1000..9999).random().toString(16).uppercase()}"
                        cameraDecodeInfo = "Mock fallback used"
                        applySignature(mockSig, "Mock signature $mockSig")
                    },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text("Mock", style = MaterialTheme.typography.labelSmall)
                }
            }
            val aliases = editor.getAliases(selectedCard)
            Text(
                text = if (aliases.isEmpty()) "Unmapped" else "Mapped",
                style = MaterialTheme.typography.bodyMedium,
                color = if (aliases.isEmpty()) Color.Red else Color.Unspecified
            )
            if (aliases.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Aliases",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    aliases.forEach { alias ->
                        SuggestionChip(
                            onClick = { aliasPendingRemoval = alias },
                            label = { Text(alias) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Controls
            frameDebugInfo?.let {
                Text(
                    text = it.describe(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            cameraDecodeInfo?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            lastResultMessage?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = autoAdvance, onCheckedChange = { autoAdvance = it })
                Text("Auto-advance", style = MaterialTheme.typography.labelMedium)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        pendingScanRequest.set(true)
                        scanRequested = true
                        cameraDecodeInfo = null
                        lastResultMessage = "Waiting for next camera frame..."
                        updateTrigger++ // Force UI refresh
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Scan")
                }
                Button(
                    onClick = {
                        onSave(editor.toDeckProfile())
                        isDirty = false
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
                Button(
                    onClick = {
                        if (isDirty) {
                            showUnsavedChangesDialog = true
                        } else {
                            onBack()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Back")
                }
            }
        }
    }

    aliasPendingRemoval?.let { alias ->
        AlertDialog(
            onDismissRequest = { aliasPendingRemoval = null },
            title = { Text("Remove alias") },
            text = { Text("Remove alias $alias from ${selectedCard.prettyString}?") },
            confirmButton = {
                TextButton(onClick = {
                    editor.remove(alias)
                    lastResultMessage = "Removed alias $alias"
                    isDirty = true
                    aliasPendingRemoval = null
                    updateTrigger++
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { aliasPendingRemoval = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            title = { Text("Profile was modified.") },
            text = { Text("Save changes?") },
            confirmButton = {
                TextButton(onClick = {
                    onSave(editor.toDeckProfile())
                    isDirty = false
                    showUnsavedChangesDialog = false
                    onBack()
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    isDirty = false
                    showUnsavedChangesDialog = false
                    onBack()
                }) {
                    Text("Discard")
                }
            }
        )
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

    DisposableEffect(lifecycleOwner, cameraProviderFuture, analysisExecutor) {
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                try {
                    if (!consumeScanRequest()) {
                        return@setAnalyzer
                    }

                    val scanOutcome = frameDecoder.decode(imageProxy)
                    mainExecutor.execute {
                        onScanProcessed(scanOutcome)
                    }
                } finally {
                    imageProxy.close()
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
private fun BarcodeGuideOverlay(guideRectSpec: GuideRectSpec) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 2.dp.toPx()
        val guideBounds = centeredGuideRectBounds(
            containerWidth = size.width,
            containerHeight = size.height,
            guideRectSpec = guideRectSpec,
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
fun CardButton(
    card: CardId,
    isMapped: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isMapped) Color(0xFF4CAF50) else Color(0xFFFFEB3B)
    val borderColor = if (isSelected) Color.Black else Color.Transparent
    val borderWidth = if (isSelected) 3.dp else 0.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = card.rank.symbol.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color.Black
        )
    }
}

private val CardId.prettyString: String
    get() = "${suit.prettySymbol}${rank.symbol}"

private val Suit.prettySymbol: String
    get() = when (this) {
        Suit.SPADES -> "♠"
        Suit.HEARTS -> "♥"
        Suit.DIAMONDS -> "♦"
        Suit.CLUBS -> "♣"
    }

private fun Double.formatAsUiConfidence(): String = "%.2f".format(this)
