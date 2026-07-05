package org.itroboc.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.itroboc.core.BoardState
import org.itroboc.core.CardId
import org.itroboc.core.HandState
import org.itroboc.core.Rank
import org.itroboc.core.Seat
import org.itroboc.core.Suit

@Composable
fun ScissorsScreen(
    seat: Seat,
    boardState: BoardState,
    handState: HandState,
    onDismiss: () -> Unit,
    onRemoveCard: (CardId) -> Unit,
    onAddCard: (CardId) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Scissors for ${seat.displayName}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Remove on the left, add manually on the right",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ScissorsHandPane(
                    seat = seat,
                    handState = handState,
                    onRemoveCard = onRemoveCard,
                    modifier = Modifier.weight(1f),
                )
                ScissorsManualEntryPane(
                    seat = seat,
                    boardState = boardState,
                    onAddCard = onAddCard,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = onDismiss) {
                Text(fontSize = 32.sp, text = "Close")
            }
        }
    }
}

@Composable
private fun ScissorsHandPane(
    seat: Seat,
    handState: HandState,
    onRemoveCard: (CardId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .border(2.dp, Color(0xFFB0BEC5), RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "${seat.displayName} hand",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Click cards to remove them",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray,
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (handState.count() == 0) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Hand is empty.\nUse the table to add cards.",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.Gray,
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                handState.cardsBySuitInBridgeOrder().forEach { suitCards ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        Text(
                            text = suitCards.suit.prettySymbol,
                            color = suitCards.suit.displayColor,
                            fontSize = 50.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(44.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (suitCards.cards.isEmpty()) {
                            Text(
                                text = "—",
                                fontSize = 42.sp,
                                color = Color.LightGray,
                            )
                        } else {
                            suitCards.cards.forEach { card ->
                                Text(
                                    text = card.rank.symbol.toString(),
                                    fontSize = 50.sp,
                                    color = Color.Black,
                                    modifier = Modifier
                                        .clickable { onRemoveCard(card) }
                                        .padding(horizontal = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScissorsManualEntryPane(
    seat: Seat,
    boardState: BoardState,
    onAddCard: (CardId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val suits = Suit.entries
    val ranks = Rank.entries

    Column(
        modifier = modifier
            .fillMaxHeight()
            .border(2.dp, Color(0xFFB0BEC5), RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "Manual entry",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Click any card to add it to ${seat.displayName}",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OwnershipLegendChip("Here", Color(0xFF81C784))
            OwnershipLegendChip("Other hand", Color(0xFFFFCC80))
            OwnershipLegendChip("Unassigned", Color(0xFFE0E0E0))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            suits.forEach { suit ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1.3f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = suit.prettySymbol,
                        color = suit.displayColor,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        ranks.forEach { rank ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                suits.forEach { suit ->
                    val card = CardId(suit, rank)
                    val ownerSeat = boardState.seatContaining(card)
                    val cardState = when {
                        ownerSeat == seat -> ManualEntryCardState.IN_SELECTED_HAND
                        ownerSeat != null -> ManualEntryCardState.IN_OTHER_HAND
                        else -> ManualEntryCardState.UNASSIGNED
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        ManualEntryCardButton(
                            card = card,
                            ownerSeat = ownerSeat,
                            state = cardState,
                            onClick = { onAddCard(card) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OwnershipLegendChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = Color.Black, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ManualEntryCardButton(
    card: CardId,
    ownerSeat: Seat?,
    state: ManualEntryCardState,
    onClick: () -> Unit,
) {
    val backgroundColor = when (state) {
        ManualEntryCardState.IN_SELECTED_HAND -> Color(0xFF81C784)
        ManualEntryCardState.IN_OTHER_HAND -> Color(0xFFFFCC80)
        ManualEntryCardState.UNASSIGNED -> Color(0xFFE0E0E0)
    }
    val borderColor = when (ownerSeat) {
        Seat.NORTH -> Color(0xFF1565C0)
        Seat.EAST -> Color(0xFF6A1B9A)
        Seat.SOUTH -> Color(0xFF2E7D32)
        Seat.WEST -> Color(0xFFC62828)
        null -> Color.Transparent
    }
    val enabled = state != ManualEntryCardState.IN_SELECTED_HAND

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(backgroundColor, RoundedCornerShape(6.dp))
            .border(2.dp, borderColor, RoundedCornerShape(6.dp))
            .then(
                if (enabled) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = card.rank.symbol.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.Black,
        )
        if (ownerSeat != null) {
            Text(
                text = ownerSeat.displayName.first().toString(),
                fontSize = 10.sp,
                color = Color.DarkGray,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
            )
        }
    }
}

private enum class ManualEntryCardState {
    IN_SELECTED_HAND,
    IN_OTHER_HAND,
    UNASSIGNED,
}

private val Suit.displayColor: Color
    get() = when (this) {
        Suit.HEARTS, Suit.DIAMONDS -> Color.Red
        Suit.SPADES, Suit.CLUBS -> Color.Black
    }

@Preview(showBackground = true, widthDp = 1000, heightDp = 800)
@Composable
fun ScissorsScreenPreview() {
    val seat = Seat.NORTH
    val northHand = HandState(
        setOf(
            CardId(Suit.SPADES, Rank.ACE),
            CardId(Suit.SPADES, Rank.KING),
            CardId(Suit.SPADES, Rank.QUEEN),
            CardId(Suit.HEARTS, Rank.TWO),
            CardId(Suit.HEARTS, Rank.THREE),
            CardId(Suit.HEARTS, Rank.FOUR),
        )
    )
    val southHand = HandState(
        setOf(
            CardId(Suit.DIAMONDS, Rank.ACE),
            CardId(Suit.DIAMONDS, Rank.KING),
        )
    )
    val boardState = BoardState(
        hands = mapOf(
            Seat.NORTH to northHand,
            Seat.SOUTH to southHand,
            Seat.EAST to HandState(),
            Seat.WEST to HandState(),
        )
    )

    MaterialTheme {
        ScissorsScreen(
            seat = seat,
            boardState = boardState,
            handState = northHand,
            onDismiss = {},
            onRemoveCard = {},
            onAddCard = {},
        )
    }
}
