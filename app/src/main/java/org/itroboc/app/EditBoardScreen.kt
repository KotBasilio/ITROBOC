package org.itroboc.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Devices
import org.itroboc.core.*

@Preview(device = Devices.AUTOMOTIVE_1024p, widthDp = 1024, heightDp = 600, showBackground = true)
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

    fun onSeatClick(seat: Seat) {
        onBoardEditStateChange(boardEditState.copy(selectedSeat = seat))
    }

    fun handleScan(signature: String) {
        val accumulator = TdScanAccumulator(deckProfile, boardState)
        val report = accumulator.scan(selectedSeat, signature)
        
        onBoardEditStateChange(boardEditState.copy(boardState = report.accumulator.boardState))
        
        lastResultMessage = when (val result = report.result) {
            is TdScanResult.Added -> {
                lastScannedCard = result.card
                "Added ${result.card} to ${selectedSeat.displayName}."
            }
            is TdScanResult.AlreadyInThisHand -> {
                lastScannedCard = result.card
                "Already in ${selectedSeat.displayName}: ${result.card}."
            }
            is TdScanResult.AlreadyOnBoard -> {
                lastScannedCard = result.card
                "Already in ${result.existingSeat.displayName}: ${result.card}. No change."
            }
            is TdScanResult.UnknownSignature -> "Unknown signature: ${result.signature}."
            is TdScanResult.HandAlreadyComplete -> "Hand ${result.seat.displayName} already complete."
        }
    }

    fun onClearClick() {
        val selectedHand = boardState.handOf(selectedSeat)
        if (selectedHand.count() > 0) {
            // Clear only hand
            val newHands = Seat.entries.associateWith { seat ->
                if (seat == selectedSeat) org.itroboc.core.HandState() else boardState.handOf(seat)
            }
            onBoardEditStateChange(boardEditState.copy(boardState = org.itroboc.core.BoardState(newHands)))
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
                        onBoardEditStateChange(boardEditState.copy(boardState = org.itroboc.core.BoardState()))
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
            CameraAreaPlaceholder(
                onMockScan = {
                    // Pick a random card not on board if possible, or just a random card
                    val allCards = Suit.entries.flatMap { s -> Rank.entries.map { r -> CardId(s, r) } }
                    val available = allCards.filter { boardState.seatContaining(it) == null }
                    val targetCard = available.randomOrNull() ?: allCards.random()
                    // Find a signature for this card in the profile
                    val signature = deckProfile.getAliases(targetCard).firstOrNull() 
                        ?: "mock-${targetCard.suit.symbol}${targetCard.rank.symbol}"
                    handleScan(signature)
                },
                modifier = Modifier.weight(3f)
            )
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
            PBNAreaPlaceholder(
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
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
    ) {
        Text("Back")
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

            Column(
                modifier = Modifier.padding(8.dp),
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
        Column(
            modifier = Modifier.padding(8.dp),
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
}

@Composable
fun StatusArea(message: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column {
            Text(
                "Status area",
                color = Color(0xFF4CAF50),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
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
fun CameraAreaPlaceholder(onMockScan: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
            .border(2.dp, Color(0xFF4CAF50))
            .clickable { onMockScan() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Camera area\n(Tap to mock scan)",
            color = Color(0xFF4CAF50),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}


@Composable
fun PBNAreaPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Text(
            "PBN area",
            color = Color(0xFF4CAF50),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
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
                Row(
                    modifier = Modifier.selectable(
                        selected = currentMode == mode,
                        onClick = { onModeChange(mode) },
                        role = Role.RadioButton,
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = currentMode == mode, onClick = null)
                    Text(mode.label, style = MaterialTheme.typography.bodySmall)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = false, onClick = null)
                Text("snap", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
