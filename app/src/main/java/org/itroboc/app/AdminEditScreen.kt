package org.itroboc.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.itroboc.vision.BarcodeDecodeResult
import org.itroboc.core.*
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

    var isReadOnly by remember(editor) { mutableStateOf(editor.hasAliasesForEveryCard()) }
    var autoAdvance by remember { mutableStateOf(true) }
    var lastResultMessage by remember { mutableStateOf<String?>(null) }
    var aliasDetailsDialog by remember { mutableStateOf<AdminAliasDetails?>(null) }
    var isDirty by remember { mutableStateOf(false) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    
    var frameDebugInfo by remember { mutableStateOf<FrameDebugInfo?>(null) }
    var cameraDecodeInfo by remember { mutableStateOf<String?>(null) }
    var scanRequested by remember { mutableStateOf(false) }
    var pendingScanCard by remember { mutableStateOf<CardId?>(null) }
    var debugLogStatus by remember { mutableStateOf<String?>(null) }
    val pendingScanRequest = remember { AtomicBoolean(false) }
    val frameDecoder = remember { CameraFrameDecoder() }
    val scanEvidenceByAlias = remember { mutableStateMapOf<String, AdminAliasScanEvidence>() }
    
    // Trigger recomposition when editor state changes
    var updateTrigger by remember { mutableIntStateOf(0) }

    val context = LocalContext.current
    val debugLogManager = remember(context) { AdminEditScanDebugLogManager(context) }
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

    LaunchedEffect(isReadOnly, hasCameraPermission) {
        if (!isReadOnly && !hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
        if (isReadOnly) {
            pendingScanRequest.set(false)
            scanRequested = false
            pendingScanCard = null
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

    fun applySignature(targetCard: CardId, rawSignature: String, detectionLabel: String) {
        if (isReadOnly) return
        val result = editor.assign(rawSignature, targetCard)
        lastResultMessage = when (result) {
            is DeckProfileEditResult.Assigned ->
                "$detectionLabel assigned to ${targetCard.prettyString}"
            is DeckProfileEditResult.AlreadyAssignedToSelected ->
                "$detectionLabel already exists for ${targetCard.prettyString}"
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

    val selectedCardAliases = editor.getAliases(selectedCard)
    val readOnlyAvailable = editor.hasAliasesForEveryCard()

    LaunchedEffect(readOnlyAvailable) {
        if (!readOnlyAvailable && isReadOnly) {
            isReadOnly = false
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Part: 52-card mapping grid (Stretched to fill height)
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Card Mapping",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isReadOnly,
                        onCheckedChange = { isReadOnly = it },
                        enabled = readOnlyAvailable
                    )
                    Text("Read only", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    Checkbox(
                        checked = autoAdvance,
                        onCheckedChange = { autoAdvance = it }
                    )
                    Text("Auto-advance", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (!readOnlyAvailable) {
                Text(
                    text = "Read only unlocks once all 52 cards have at least one alias.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                )
            }

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
                        Text(text = suit.prettySymbol, style = ItrobocTextStyles.AdminGridSuit)
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
                if (!isReadOnly && hasCameraPermission) {
                    BarcodeCameraScanner(
                        consumeScanRequest = { pendingScanRequest.compareAndSet(true, false) },
                        frameDecoder = frameDecoder,
                        onScanProcessed = { scanOutcome ->
                            val scanCard = pendingScanCard ?: selectedCard
                            frameDebugInfo = scanOutcome.frameDebugInfo
                            cameraDecodeInfo = scanOutcome.describe()
                            scanRequested = false
                            pendingScanCard = null
                            debugLogManager.appendScanRecord(
                                selectedCard = scanCard.prettyString,
                                outcome = scanOutcome,
                                deckProfileMatchCount = scanOutcome.deckProfileMatchCount(editor.toDeckProfile()),
                            )

                            when (scanOutcome) {
                                is CameraScanOutcome.Decoded -> when (val decodeResult = scanOutcome.decodeResult) {
                                    is BarcodeDecodeResult.Found -> {
                                        val signature = decodeResult.signature
                                        val detectionLabel = "Detected ${signature.rawSignature} (${signature.confidence.formatAsUiConfidence()})"
                                        scanEvidenceByAlias[signature.rawSignature] = signature.toAdminAliasScanEvidence()
                                        applySignature(
                                            targetCard = scanCard,
                                            rawSignature = signature.rawSignature,
                                            detectionLabel = detectionLabel,
                                        )
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
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (isReadOnly) {
                    AdminReadOnlyCardPreview(
                        card = selectedCard,
                        aliases = selectedCardAliases,
                    )
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
                Button(
                    onClick = {
                        pendingScanRequest.set(true)
                        scanRequested = true
                        pendingScanCard = selectedCard
                        cameraDecodeInfo = null
                        lastResultMessage = "Waiting for next camera frame..."
                        updateTrigger++ // Force UI refresh
                    },
                    enabled = !isReadOnly && hasCameraPermission,
                    modifier = Modifier.height(44.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp)
                ) {
                    Text("Scan", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(modifier = Modifier.width(16.dp))
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
                        applySignature(
                            targetCard = selectedCard,
                            rawSignature = mockSig,
                            detectionLabel = "Mock signature $mockSig",
                        )
                    },
                    enabled = !isReadOnly,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text("Mock", style = MaterialTheme.typography.labelSmall)
                }
            }
            val aliases = selectedCardAliases
            Text(
                text = if (aliases.isEmpty()) {
                    "Unmapped • Aliases: none"
                } else {
                    "Mapped • Aliases: ${aliases.joinToString()}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (aliases.isEmpty()) Color.Red else Color.Unspecified
            )
            if (aliases.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    aliases.forEach { alias ->
                        SuggestionChip(
                            onClick = {
                                aliasDetailsDialog = AdminAliasDetails(
                                    rawSignature = alias,
                                    evidence = scanEvidenceByAlias[alias],
                                )
                            },
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
            Text(
                text = "Log: ${debugLogManager.logFile.absolutePath}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
//            debugLogStatus?.let {
//                Text(
//                    text = it,
//                    style = MaterialTheme.typography.labelSmall,
//                    color = Color.Gray,
//                    maxLines = 2,
//                    overflow = TextOverflow.Ellipsis,
//                )
//            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        onSave(editor.toDeckProfile())
                        isDirty = false
                    },
                    enabled = !isReadOnly,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
                Button(
                    onClick = {
                        if (isDirty && !isReadOnly) {
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
            OutlinedButton(
                onClick = {
                    val shareIntent = debugLogManager.createShareIntent()
                    if (shareIntent == null) {
                        debugLogStatus = "No debug log to share yet."
                    } else {
                        try {
                            context.startActivity(Intent.createChooser(shareIntent, "Share scan debug log"))
                            debugLogStatus = "Opened Android share sheet for scan debug log."
                        } catch (_: ActivityNotFoundException) {
                            debugLogStatus = "No app available to share the scan debug log."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Share Debug Log")
            }
        }
    }

    aliasDetailsDialog?.let { details ->
        AlertDialog(
            onDismissRequest = { aliasDetailsDialog = null },
            title = { Text("Alias details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    details.displayLines().forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                if (!isReadOnly) {
                    TextButton(onClick = {
                        val alias = details.rawSignature
                        editor.remove(alias)
                        lastResultMessage = "Removed alias $alias"
                        isDirty = true
                        aliasDetailsDialog = null
                        updateTrigger++
                    }) {
                        Text("Remove")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { aliasDetailsDialog = null }) {
                    Text("Close")
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
            style = ItrobocTextStyles.AdminGridRank,
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

private fun CameraScanOutcome.deckProfileMatchCount(deckProfile: DeckProfile): Int = when (this) {
    is CameraScanOutcome.Decoded -> when (val result = decodeResult) {
        is BarcodeDecodeResult.Found -> if (deckProfile.lookup(result.signature.rawSignature) != null) 1 else 0
        is BarcodeDecodeResult.Ambiguous -> result.candidates
            .map { it.rawSignature }
            .distinct()
            .count { deckProfile.lookup(it) != null }
        is BarcodeDecodeResult.NotFound -> 0
    }
    is CameraScanOutcome.ConversionFailed -> 0
}

private fun Double.formatAsUiConfidence(): String = "%.2f".format(this)
