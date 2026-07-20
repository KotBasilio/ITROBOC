# Typography Inventory and Style Proposal

This document presents the inventory of `fontSize` usage in the ITROBOC project and proposes a central semantic typography object `ItrobocTextStyles`.

## Observed fontSize inventory

| File | Context | Current Size (sp) | Inferred Semantic Role |
| :--- | :--- | :--- | :--- |
| `MainMenuScreen.kt` | "Admin", "TD eye" buttons | 60 | Main menu actions |
| `TdOverviewScreen.kt` | "TD Actions" Header | 40 | Screen title |
| `TdOverviewScreen.kt` | Import/Export/Save/Settings/Back | 40 | Primary action buttons |
| `TdOverviewScreen.kt` | "TD Settings" Header | 40 | Dialog title |
| `TdOverviewScreen.kt` | "Total boards in grid", "Perception" | 40 | Dialog section headers |
| `TdOverviewScreen.kt` | Settings chip labels | 24 | Selection chip text |
| `TdOverviewScreen.kt` | "Shy" / "Bold" labels | 36 | Slider labels |
| `TdOverviewScreen.kt` | Board grid numbers | 36 | Grid item label |
| `EditBoardScreen.kt` | "Board: n" | 32 | Cockpit header |
| `EditBoardScreen.kt` | "Back" button (cockpit) | 32 | Cockpit action button |
| `EditBoardScreen.kt` | Cockpit buttons (Undo, Scissors, Clear, etc) | 32 | Cockpit action button |
| `EditBoardScreen.kt` | Suit symbols in hand | 24 | Hand card symbol |
| `EditBoardScreen.kt` | Rank symbols in hand | 24 | Hand card ranks |
| `EditBoardScreen.kt` | "Cards: n/52" | 40 | Status summary |
| `EditBoardScreen.kt` | SPS / IDLE telemetry | 24 | Telemetry data |
| `EditBoardScreen.kt` | "Thoughts: ..." | 20 | Stabilization thoughts |
| `EditBoardScreen.kt` | Last scanned card display | 50 | Primary scanning result |
| `EditBoardScreen.kt` | "Barcode" / "Feed mode" headers | 30 | Cockpit section headers |
| `EditBoardScreen.kt` | Radio labels (Orientation/Feed) | 28 | Choice selection label |
| `EditBoardScreen.kt` | "Swap ... with..." Header | 40 | Screen title |
| `EditBoardScreen.kt` | Seat buttons (Swap) | 40 | Selection action buttons |
| `ScissorsScreen.kt` | "Close" button | 32 | Dialog action button |
| `ScissorsScreen.kt` | Suit symbols in manual grid | 38 | Grid suit header |
| `ScissorsScreen.kt` | Rank symbols in manual buttons | 30 | Grid card rank |
| `AdminEditScreen.kt` | Suit symbols in calibration grid | 20 | Calibration grid header |
| `AdminEditScreen.kt` | Rank symbols in calibration buttons | 14 | Calibration card rank |
| `AdminReadOnlyCardPreview.kt` | Large card label (e.g. "SA") | 38 | Card inspection title |
| `AdminReadOnlyCardPreview.kt` | Card corner suit symbol | 24 | Card inspection suit |

## Semantic Grouping Table

| Role | Proposed Value | Bold | Notes |
| :--- | :--- | :--- | :--- |
| `MainMenuAction` | 60.sp | No | Large landing buttons |
| `ScreenTitle` | 40.sp | Yes | Main headers on screens/dialogs |
| `SectionHeader` | 30.sp | Yes | Internal cockpit/dialog groupings |
| `ActionButton` | 40.sp | No | Primary full-width or large buttons |
| `ActionButtonSmall` | 32.sp | No | Secondary or space-constrained buttons |
| `TdStatusSummary` | 40.sp | No | "Cards: n/52" |
| `TdResultCard` | 50.sp | Yes | The large "Last Scanned" card ID |
| `TdHandSuit` | 24.sp | Yes | Suits in 13-card hand lists |
| `TdHandRanks` | 24.sp | No | Ranks in 13-card hand lists |
| `TdTelemetry` | 24.sp | No | SPS/IDLE and Status messages |
| `TdThoughts` | 20.sp | No | Stabilization layer feedback |
| `ChoiceLabel` | 28.sp | No | Radio button and selection labels |
| `GridItemLabel` | 36.sp | Yes | Board numbers in TD overview grid |
| `ScissorsGridSuit` | 38.sp | No | Large suit headers in Scissors |
| `ScissorsGridRank` | 30.sp | Yes | Rank buttons in Scissors |
| `AdminGridSuit` | 20.sp | No | Headers in Admin calibration |
| `AdminGridRank` | 14.sp | Yes | Rank buttons in Admin calibration |
| `CardInspectionTitle` | 38.sp | Black | Card label in read-only preview |
| `CardInspectionSuit` | 24.sp | Black | Suit symbol in read-only preview |

## Proposed ItrobocTextStyles

```kotlin
object ItrobocTextStyles {
    val MainMenuAction = TextStyle(fontSize = 60.sp)
    
    val ScreenTitle = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold)
    val SectionHeader = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold)
    
    val ActionButton = TextStyle(fontSize = 40.sp)
    val ActionButtonSmall = TextStyle(fontSize = 32.sp)
    
    val TdStatusSummary = TextStyle(fontSize = 40.sp)
    val TdResultCard = TextStyle(fontSize = 50.sp, fontWeight = FontWeight.Bold)
    
    val TdHandSuit = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold)
    val TdHandRanks = TextStyle(fontSize = 24.sp)
    
    val TdTelemetry = TextStyle(fontSize = 24.sp)
    val TdThoughts = TextStyle(fontSize = 20.sp)
    
    val ChoiceLabel = TextStyle(fontSize = 28.sp)
    val GridItemLabel = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold)
    
    val ScissorsGridSuit = TextStyle(fontSize = 38.sp)
    val ScissorsGridRank = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold)
    
    val AdminGridSuit = TextStyle(fontSize = 20.sp)
    val AdminGridRank = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)
    
    val CardInspectionTitle = TextStyle(fontSize = 38.sp, fontWeight = FontWeight.Black)
    val CardInspectionSuit = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Black)
}
```

## Excluded one-offs

- `TdOverviewScreen.kt:316`: `fontSize = 24.sp` for `FilterChip` labels in settings. While it matches `TdTelemetry`, it feels like a standard Material chip size that might stay local or use a different semantic name like `SettingChipLabel`. I've included it under `ChoiceLabel` (merging 24 and 28) or kept it separate if Archy prefers. Actually, let's propose `SettingValue` for it.
- `TdOverviewScreen.kt:351/356`: `fontSize = 36.sp` for slider "Shy"/"Bold" labels. These are very specific to this slider.

## Open questions for Archy

1.  **ActionButton merge?** Should we try to unify 32sp and 40sp action buttons, or is the distinction between "Main" and "Cockpit/Minor" actions intentional?
2.  **ChoiceLabel consistency:** Radio labels are 28sp, but Settings chips are 24sp. Should they be unified to one `ChoiceLabel` (e.g. 26sp)?
3.  **Admin vs Scissors:** Admin uses much smaller fonts (14sp/20sp) than Scissors (30sp/38sp) for the card grids. This is likely because Admin shows 52 cards + camera, while Scissors is a full-screen repair UI.
4.  **Material Theme bridge:** Some Text usages already use `style = MaterialTheme.typography.*`. Should `ItrobocTextStyles` extend or wrap these, or be a separate "Product Typography" layer?
