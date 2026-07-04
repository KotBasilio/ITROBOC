# Design: The Beetle's Mind (🪲🧠)

Following the round table discussion, here is the architectural draft for the "thinking" parallel thread.

## The Goal
Move from a reflexive "Eyes see -> Action" model to a cognitive "Eyes see -> Mind ponders -> Action" model for `BarcodeDecodeResult.Ambiguous` cases.

## The 🪲🧠 Architecture

### 1. The Pondering Buffer (The "Boiling Pot")
- A thread-safe, fixed-size queue (e.g., `ArrayDeque` with max 50 entries).
- Every time `handleCameraScan` receives `Ambiguous`, it drops the candidates into the pot.
- If `Found` occurs, the pot is cleared (the eyes have spoken clearly).

### 2. The Mind Thread (Parallel Executor)
- A separate `SingleThreadExecutor` dedicated to "Thinking."
- It doesn't block the 14 SPS Eye Thread. It observes the pot.

### 3. Resolution Logic ("Seeing with the Mind")
The Mind Thread applies two filters:
- **Temporal Consensus**: If 10 frames in a row show the same ambiguity (e.g., "SA or SK"), the confidence in that pair increases.
- **Board Context**: It queries the `currentBoardState`. If "SK" is already on the board, the Mind Thread "resolves" the ambiguity to "SA".

### 4. Convergence (The "Eureka" Moment)
- When the Mind Thread reaches a high-confidence conclusion, it calls back into `handleCameraScan` but with a **Synthesized** `BarcodeDecodeResult.Found`.
- To the UI, it looks like a normal scan, but with a special "Mind-Scan" flag.

## Bob's Implementation Notes 🧩
**Bob ☕🧩:** To keep it lock-free, the Mind Thread should receive an **immutable snapshot** of the `BoardState`. We'll use a `Channel` or a `ConcurrentLinkedQueue` to feed the pot. We must ensure the "Eureka" event doesn't trigger a recursive thinking loop!

## Trace's Philosophical Scope 🫖
**Trace 🫖🧭:** This "perfect convergence" is the key. The bug isn't guessing; it's **inferring**. It turns the vision system from a sensor into a player.

## Fi's Aesthetic Touch ✂️💋
**Fi ✂️💋:** We need a tiny "thinking" animation! Maybe the 🪲 mascot's antennae wiggle when the pot is boiling? 🪲〰️

---

**Next Steps:**
- Define the `BeetleMind` component.
- Draft the `TemporalIntegrator` logic.
- Plan the integration into `EditBoardScreen`.
