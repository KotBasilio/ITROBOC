package org.itroboc.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.unit.dp
import org.itroboc.core.*

@Composable
fun ScissorsScreen(
    seat: Seat,
    boardState: BoardState,
    handState: HandState,
    onDismiss: () -> Unit,
    onRemoveCard: (CardId) -> Unit,
    onAddCard: (CardId) -> Unit,
    onSeatChange: (Seat) -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.weight(2f))
                Text(
                    text = "Add cards",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Normal,
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "Scissors for ${seat.displayName}",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "Remove cards",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Normal,
                )
                Spacer(modifier = Modifier.weight(2f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ScissorsManualEntryPane(
                    seat = seat,
                    boardState = boardState,
                    onAddCard = onAddCard,
                    modifier = Modifier.weight(1f),
                )
                ScissorsHandPane(
                    seat = seat,
                    handState = handState,
                    onRemoveCard = onRemoveCard,
                    onDismiss = onDismiss,
                    onSeatChange = onSeatChange,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ScissorsHandPane(
    seat: Seat,
    handState: HandState,
    onRemoveCard: (CardId) -> Unit,
    onDismiss: () -> Unit,
    onSeatChange: (Seat) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .border(2.dp, Color(0xFFB0BEC5), RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        ScissorsHandArea(
            seat = seat,
            handState = handState,
            onRemoveCard = onRemoveCard,
            onSeatChange = onSeatChange,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Legend:",
                style = ItrobocTextStyles.CardInspectionSuit,
                fontWeight = FontWeight.Bold,
            )
            OwnershipLegendChip("Here", Color(0xFF81C784))
            OwnershipLegendChip("There", Color(0xFFFFCC80))
            OwnershipLegendChip("Free", Color(0xFFE0E0E0))
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Button(onClick = onDismiss) {
                Text(style = ItrobocTextStyles.CockpitBasic, text = "Close")
            }
        }
    }
}

@Composable
private fun ColumnScope.ScissorsHandArea(
    seat: Seat,
    handState: HandState,
    onRemoveCard: (CardId) -> Unit,
    onSeatChange: (Seat) -> Unit,
) {
    val isComplete = handState.isComplete()
    val backgroundColor = if (isComplete) Color(0xFFC8E6C9) else Color.Transparent
    val borderColor = seat.borderColor
    val borderWidth = 6.dp

    // We use a fixed height here (e.g., 280.dp) to fit approximately 5 rows of cards reliably.
    // This ensures the area doesn't "stretch" or "shrink" when cards are added or removed.
    val handContainerModifier = Modifier
        .height(340.dp)
        .fillMaxWidth()
        .background(backgroundColor, RoundedCornerShape(12.dp))
        .border(borderWidth, borderColor, RoundedCornerShape(12.dp))

    Text(
        text = "Click cards to remove them",
        style = MaterialTheme.typography.bodyLarge,
        color = Color.Gray,
    )
    Spacer(modifier = Modifier.height(8.dp))

    if (handState.count() == 0) {
        Box(
            modifier = handContainerModifier,
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
            modifier = handContainerModifier,
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            handState.cardsBySuitInBridgeOrder().forEach { suitCards ->
                ScissorsSuitCardsRow(
                    suitCards = suitCards,
                    onRemoveCard = onRemoveCard,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Seat.entries.forEach { chip ->
            SeatBorderLegendChip(
                ownerSeat = seat,
                seat = chip,
                onClick = { onSeatChange(chip) }
            )
        }
    }

    // This Spacer now has weight(1f), which tells it to consume ALL remaining
    // vertical space, pushing the items below it to the very bottom of the parent Column.
    Spacer(modifier = Modifier.weight(1f))
}

@Composable
private fun ScissorsSuitCardsRow(
    suitCards: SuitCards,
    onRemoveCard: (CardId) -> Unit,
) {
    val cards = suitCards.cards
    val splitIndex = if (cards.size >= 8) (cards.size + 1) / 2 else cards.size
    val topRow = cards.take(splitIndex)
    val bottomRow = cards.drop(splitIndex)

    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Start,
    ) {
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = suitCards.suit.prettySymbol,
            color = suitCards.suit.displayColor,
            style = ItrobocTextStyles.TdResultCard,
            modifier = Modifier.width(44.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))

        if (cards.isEmpty()) {
            Text(
                text = " —",
                style = ItrobocTextStyles.BigVisible,
                color = Color.Black,
            )
        } else if (bottomRow.isEmpty()) {
            ScissorsSuitRankLine(cards = topRow, onRemoveCard = onRemoveCard)
        } else {
            Box(
                modifier = Modifier
                    .border(1.dp, Color(0xFFB0BEC5), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ScissorsSuitRankLine(cards = topRow, onRemoveCard = onRemoveCard)
                    ScissorsSuitRankLine(cards = bottomRow, onRemoveCard = onRemoveCard)
                }
            }
        }
    }
}

@Composable
private fun ScissorsSuitRankLine(
    cards: List<CardId>,
    onRemoveCard: (CardId) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        cards.forEach { card ->
            Text(
                text = card.rank.symbol.toString(),
                style = ItrobocTextStyles.TdResultCard,
                color = Color.Black,
                modifier = Modifier
                    .clickable { onRemoveCard(card) }
                    .padding(horizontal = 12.dp),
            )
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
            .border(2.dp, Color(0xFFB0BEC5), RoundedCornerShape(12.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            suits.forEach { suit ->
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = suit.prettySymbol, style = ItrobocTextStyles.ScissorsGridSuit)
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
        Text(text = label, color = Color.Black, fontWeight = FontWeight.Medium, style = ItrobocTextStyles.CardInspectionSuit)
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
    val enabled = state != ManualEntryCardState.IN_SELECTED_HAND

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(backgroundColor, RoundedCornerShape(6.dp))
            .border(6.dp, ownerSeat.borderColor, RoundedCornerShape(6.dp))
            .then(
                if (enabled) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = card.rank.symbol.toString(),
            fontWeight = FontWeight.Bold,
            style = ItrobocTextStyles.ScissorsGridRank,
            color = Color.Black,
        )
    }
}

@Composable
private fun SeatBorderLegendChip(ownerSeat: Seat?, seat: Seat, onClick: () -> Unit) {
    val backgroundColor =
        if (seat == ownerSeat) { Color(0xFF81C784) }
        else { Color(0xFFFFCC80) }

    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(6.dp))
            .border(6.dp, seat.borderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = seat.displayName, color = Color.Black, fontWeight = FontWeight.Medium, style = ItrobocTextStyles.ScissorsGridRank)
    }
}

private enum class ManualEntryCardState {
    IN_SELECTED_HAND,
    IN_OTHER_HAND,
    UNASSIGNED,
}

private val Seat?.borderColor: Color
    get() = when (this) {
        Seat.NORTH -> Color(0xFF1565C0)
        Seat.EAST -> Color(0xFF6A1B9A)
        Seat.SOUTH -> Color(0xFFC62828)
        Seat.WEST -> Color(0xFF2E7D32)
        null -> Color.Transparent
    }

private val Suit.displayColor: Color
    get() = when (this) {
        Suit.HEARTS, Suit.DIAMONDS -> Color.Red
        Suit.SPADES, Suit.CLUBS -> Color.Black
    }

@ComposePreview(showBackground = true, device = Devices.AUTOMOTIVE_1024p, widthDp = 1024, heightDp = 800)
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
    val westHand = HandState(
        setOf(
            CardId(Suit.DIAMONDS, Rank.THREE),
            CardId(Suit.DIAMONDS, Rank.QUEEN),
        )
    )
    val eastHand = HandState(
        setOf(
            CardId(Suit.CLUBS, Rank.THREE),
            CardId(Suit.CLUBS, Rank.QUEEN),
            CardId(Suit.CLUBS, Rank.ACE),
        )
    )
    val boardState = BoardState(
        hands = mapOf(
            Seat.NORTH to northHand,
            Seat.SOUTH to southHand,
            Seat.EAST to eastHand,
            Seat.WEST to westHand,
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
            onSeatChange = {},
        )
    }
}
