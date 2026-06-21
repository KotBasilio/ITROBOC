package org.itroboc.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.itroboc.core.BoardProgressSummary
import org.itroboc.core.BuiltInDeckProfiles
import org.itroboc.core.CardId
import org.itroboc.core.PbnExporter
import org.itroboc.core.Seat
import org.itroboc.core.Suit
import org.itroboc.core.TdScanAccumulator
import org.itroboc.core.TdScanSessionPresentation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FakeTdScreen()
                }
            }
        }
    }
}

private val presetBatches = listOf(
    FakeBatchPreset(
        label = "Add two random cards",
        signatures = "0x101A,0x102F",
    ),
    FakeBatchPreset(
        label = "Unknown signature",
        signatures = "0x10FF",
    ),
    FakeBatchPreset(
        label = "Duplicate test",
        signatures = "0x1001,0x1001,0x1002",
    ),
    FakeBatchPreset(
        label = "North Demo",
        signatures = "0x1001,0x1002,0x1003,0x1004,0x1005,0x1012,0x1013,0x1014,0x1021,0x1022,0x1031,0x1032,0x1033",
    ),
    FakeBatchPreset(
        label = "East Demo",
        signatures = "0x1006,0x101A,0x102F,0x101D,0x1027,0x100C,0x1019,0x102B,0x1030,0x100F,0x1020,0x1016,0x1034",
    ),
    FakeBatchPreset(
        label = "South Demo",
        signatures = "0x1007,0x1011,0x1023,0x100B,0x101C,0x1026,0x1009,0x1028,0x101F,0x100E,0x102C,0x1015,0x1029",
    ),
    FakeBatchPreset(
        label = "West Demo",
        signatures = "0x1008,0x1010,0x1017,0x100A,0x101B,0x1025,0x100D,0x102D,0x1018,0x1024,0x102A,0x101E,0x102E",
    ),
    FakeBatchPreset(label = "Conflict test", signatures = "0x1002"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FakeTdScreen() {
    var selectedSeat by rememberSaveable { mutableStateOf(Seat.NORTH) }
    var inputText by rememberSaveable { mutableStateOf("0x1001,0x1002") }
    var accumulator by remember {
        mutableStateOf(TdUiState.initial())
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
        Text(
            text = "ITROBOC Fake TD Shell",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Android shell only. Fake signatures in, pure core out.",
            style = MaterialTheme.typography.bodyLarge,
        )

        Panel(title = "Current Seat") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Seat.entries.forEach { seat ->
                    FilterChip(
                        selected = seat == selectedSeat,
                        onClick = { selectedSeat = seat },
                        label = { Text(seat.displayName) },
                    )
                }
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
                presetBatches.forEach { preset ->
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
                Button(onClick = { accumulator = TdUiState.initial() }) {
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

        Panel(title = "Current Hand") {
            presentation.handProgress.cardsBySuit.forEach { suitCards ->
                val cardText = suitCards.cards.joinToString(" ") { it.toPrettyString() }.ifBlank { "—" }
                Text(
                    text = "${suitCards.suit.symbol}: $cardText",
                    style = MaterialTheme.typography.bodyLarge,
                )
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
        fun initial(): TdUiState {
            val accumulator = TdScanAccumulator(BuiltInDeckProfiles.demoBridge52())
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

private val Suit.prettySymbol: String
    get() = when (this) {
        Suit.SPADES -> "♠"
        Suit.HEARTS -> "♥"
        Suit.DIAMONDS -> "♦"
        Suit.CLUBS -> "♣"
    }
