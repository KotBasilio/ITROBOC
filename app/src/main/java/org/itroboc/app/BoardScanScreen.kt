package org.itroboc.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class BarcodeOrientationMode(
    val label: String,
) {
    BFM("bfm"),
    BRM("brm"),
    AUTO("auto"),
}

@Composable
fun BoardScanScreen(
    boardNumber: Int,
    onBack: () -> Unit
) {
    var orientationMode by remember { mutableStateOf(BarcodeOrientationMode.BFM) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Board $boardNumber",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Board scan workflow will be implemented here.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Barcode orientation",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarcodeOrientationMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier.selectable(
                        selected = orientationMode == mode,
                        onClick = { orientationMode = mode },
                        role = Role.RadioButton,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = orientationMode == mode,
                        onClick = null,
                    )
                    Text(mode.label)
                }
            }
        }
        if (orientationMode == BarcodeOrientationMode.AUTO) {
            Text(
                text = "Auto orientation is reserved for future detection logic.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onBack) {
            Text("Back")
        }
    }
}
