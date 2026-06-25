package org.itroboc.app

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
    // 3x3 Cockpit Layout using Row/Column
    Column(modifier = Modifier.fillMaxSize()) {
        // Top Row: Board controls | North hand | Status
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            BoardControlsArea(
                boardNumber = boardNumber,
                onBack = onBack,
                modifier = Modifier.weight(1f)
            )
            HandAreaPlaceholder(
                seatName = "North",
                modifier = Modifier.weight(1.5f)
            )
            StatusAreaPlaceholder(
                modifier = Modifier.weight(1f)
            )
        }

        // Middle Row: West hand | Camera Area | East hand
        Row(modifier = Modifier.weight(1.5f).fillMaxWidth()) {
            HandAreaPlaceholder(
                seatName = "West",
                modifier = Modifier.weight(1f)
            )
            CameraAreaPlaceholder(
                modifier = Modifier.weight(2f)
            )
            HandAreaPlaceholder(
                seatName = "East",
                modifier = Modifier.weight(1f)
            )
        }

        // Bottom Row: Orientation | South hand | Feed mode
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            OrientationArea(
                currentMode = orientationMode,
                onModeChange = onOrientationModeChange,
                modifier = Modifier.weight(1f)
            )
            HandAreaPlaceholder(
                seatName = "South",
                modifier = Modifier.weight(1.5f)
            )
            FeedModeArea(
                modifier = Modifier.weight(1f)
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
        Button(
            onClick = { /* TODO: Ticket 4 */ },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
        ) {
            Text("Clear")
        }
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
        ) {
            Text("Back")
        }
    }
}

@Composable
fun HandAreaPlaceholder(
    seatName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp)
            .background(Color(0xFFE0E0E0))
            .border(1.dp, Color.LightGray),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(seatName, style = MaterialTheme.typography.labelLarge)
            Text("Hand Placeholder", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            // Visual stacked suits from sketch
            Column(modifier = Modifier.padding(top = 4.dp)) {
                Text("♠", color = Color.Black, fontSize = 12.sp)
                Text("♥", color = Color.Red, fontSize = 12.sp)
                Text("♦", color = Color.Red, fontSize = 12.sp)
                Text("♣", color = Color.Black, fontSize = 12.sp)
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
            "Barcode orientation",
            style = MaterialTheme.typography.titleSmall,
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
