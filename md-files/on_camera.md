# On Camera, Frame Processing, and Beetle Mind

Last aligned with source snapshot: `7b247cc`.

Vacation note from Selyn. Current implementation note: the neutral shared camera/analyzer name is now `CameraFrameDecoder`. For compatibility, the existing `AdminEditCameraFrameDecoder` implementation remains as the backing class while call sites can migrate gradually.

## Short verdict

The current ITROBOC shape works, but the camera preview rendering and camera frame processing are still too married.

The key seam:

> Camera preview rendering and camera frame processing are different stories.

The Compose screen should host the cockpit. It should not become the place where the beetle's eye, retina, and mind all meet.

## Cleaner conceptual split

```text
Camera surface
    = show live camera preview

Frame analyzer
    = ImageProxy -> CameraScanOutcome

Beetle mind
    = CameraScanOutcome -> stable scan decision / thought / dream

Controller
    = accepted scan decision -> reducer action

Screen
    = layout + buttons + state display
```

Beetle-language version:

```text
Eye surface      = what TD sees
Retina/analyzer  = pixels into barcode evidence
Mind             = evidence into trusted decision
Law book         = reducer mutates board
Cockpit          = Compose screen
```

## Current naming decision

The neutral frame-decoder name is settled as:

```kotlin
CameraFrameDecoder
```

The previous smell was that `AdminEditCameraFrameDecoder` was used beyond Admin. That meant it had become shared camera/analyzer infrastructure while still carrying an Admin-only name.

Current compatibility shape:

```text
CameraFrameDecoder = neutral shared name
AdminEditCameraFrameDecoder = current backing implementation / legacy call-site name
```

This is a safe transitional step. The important design direction is no longer open: future shared camera/analyzer work should use the neutral `CameraFrameDecoder` name.

## What belongs in Beetle Mind

Move or isolate evidence-trust logic into a pure-ish mind object:

```text
requiredConsensusFrames
ponderingSignature
ponderingCount
lastRawSignature
lastScanTimeMillis
debounceWindowMillis
unknown-signature handling
thoughts/dream state
Found/Ambiguous/NotFound stabilization policy
```

Possible shape:

```kotlin
internal class BeetleMind(
    private val config: BeetleMindConfig,
    private val nowMillis: () -> Long,
) {
    fun observe(input: BeetleMindInput): BeetleMindOutput
    fun dream(topic: String): BeetleMindOutput
    fun reset(): BeetleMindOutput
}
```

Then `EditBoardController` becomes thinner:

```text
handleCameraScan(outcome)
  -> convert outcome to BeetleMindInput
  -> mind.observe(...)
  -> if Accepted(signature), call reducer path
  -> update UI message/thought
```

The controller routes. The mind thinks.

## What should not go into Beetle Mind

Keep these out:

```text
CameraX lifecycle
PreviewView
ImageProxy.close()
Android permissions
Compose rendering
Storage/export
PBN
actual BoardState mutation
```

The mind should not own pixels or laws. It should own trust in evidence.

```text
Vision sees.
Mind hesitates.
Reducer decides legality.
Screen shows.
```

## Shared camera component idea

There is duplicated camera-preview/analyzer logic between Admin and TD. A future shared component might look like:

```kotlin
@Composable
internal fun BarcodeCameraScanner(
    mode: CameraScanMode,
    frameDecoder: CameraFrameDecoder,
    onScanOutcome: (CameraScanOutcome) -> Unit,
    overlay: @Composable () -> Unit,
)
```

Where mode expresses Admin vs TD:

```kotlin
sealed interface CameraScanMode {
    data object Stream : CameraScanMode          // TD
    data class Snap(val request: AtomicBoolean)  // Admin
}
```

Admin says: scan one requested frame.

TD says: stream continuously.

## Suggested ticket

### Ticket: Extract Beetle Mind and neutral camera scan pipeline

Goal:
Separate TD screen rendering from camera frame processing and scan stabilization.

Steps:
1. Extract Beetle Mind consensus/debounce/dream logic from `EditBoardController` into a pure Kotlin class.
2. Keep controller responsible for routing accepted scan decisions to `EditBoardReducer`.
3. Migrate shared frame-decoder call sites to the neutral `CameraFrameDecoder` name.
4. Optionally extract duplicated CameraPreview logic from Admin and TD into shared `BarcodeCameraScanner`.
5. Preserve existing behavior:
   - default Perception = 4;
   - Shy/Bold frames 6..2;
   - dreams Eyes/Hands/Wind/Joker;
   - manual add bypasses stabilization.
6. Add BeetleMind unit tests independent of Compose/CameraX.

Non-goals:
- Do not change decoder logic.
- Do not change reducer laws.
- Do not change PBN export.
- Do not change Scissors semantics.

## Tests to want

```text
BeetleMind requires N stable frames before Accept
BeetleMind resets/dreams on Eyes/Hands/Wind/Joker
Ambiguous blanks or displays uncertainty without accepting
Unknown signature says No / never stabilizes
Debounce prevents repeated same signature acceptance
Changing requiredConsensusFrames changes acceptance threshold
Controller applies reducer only on Accepted output
```

## Summary

The current code is green functionally, but the next architectural maturation is:

```text
EditBoardScreen should stop being the place where the beetle's eye, retina, and mind all meet.
```

Let the screen host the cockpit.
Let camera infrastructure feed scan evidence.
Let Beetle Mind stabilize evidence.
Let the reducer mutate board truth.
