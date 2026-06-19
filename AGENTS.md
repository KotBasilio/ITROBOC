# AGENTS

- Read docs/product_context.md before making architectural changes.
- Keep core logic pure and testable.
- Do not add Android camera, UI, CameraX, OpenCV, or device integration code unless the task explicitly asks for it.
- Prefer small, readable Kotlin files.
- Keep bridge-domain concepts explicit.
- Prefer typed result models over booleans/strings for domain outcomes.
- Failed or ignored scans must not silently mutate state.
- Add tests for behavior changes.
- Run ./gradlew test before reporting completion when possible.
- Commit and push at the end of each ticket so review can happen on the remote branch.
