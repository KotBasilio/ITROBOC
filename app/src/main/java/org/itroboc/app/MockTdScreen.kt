package org.itroboc.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.itroboc.core.BoardProgressSummary
import org.itroboc.core.BuiltInDeckProfiles
import org.itroboc.core.CardId
import org.itroboc.core.DeckProfile
import org.itroboc.core.DeckProfileMetadata
import org.itroboc.core.PbnExporter
import org.itroboc.core.Rank
import org.itroboc.core.Seat
import org.itroboc.core.Suit
import org.itroboc.core.TdScanAccumulator
import org.itroboc.core.TdScanSessionPresentation

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MockTdScreen(
    deckProfile: DeckProfile = BuiltInDeckProfiles.defaultProfile(),
    onBack: () -> Unit
) {
    var selectedSeat by rememberSaveable { mutableStateOf(Seat.NORTH) }
    var inputText by rememberSaveable(deckProfile.metadata.profileId) {
        mutableStateOf(deckProfile.sampleSignatures(count = 2))
    }
    var accumulator by remember(deckProfile) {
        mutableStateOf(TdUiState.initial(deckProfile))
    }

    val presentation = accumulator.presentationFor(selectedSeat)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(Color(0xFFF7F2E8))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ITROBOC Fake TD Shell",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Button(onClick = onBack) {
                Text("Back")
            }
        }
        Text(
            text = "Android shell only. Fake signatures in, pure core out.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = activeProfileLabel(accumulator.accumulator.deckProfile.metadata),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF57534E),
        )

        Panel(title = "Current Hand (${selectedSeat.displayName})") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Seat.entries.forEach { seat ->
                    FilterChip(
                        selected = seat == selectedSeat,
                        onClick = { selectedSeat = seat },
                        label = { Text(seat.displayName) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            presentation.handProgress.cardsBySuit.forEach { suitCards ->
                val cardText =
                    suitCards.cards.joinToString(" ") { it.toPrettyString() }.ifBlank { "—" }
                Text(
                    text = "${suitCards.suit.symbol}: $cardText",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Panel(title = "Fake Batch Input") {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Comma-separated signatures") },
                minLines = 2,
                keyboardActions = KeyboardActions(
                    onDone = {
                        accumulator = accumulator.scan(selectedSeat, inputText)
                    },
                ),
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presetBatches(deckProfile).forEach { preset ->
                    Button(onClick = { inputText = preset.signatures }) {
                        Text(preset.label)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { accumulator = accumulator.scan(selectedSeat, inputText) }) {
                    Text("Scan Fake Batch")
                }
                Button(onClick = { accumulator = TdUiState.initial(deckProfile) }) {
                    Text("Reset Session")
                }
            }
        }

        Panel(title = "Hand Status") {
            Text(
                text = presentation.handStatusText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            presentation.latestBatchMessages.forEach { message ->
                Text(text = "• $message", style = MaterialTheme.typography.bodyLarge)
            }
        }

        Panel(title = "Board Progress") {
            BoardProgressView(summary = presentation.boardProgress)
        }

        if (presentation.boardProgress.boardComplete) {
            Panel(title = "PBN Preview") {
                Text(
                    text = PbnExporter.export(accumulator.accumulator.boardState),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun BoardProgressView(summary: BoardProgressSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Total cards: ${summary.totalCards}/52", style = MaterialTheme.typography.bodyLarge)
        Text("Board complete: ${if (summary.boardComplete) "Yes" else "No"}", style = MaterialTheme.typography.bodyLarge)
        if (summary.duplicateConflictCount > 0) {
            Text("Recent cross-hand conflicts: ${summary.duplicateConflictCount}", color = Color(0xFF9A3412))
        }
        Seat.entries.forEach { seat ->
            val count = summary.handCounts.getValue(seat)
            val suffix = if (seat in summary.completeSeats) "complete" else "${13 - count} missing"
            Text("${seat.displayName}: $count/13 ($suffix)", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun Panel(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

private data class FakeBatchPreset(
    val label: String,
    val signatures: String,
)

private fun presetBatches(deckProfile: DeckProfile): List<FakeBatchPreset> =
    listOf(
        FakeBatchPreset(
            label = "Add two random cards",
            signatures = deckProfile.sampleSignatures(count = 2),
        ),
        FakeBatchPreset(
            label = "Unknown signature",
            signatures = "bfmFFFF",
        ),
        FakeBatchPreset(
            label = "Duplicate test",
            signatures = deckProfile.sampleSignatures(count = 2).duplicateFirstSignature(),
        ),
        FakeBatchPreset(
            label = "North Demo",
            signatures = deckProfile.signaturesForCards(
                CardId(Suit.SPADES, Rank.ACE),
                CardId(Suit.SPADES, Rank.KING),
                CardId(Suit.SPADES, Rank.QUEEN),
                CardId(Suit.SPADES, Rank.JACK),
                CardId(Suit.SPADES, Rank.TEN),
                CardId(Suit.SPADES, Rank.NINE),
                CardId(Suit.SPADES, Rank.EIGHT),
                CardId(Suit.SPADES, Rank.SEVEN),
                CardId(Suit.SPADES, Rank.SIX),
                CardId(Suit.SPADES, Rank.FIVE),
                CardId(Suit.SPADES, Rank.FOUR),
                CardId(Suit.SPADES, Rank.THREE),
                CardId(Suit.SPADES, Rank.TWO),
            ),
        ),
        FakeBatchPreset(
            label = "East Demo",
            signatures = deckProfile.signaturesForCards(
                CardId(Suit.HEARTS, Rank.ACE),
                CardId(Suit.HEARTS, Rank.KING),
                CardId(Suit.HEARTS, Rank.QUEEN),
                CardId(Suit.HEARTS, Rank.JACK),
                CardId(Suit.HEARTS, Rank.TEN),
                CardId(Suit.HEARTS, Rank.NINE),
                CardId(Suit.HEARTS, Rank.EIGHT),
                CardId(Suit.HEARTS, Rank.SEVEN),
                CardId(Suit.HEARTS, Rank.SIX),
                CardId(Suit.HEARTS, Rank.FIVE),
                CardId(Suit.HEARTS, Rank.FOUR),
                CardId(Suit.HEARTS, Rank.THREE),
                CardId(Suit.HEARTS, Rank.TWO),
            ),
        ),
        FakeBatchPreset(
            label = "South Demo",
            signatures = deckProfile.signaturesForCards(
                CardId(Suit.DIAMONDS, Rank.ACE),
                CardId(Suit.DIAMONDS, Rank.KING),
                CardId(Suit.DIAMONDS, Rank.QUEEN),
                CardId(Suit.DIAMONDS, Rank.JACK),
                CardId(Suit.DIAMONDS, Rank.TEN),
                CardId(Suit.DIAMONDS, Rank.NINE),
                CardId(Suit.DIAMONDS, Rank.EIGHT),
                CardId(Suit.DIAMONDS, Rank.SEVEN),
                CardId(Suit.DIAMONDS, Rank.SIX),
                CardId(Suit.DIAMONDS, Rank.FIVE),
                CardId(Suit.DIAMONDS, Rank.FOUR),
                CardId(Suit.DIAMONDS, Rank.THREE),
                CardId(Suit.DIAMONDS, Rank.TWO),
            ),
        ),
        FakeBatchPreset(
            label = "West Demo",
            signatures = deckProfile.signaturesForCards(
                CardId(Suit.CLUBS, Rank.ACE),
                CardId(Suit.CLUBS, Rank.KING),
                CardId(Suit.CLUBS, Rank.QUEEN),
                CardId(Suit.CLUBS, Rank.JACK),
                CardId(Suit.CLUBS, Rank.TEN),
                CardId(Suit.CLUBS, Rank.NINE),
                CardId(Suit.CLUBS, Rank.EIGHT),
                CardId(Suit.CLUBS, Rank.SEVEN),
                CardId(Suit.CLUBS, Rank.SIX),
                CardId(Suit.CLUBS, Rank.FIVE),
                CardId(Suit.CLUBS, Rank.FOUR),
                CardId(Suit.CLUBS, Rank.THREE),
                CardId(Suit.CLUBS, Rank.TWO),
            ),
        ),
        FakeBatchPreset(label = "Conflict test", signatures = deckProfile.sampleSignatures(count = 1)),
    )

private fun DeckProfile.sampleSignatures(count: Int): String =
    rawSignatures()
        .sortedWith(compareBy<String> { !it.startsWith("bfm") }.thenBy { it })
        .asSequence()
        .distinctBy { lookup(it) }
        .take(count)
        .joinToString(separator = ",")

private fun DeckProfile.signaturesForCards(vararg cards: CardId): String =
    cards.mapNotNull { card ->
        rawSignatures()
            .filter { lookup(it) == card }
            .minWithOrNull(compareBy<String> { !it.startsWith("bfm") }.thenBy { it })
    }.joinToString(separator = ",")

private fun String.duplicateFirstSignature(): String {
    val signatures = split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    return if (signatures.isEmpty()) {
        this
    } else {
        (listOf(signatures.first()) + signatures).joinToString(separator = ",")
    }
}

private data class TdUiState(
    val accumulator: TdScanAccumulator,
    val latestPresentation: TdScanSessionPresentation,
) {
    fun scan(seat: Seat, rawInput: String): TdUiState {
        val signatures = rawInput.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val batchReport = accumulator.scanMany(seat, signatures)
        return TdUiState(
            accumulator = batchReport.accumulator,
            latestPresentation = TdScanSessionPresentation.from(seat, batchReport),
        )
    }

    fun presentationFor(selectedSeat: Seat): TdScanSessionPresentation =
        if (latestPresentation.seat == selectedSeat) {
            latestPresentation
        } else {
            TdScanSessionPresentation.from(
                selectedSeat,
                accumulator.scanMany(selectedSeat, emptyList()),
            )
        }

    companion object {
        fun initial(deckProfile: DeckProfile): TdUiState {
            val accumulator = TdScanAccumulator(deckProfile)
            return TdUiState(
                accumulator = accumulator,
                latestPresentation = TdScanSessionPresentation.from(
                    Seat.NORTH,
                    accumulator.scanMany(Seat.NORTH, emptyList()),
                ),
            )
        }
    }
}

private fun CardId.toPrettyString(): String = "${suit.prettySymbol}${rank.symbol}"

private fun activeProfileLabel(metadata: DeckProfileMetadata): String {
    val suffix = if (metadata.isDemo) " (demo)" else ""
    return "Profile: ${metadata.displayName}$suffix"
}

private val Suit.prettySymbol: String
    get() = when (this) {
        Suit.SPADES -> "♠"
        Suit.HEARTS -> "♥"
        Suit.DIAMONDS -> "♦"
        Suit.CLUBS -> "♣"
    }
