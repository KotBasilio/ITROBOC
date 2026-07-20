package org.itroboc.app

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Centralized typography for ITROBOC UI.
 * Prefer semantic roles over raw font sizes.
 */
object ItrobocTextStyles {
    // Large landing buttons
    val MainMenuAction = TextStyle(fontSize = 60.sp)

    // Merged: Screen title + Primary action buttons + Dialog title + Dialog section headers + Status summary + Swap headers
    val BigVisible = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold)

    // Internal cockpit/dialog groupings
    val SectionHeader = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold)

    // Merged: Cockpit header + Cockpit action button + Dialog action button
    val CockpitBasic = TextStyle(fontSize = 32.sp)

    // The large "Last Scanned" card ID
    val TdResultCard = TextStyle(fontSize = 50.sp, fontWeight = FontWeight.Bold)

    // Merged: Hand card symbol + Hand card ranks
    val TdHand = TextStyle(fontSize = 24.sp)

    // SPS/IDLE telemetry
    val TdTelemetry = TextStyle(fontSize = 24.sp)

    // Stabilization layer feedback
    val TdThoughts = TextStyle(fontSize = 20.sp)

    // Radio button labels
    val ChoiceLabel = TextStyle(fontSize = 28.sp)

    // Merged: Slider labels + Grid item label
    val SettingsBasic = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold)

    // Large suit headers in Scissors repair
    val ScissorsGridSuit = TextStyle(fontSize = 38.sp)

    // Rank buttons in Scissors repair
    val ScissorsGridRank = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold)

    // Headers in Admin calibration
    val AdminGridSuit = TextStyle(fontSize = 20.sp)

    // Rank buttons in Admin calibration
    val AdminGridRank = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)

    // Card label in read-only preview
    val CardInspectionTitle = TextStyle(fontSize = 38.sp, fontWeight = FontWeight.Black)

    // Suit symbol in read-only preview
    val CardInspectionSuit = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Black)

    // Selection chip labels (e.g. settings)
    val SettingValue = TextStyle(fontSize = 24.sp)
}
