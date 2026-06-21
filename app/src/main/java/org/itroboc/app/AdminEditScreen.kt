package org.itroboc.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.itroboc.core.*

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
    
    // Trigger recomposition when editor state changes
    var updateTrigger by remember { mutableIntStateOf(0) }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Part: 52-card mapping grid (Stretched to fill height)
        Column(
            modifier = Modifier
                .weight(0.5f)
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
                .weight(0.5f)
                .fillMaxHeight()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mock Camera Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .background(Color.DarkGray, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("Mock Camera Preview", color = Color.LightGray)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Selected card status
            Text(
                text = "Selected: ${selectedCard.prettyString}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
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
                        val mockSig = "0x${(1000..9999).random().toString(16).uppercase()}"
                        val result = editor.assign(mockSig, selectedCard)
                        lastResultMessage = when (result) {
                            is DeckProfileEditResult.Assigned -> "Signature $mockSig assigned to ${selectedCard.prettyString}"
                            is DeckProfileEditResult.AlreadyAssignedToSelected -> "Alias $mockSig already exists for ${selectedCard.prettyString}"
                            is DeckProfileEditResult.SignatureConflict -> "CONFLICT: $mockSig is already mapped to ${result.existingCard.prettyString}"
                            else -> "No card selected"
                        }
                        
                        if (result is DeckProfileEditResult.Assigned && autoAdvance) {
                            // Find next unmapped card or stay
                            val allCards = Suit.entries.flatMap { s -> Rank.entries.map { r -> CardId(s, r) } }
                            val nextUnmapped = allCards.firstOrNull { !editor.isMapped(it) }
                            if (nextUnmapped != null) {
                                selectedSuit = nextUnmapped.suit
                                selectedRank = nextUnmapped.rank
                            } else {
                                lastResultMessage = "Profile complete! All 52 cards mapped."
                            }
                        }
                        if (result is DeckProfileEditResult.Assigned) {
                            isDirty = true
                        }
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
            title = { Text("Profile was modified") },
            text = { Text("Profile was modified. Choose action.") },
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
