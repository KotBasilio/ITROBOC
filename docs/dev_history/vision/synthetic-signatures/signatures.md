# Opaque Synthetic Raw Signatures

## Overview
Bridge cards in demo presets are represented by **opaque synthetic raw signatures**.  
These are hexadecimal identifiers ranging from `0x1001` through `0x1034`, covering all 52 cards exactly once.

- **Range:** `0x1001` (SA) through `0x1034` (C2)
- **Count:** 0x34 hex = 52 decimal
- **Purpose:** Provides a non-semantic, machine-friendly mapping while preserving deterministic order.

## Mapping Scheme
The `BuiltInDeckProfiles.demoBridge52()` mapping follows the **bridge/PBN sequence**:
1. **Suits:** Spades (S), Hearts (H), Diamonds (D), Clubs (C)
2. **Ranks:** A, K, Q, J, T, 9, 8, 7, 6, 5, 4, 3, 2

### Examples
- `0x1001` → Spades Ace (SA)
- `0x1002` → Spades King (SK)
- ...
- `0x100D` → Spades Two (S2)
- `0x100E` → Hearts Ace (HA)
- ...
- `0x1034` → Clubs Two (C2)

## Usage in Scanning
The `:app` layer accepts these signatures as a comma-separated string (e.g., `0x1001,0x1002`). The `:core` logic performs a lookup in the `DeckProfile` to associate the scanned signature with a specific `CardId` and assign it to the active `Seat`.

## Presets & Integrity
A valid deal requires **exactly 13 unique signatures** per seat (North, East, South, West). The system flags:
- **Unknown Signatures:** IDs not found in the profile (e.g., `0xDEAD`).
- **Duplicate Conflicts:** Same signature scanned by two different seats.
- **Local Redundancy:** Same signature scanned multiple times by the same seat (idempotent but noted).
