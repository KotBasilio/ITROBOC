package org.itroboc.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.itroboc.core.CardId
import org.itroboc.core.Suit

private val inspectionGreen = Color(0xFF2E7D32)

internal data class Grid13CardAliases(
    val bfm: String?,
    val brm: String?,
) {
    val hasData: Boolean
        get() = bfm != null || brm != null
}

internal fun grid13CardAliases(aliases: List<String>): Grid13CardAliases =
    Grid13CardAliases(
        bfm = aliases.firstOrNull { it.startsWith("bfm") && grid13BitsFromToken(it) != null },
        brm = aliases.firstOrNull { it.startsWith("brm") && grid13BitsFromToken(it) != null },
    )

internal fun grid13BitsFromToken(token: String): String? {
    if (!token.matches(Regex("^(bfm|brm)[0-9A-F]{4}$"))) {
        return null
    }
    val value = token.drop(3).toInt(radix = 16)
    if (value > 0x1FFF) {
        return null
    }
    return value.toString(radix = 2).padStart(13, '0')
}

@Composable
internal fun AdminReadOnlyCardPreview(
    card: CardId,
    aliases: List<String>,
    modifier: Modifier = Modifier,
) {
    val grid13Aliases = grid13CardAliases(aliases)
    if (!grid13Aliases.hasData) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.DarkGray, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No Grid13 aliases mapped for ${card.prettyCardLabel}.",
                color = Color.LightGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(10.dp)
            .background(Color(0xFFFFFEF7), RoundedCornerShape(20.dp))
            .border(3.dp, inspectionGreen, RoundedCornerShape(20.dp))
            .padding(18.dp),
    ) {
        CardCorner(
            card = card,
            modifier = Modifier.align(Alignment.TopStart),
        )
        BarcodeInspection(
            label = "bfm",
            token = grid13Aliases.bfm,
            modifier = Modifier.align(Alignment.TopEnd),
        )

        Text(
            text = card.prettyCardLabel,
            color = inspectionGreen,
            fontSize = 38.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.Center),
        )

        BarcodeInspection(
            label = "brm",
            token = grid13Aliases.brm,
            modifier = Modifier.align(Alignment.BottomStart),
        )
        CardCorner(
            card = card,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .rotate(180f),
        )
    }
}

@Composable
private fun CardCorner(
    card: CardId,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = card.rank.symbol.toString(),
            color = inspectionGreen,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = card.suit.prettySymbol,
            color = inspectionGreen,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun BarcodeInspection(
    label: String,
    token: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(160.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
        )
        if (token == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(5f)
                    .border(1.dp, Color.Gray),
                contentAlignment = Alignment.Center,
            ) {
                Text("missing", style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Grid13Barcode(
                bits = requireNotNull(grid13BitsFromToken(token)),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = token,
                style = MaterialTheme.typography.labelSmall,
                color = Color.DarkGray,
            )
        }
    }
}

@Composable
private fun Grid13Barcode(
    bits: String,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .aspectRatio(5f)
            .background(Color.White),
    ) {
        val cellWidth = size.width / bits.length
        bits.forEachIndexed { index, bit ->
            if (bit == '1') {
                drawRect(
                    color = Color.Black,
                    topLeft = androidx.compose.ui.geometry.Offset(index * cellWidth, 0f),
                    size = androidx.compose.ui.geometry.Size(cellWidth, size.height),
                )
            }
        }
        drawRect(
            color = Color.Gray,
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

private val CardId.prettyCardLabel: String
    get() = "${suit.prettySymbol}${rank.symbol}"

private val Suit.prettySymbol: String
    get() = when (this) {
        Suit.SPADES -> "♠"
        Suit.HEARTS -> "♥"
        Suit.DIAMONDS -> "♦"
        Suit.CLUBS -> "♣"
    }
