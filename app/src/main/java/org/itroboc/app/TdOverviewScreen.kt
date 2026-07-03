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
import androidx.compose.ui.unit.dp
import org.itroboc.core.BoardProgressSummary
import org.itroboc.core.BuiltInDeckProfiles

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
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = onBack) {
                Text("Back")
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
                Text("Import")
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
                Text("Export")
            }
            Button(
                onClick = { showSettingsDialog = true },
                modifier = Modifier.weight(1f).height(56.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Settings")
            }
        }
    }

    if (showSettingsDialog) {
        TdSettingsDialog(
            currentSize = sessionState.totalBoardsInGrid,
            onDismiss = { showSettingsDialog = false },
            onSizeSelected = { newSize ->
                onSessionStateChange(sessionState.updateGridSize(newSize))
                showSettingsDialog = false
            }
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
fun TdSettingsDialog(
    currentSize: Int,
    onDismiss: () -> Unit,
    onSizeSelected: (Int) -> Unit
) {
    val allowedSizes = TdSessionState.ALLOWED_GRID_SIZES

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("TD Settings") },
        text = {
            Column {
                Text(
                    "Total boards in grid:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    allowedSizes.forEach { size ->
                        FilterChip(
                            selected = size == currentSize,
                            onClick = { onSizeSelected(size) },
                            label = { Text(size.toString()) },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun BoardButton(
    number: Int,
    status: BoardUiStatus,
    onClick: () -> Unit
) {
    val backgroundColor = when (status) {
        BoardUiStatus.Complete -> Color(0xFF4CAF50) // Green
        else -> Color(0xFFFFEB3B) // Yellow
    }

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor),
        modifier = Modifier.fillMaxWidth().aspectRatio(0.8f),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = "$number",
            color = Color.Black,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
