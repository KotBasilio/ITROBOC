package org.itroboc.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun AdminActionsScreen(
    onBack: () -> Unit
) {
    var uiState by remember {
        mutableStateOf(
            AdminProfileUiState(
                profiles = listOf(
                    ProfileListItem(
                        id = "builtin-demo-bridge52-v1",
                        displayName = "Built-in Demo Bridge 52",
                        isBuiltIn = true,
                        isDemo = true
                    )
                ),
                activeProfileId = "builtin-demo-bridge52-v1"
            )
        )
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var messageToDisplay by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin actions") },
                actions = {
                    Button(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        },
        bottomBar = {
            AdminBottomBar(
                onExport = { messageToDisplay = "Export: Not implemented yet." },
                onImport = { messageToDisplay = "Import: Not implemented yet." },
                onEdit = { messageToDisplay = "Edit: Not implemented yet." },
                onDelete = {
                    val active = uiState.activeProfile
                    if (active?.isBuiltIn == true) {
                        messageToDisplay = "Built-in profiles cannot be deleted yet."
                    } else {
                        showDeleteDialog = true
                    }
                },
                onSettings = { messageToDisplay = "Settings: Not implemented yet." }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.profiles) { profile ->
                    ProfileRow(
                        profile = profile,
                        isActive = profile.id == uiState.activeProfileId,
                        onClick = { uiState = uiState.copy(activeProfileId = profile.id) }
                    )
                }

                item {
                    TextButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add new")
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddProfileDialog(
            onConfirm = { name ->
                val trimmed = name.trim()
                if (trimmed.isNotEmpty()) {
                    val newProfile = ProfileListItem(
                        id = "custom-profile-${uiState.profiles.size}",
                        displayName = trimmed,
                        isBuiltIn = false,
                        isDemo = false
                    )
                    uiState = uiState.copy(
                        profiles = uiState.profiles + newProfile,
                        activeProfileId = newProfile.id
                    )
                    showAddDialog = false
                }
            },
            onDismiss = { showAddDialog = false }
        )
    }

    if (showDeleteDialog) {
        val activeName = uiState.activeProfile?.displayName ?: ""
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Profile") },
            text = { Text("Are you sure you want to delete the active profile \"$activeName\"?") },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = uiState.activeProfileId
                    val newProfiles = uiState.profiles.filter { it.id != toDelete }
                    uiState = uiState.copy(
                        profiles = newProfiles,
                        activeProfileId = newProfiles.firstOrNull()?.id ?: ""
                    )
                    showDeleteDialog = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    messageToDisplay?.let { msg ->
        AlertDialog(
            onDismissRequest = { messageToDisplay = null },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { messageToDisplay = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AdminActionsPreview() {
    MaterialTheme {
        AdminActionsScreen(onBack = {})
    }
}

@Composable
fun ProfileRow(
    profile: ProfileListItem,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = if (isActive) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                )
                if (profile.isBuiltIn) {
                    Text(
                        text = "Built-in",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
            if (isActive) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Active",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun AddProfileDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add new profile") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Profile Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AdminBottomBar(
    onExport: () -> Unit,
    onImport: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val buttonModifier = Modifier.weight(1f).height(48.dp)
            val textStyle = MaterialTheme.typography.labelSmall
            
            Button(onClick = onExport, modifier = buttonModifier, contentPadding = PaddingValues(0.dp)) {
                Text("Export", style = textStyle)
            }
            Button(onClick = onImport, modifier = buttonModifier, contentPadding = PaddingValues(0.dp)) {
                Text("Import", style = textStyle)
            }
            Button(onClick = onEdit, modifier = buttonModifier, contentPadding = PaddingValues(0.dp)) {
                Text("Edit", style = textStyle)
            }
            Button(onClick = onDelete, modifier = buttonModifier, contentPadding = PaddingValues(0.dp)) {
                Text("Delete", style = textStyle)
            }
            Button(onClick = onSettings, modifier = buttonModifier, contentPadding = PaddingValues(0.dp)) {
                Text("Settings", style = textStyle)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(
    title: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    CenterAlignedTopAppBar(
        title = title,
        actions = actions
    )
}
