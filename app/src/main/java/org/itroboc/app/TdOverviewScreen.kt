package org.itroboc.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.itroboc.core.BuiltInDeckProfiles

enum class BoardUiStatus {
    Empty,
    Partial,
    Complete
}

@Composable
fun TdOverviewScreen(
    activeProfile: ProfileListItem = BuiltInDeckProfiles.demoBridge52().metadata.toProfileListItem(),
    onNavigateToBoard: (Int) -> Unit,
    onBack: () -> Unit
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
                text = "TD Actions",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = onBack) {
                Text("Back")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 10-column Grid for 30 boards
        LazyVerticalGrid(
            columns = GridCells.Fixed(10),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(30) { index ->
                val boardNumber = index + 1
                // Mock logic: alternate colors for demonstration
                val status = when {
                    boardNumber % 10 == 0 -> BoardUiStatus.Complete
                    else -> BoardUiStatus.Empty
                }
                
                BoardButton(
                    number = boardNumber,
                    status = status,
                    onClick = { onNavigateToBoard(boardNumber) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Mock status text area
        Text(
            text = "Filled: 3/30",
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
                onClick = { /* TODO: Import */ },
                modifier = Modifier.weight(1f).height(56.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Import")
            }
            Button(
                onClick = { /* TODO: Export */ },
                modifier = Modifier.weight(1f).height(56.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Export")
            }
            Button(
                onClick = { /* TODO: Settings */ },
                modifier = Modifier.weight(1f).height(56.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Settings")
            }
        }
    }
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
