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
import org.itroboc.core.HandState
import org.itroboc.core.Seat
import org.itroboc.core.Suit

@Preview(device = Devices.AUTOMOTIVE_1024p, widthDp = 1024, heightDp = 600, showBackground = true)
@Composable
fun EditBoardScreenPreview() {
    MaterialTheme {
        EditBoardScreen(
            boardEditState = BoardEditState(boardNumber = 7),
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
    orientationMode: BarcodeOrientationMode,
    onOrientationModeChange: (BarcodeOrientationMode) -> Unit,
    onBoardEditStateChange: (BoardEditState) -> Unit,
    onBack: () -> Unit
) {
    val boardNumber = boardEditState.boardNumber
    val boardState = boardEditState.boardState
    val selectedSeat = boardEditState.selectedSeat

    fun onSeatClick(seat: Seat) {
        onBoardEditStateChange(boardEditState.copy(selectedSeat = seat))
    }

    // 3x3 Cockpit Layout using Row/Column
    Column(modifier = Modifier.fillMaxSize()) {
        // Top Row: Board controls | North hand | Last Scanned | Status
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            BoardControlsArea(
                boardNumber = boardNumber,
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
            LastScannedCardAreaPlaceholder(
                modifier = Modifier.weight(1f)
            )
            StatusAreaPlaceholder(
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            onClick = { /* TODO: Ticket 4 */ },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
        ) {
            Text("Clear")
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
fun StatusAreaPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Text(
            "Status area",
            color = Color(0xFF4CAF50),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
    }
}


@Composable
fun LastScannedCardAreaPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Text(
            "Last scanned card",
            color = Color(0xFF4CAF50),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
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
fun CameraAreaPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
            .border(2.dp, Color(0xFF4CAF50)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Camera area",
            color = Color(0xFF4CAF50),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
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
