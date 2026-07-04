# Vision Analyzer Lock-Free Inspection

Following Bob's (🧩) deep dive into the `AdminEditCameraFrameDecoder` and its surrounding orchestration, here are the findings and proposed optimizations to keep the vision analyzer as lock-free as possible.

## Current State

The analysis happens in a single-threaded executor (`analysisExecutor`), which is good for avoiding thread contention during the heavy-lifting of barcode decoding.

```kotlin
// In EditBoardScreen.kt
val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
```

However, the `AdminEditCameraFrameDecoder` itself is **stateful**:
- `reusableRoiPixels: ByteArray` is used to avoid re-allocating memory for every frame's ROI (Region of Interest).

```kotlin
// In AdminEditCameraSupport.kt
internal class AdminEditCameraFrameDecoder(...) {
    private var reusableRoiPixels = ByteArray(0)

    fun decode(imageProxy: ImageProxy): CameraScanOutcome {
        // ...
        reusableRoiPixels = ensurePixelCapacity(reusableRoiPixels, roi.width * roi.height)
        // ...
    }
}
```

### Contention Points
1.  **Stateful Decoder**: While currently called from a single thread, if we ever wanted to parallelize frame analysis (e.g., analyze every 2nd frame on a different thread), this `reusableRoiPixels` would cause a race condition without synchronization.
2.  **AtomicBoolean Gating**: `pendingScanRequest: AtomicBoolean` is used to skip analysis if a previous scan is still being processed by the UI or if scanning is disabled. This is already a lock-free pattern.

## Proposed "Lock-Free" Optimizations

### 1. Thread-Local Buffers (C++ style `thread_local`)
If we want to keep the decoder stateful but thread-safe without locks, we can move the reusable buffer into a `ThreadLocal`. This is similar to how a high-performance C++ analyzer might use per-thread scratchpads.

### 2. Immutable Decoder with External Buffers
Alternatively, we can make the `AdminEditCameraFrameDecoder` completely stateless (immutable) and pass the `reusableRoiPixels` as a parameter to the `decode` function. The caller (the `analysisExecutor` task) would own the buffer.

### 3. Allocation-Free ROI Extraction
The current `ensurePixelCapacity` might re-allocate if the ROI size changes. While rare in our "centered barcode" case, we could pre-allocate for the maximum possible ROI size based on `AdminScanGuideSpec`.

## Conclusion for Archy (🧭)
**Bob ☕🧩:** The current implementation is effectively lock-free because it's pinned to a single thread. However, it’s "unsafe" if moved to a thread pool. I recommend moving towards a **stateless decoder** where the scratchpad buffer is managed by the analysis loop itself. This is more in line with C++ performance thinking: keep the logic pure and the memory management explicit.

**Trace 🫖🧭:** I like the stateless approach. it makes the decoder easier to test in isolation and removes the "hidden" state that could bite us later.

**Fi ✂️💋:** No locks, no blocks! Just smooth sailing for our analyzer.
