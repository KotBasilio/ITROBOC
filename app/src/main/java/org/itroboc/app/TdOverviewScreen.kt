package org.itroboc.app

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.itroboc.core.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class BoardUiStatus {
    Empty,
    Partial,
    Complete
}

@Composable
fun TdOverviewScreen(
    sessionState: TdSessionState,
    activeProfile: ProfileListItem = BuiltInDeckProfiles.demoBridge52().metadata.toProfileListItem(),
    onSessionStateChange: (TdSessionState) -> Unit,
    onNavigateToBoard: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val shareManager = remember(context) { TdSessionShareManager(context.applicationContext) }
    val latestSessionState by rememberUpdatedState(sessionState)
    val latestOnSessionStateChange by rememberUpdatedState(onSessionStateChange)
    var messageToDisplay by remember { mutableStateOf<String?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            try {
                val exportText = TdSessionExchange.exportCompleteBoards(latestSessionState)
                if (exportText != null) {
                    val stream = context.contentResolver.openOutputStream(it)
                    if (stream == null) {
                        messageToDisplay = "Failed to save: could not open destination."
                    } else {
                        stream.use { it.write(exportText.toByteArray(Charsets.UTF_8)) }
                        messageToDisplay = "Session saved successfully."
                    }
                }
            } catch (e: Exception) {
                messageToDisplay = "Failed to save: ${e.message}"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val importedText = stream.bufferedReader().use { reader -> reader.readText() }
                    val result = TdSessionExchange.importCumulative(latestSessionState, importedText)
                    latestOnSessionStateChange(result.sessionState)
                    messageToDisplay = when {
                        result.importedBoardNumbers.isEmpty() ->
                            "No complete boards found to import."
                        result.ignoredBlockCount > 0 ->
                            "Imported boards ${result.importedBoardNumbers.joinToString()} and ignored ${result.ignoredBlockCount} empty or partial block(s)."
                        else ->
                            "Imported boards ${result.importedBoardNumbers.joinToString()}."
                    }
                }
            } catch (e: Exception) {
                messageToDisplay = "Failed to import boards: ${e.message}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TD Actions",
                style = ItrobocTextStyles.BigVisible
            )
            Button(onClick = onBack) {
                Text("Back", style = ItrobocTextStyles.BigVisible)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Dynamic-column Grid based on totalBoardsInGrid
        val columns = sessionState.totalBoardsInGrid / 3
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sessionState.totalBoardsInGrid) { index ->
                val boardNumber = index + 1
                val boardEditState = sessionState.boards[boardNumber]
                val boardState = boardEditState?.boardState
                
                val status = when {
                    boardState == null || boardState.totalCardCount() == 0 -> BoardUiStatus.Empty
                    BoardProgressSummary.from(boardState).boardComplete -> BoardUiStatus.Complete
                    else -> BoardUiStatus.Partial
                }
                
                BoardButton(
                    number = boardNumber,
                    totalBoards = sessionState.totalBoardsInGrid,
                    status = status,
                    onClick = { onNavigateToBoard(boardNumber) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Session status text area
        val completedBoards = sessionState.boards.values.filter { 
            it.boardNumber <= sessionState.totalBoardsInGrid &&
            BoardProgressSummary.from(it.boardState).boardComplete 
        }
        val filledCount = completedBoards.size
        Text(
            text = "Filled: $filledCount/${sessionState.totalBoardsInGrid}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Active profile: ${activeProfile.displayName}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Session buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    importLauncher.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
                },
                modifier = Modifier.weight(1f).height(56.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Import", style = ItrobocTextStyles.BigVisible)
            }
            Button(
                onClick = {
                    val exportText = TdSessionExchange.exportCompleteBoards(sessionState)
                    if (exportText == null) {
                        messageToDisplay = "No complete boards available for export."
                        return@Button
                    }

                    try {
                        val shareIntent = shareManager.createShareIntent(exportText)
                        context.startActivity(Intent.createChooser(shareIntent, "Export TD boards"))
                        messageToDisplay = "Opened Android share sheet for TD board export."
                    } catch (_: ActivityNotFoundException) {
                        messageToDisplay = "No app available to share the TD board export."
                    }
                },
                modifier = Modifier.weight(1f).height(56.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Export", style = ItrobocTextStyles.BigVisible)
            }
            Button(
                onClick = {
                    val exportText = TdSessionExchange.exportCompleteBoards(sessionState)
                    if (exportText == null) {
                        messageToDisplay = "No complete boards available to save."
                        return@Button
                    }
                    saveLauncher.launch(generatePbnFilename())
                },
                modifier = Modifier.weight(1f).height(56.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Save", style = ItrobocTextStyles.BigVisible)
            }
            Button(
                onClick = { showSettingsDialog = true },
                modifier = Modifier.weight(1f).height(56.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Settings", style = ItrobocTextStyles.BigVisible)
            }
        }
    }

    if (showSettingsDialog) {
        TdSettingsScreen(
            currentSize = sessionState.totalBoardsInGrid,
            minAllowedSize = sessionState.highestNonEmptyBoardNumber,
            currentConsensusFrames = sessionState.requiredConsensusFrames,
            onDismiss = { showSettingsDialog = false },
            onSizeSelected = { newSize ->
                onSessionStateChange(sessionState.updateGridSize(newSize))
                showSettingsDialog = false
            },
            onConsensusFramesSelected = { newFrames ->
                onSessionStateChange(sessionState.updateRequiredConsensusFrames(newFrames))
            },
        )
    }

    messageToDisplay?.let { message ->
        AlertDialog(
            onDismissRequest = { messageToDisplay = null },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { messageToDisplay = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TdSettingsScreen(
    currentSize: Int,
    minAllowedSize: Int,
    currentConsensusFrames: Int,
    onDismiss: () -> Unit,
    onSizeSelected: (Int) -> Unit,
    onConsensusFramesSelected: (Int) -> Unit,
) {
    val allowedSizes = TdSessionState.ALLOWED_GRID_SIZES.filter { it >= minAllowedSize }
    val perceptionFrames = TdSessionState.ALLOWED_CONSENSUS_FRAMES
    var perceptionIndex by remember(currentConsensusFrames) {
        mutableFloatStateOf(perceptionFrames.indexOf(currentConsensusFrames).coerceAtLeast(0).toFloat())
    }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TD Settings",
                    style = ItrobocTextStyles.BigVisible
                )
                Button(onClick = onDismiss) {
                    Text("Close", style = ItrobocTextStyles.BigVisible )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text( "Total boards in grid",
                style = ItrobocTextStyles.BigVisible,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                allowedSizes.forEach { size ->
                    FilterChip(
                        selected = size == currentSize,
                        onClick = { onSizeSelected(size) },
                        label = { 
                            Text(
                                text = size.toString(),
                                style = ItrobocTextStyles.SettingValue,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) 
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            Text( "Perception",
                style = ItrobocTextStyles.BigVisible,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Slider(
                value = perceptionIndex,
                onValueChange = { value ->
                    val snappedIndex = value.roundToInt().coerceIn(0, perceptionFrames.lastIndex)
                    perceptionIndex = snappedIndex.toFloat()
                    onConsensusFramesSelected(perceptionFrames[snappedIndex])
                },
                valueRange = 0f..perceptionFrames.lastIndex.toFloat(),
                steps = 3,
                modifier = Modifier.fillMaxWidth(0.85f),
            )

            Row(
                modifier = Modifier.fillMaxWidth(0.85f),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text( "Shy",
                    style = ItrobocTextStyles.SettingsBasic,
                )
                Text( "Bold",
                    style = ItrobocTextStyles.SettingsBasic,
                )
            }
        }
    }
}

private fun generatePbnFilename(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US)
    val timestamp = formatter.format(Date())
    return "itroboc_session_$timestamp.pbn"
}

@Composable
fun BoardButton(
    number: Int,
    totalBoards: Int,
    status: BoardUiStatus,
    onClick: () -> Unit
) {
    val backgroundColor = when (status) {
        BoardUiStatus.Complete -> Color(0xFF4CAF50) // Green
        else -> Color(0xFFFFEB3B) // Yellow
    }

    val ar = if (totalBoards > 35 ) 0.6f
             else if (totalBoards > 32 ) 0.7f
             else if (totalBoards > 25 ) 0.8f
             else if (totalBoards > 20 ) 1f
             else 1.5f

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor),
        modifier = Modifier.fillMaxWidth().aspectRatio(ar),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = "$number",
            color = Color.Black,
            style = ItrobocTextStyles.SettingsBasic
        )
    }
}

@Preview(showBackground = true, widthDp = 1024, heightDp = 768)
@Composable
fun TdOverviewScreenPreview() {
    val sampleBoards = mutableMapOf<Int, BoardEditState>()

    // Board 1: Partial
    val partialBoard = BoardState().addCard(Seat.NORTH, CardId.parse("SA"))
    sampleBoards[1] = BoardEditState(1, boardState = partialBoard)

    // Board 2: Complete
    val completeBoard = BoardState()
        .let { state ->
            var updatedState = state
            Seat.entries.forEach { seat ->
                listOf("A", "K", "Q", "J", "T", "9", "8", "7", "6", "5", "4", "3", "2").forEach { rank ->
                    val suit = when (seat) {
                        Seat.NORTH -> "S"
                        Seat.EAST -> "H"
                        Seat.SOUTH -> "D"
                        Seat.WEST -> "C"
                    }
                    updatedState = updatedState.addCard(seat, CardId.parse("$suit$rank"))
                }
            }
            updatedState
        }
    sampleBoards[2] = BoardEditState(2, boardState = completeBoard)

    val sampleSessionState = TdSessionState(
        totalBoardsInGrid = 36,
        boards = sampleBoards
    )

    MaterialTheme {
        TdOverviewScreen(
            sessionState = sampleSessionState,
            onSessionStateChange = {},
            onNavigateToBoard = {},
            onBack = {}
        )
    }
}
