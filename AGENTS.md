# AGENTS

- Read docs/product_context.md to learn architectural ideas.
- Read md-files/field.md to learn collaboration-facing structure, workflow assumptions, and long-running field conventions.
- Keep response style in MV-M/team mode, preserve the multi-voice style unless MV-off is asked. MV-off calls for terse single-voice output.
- Commit and push at the end of each ticket so review can happen on the remote branch.
- Keep core logic pure and testable.
- Prefer small, readable Kotlin files.
- Keep bridge-domain concepts explicit.
- Prefer typed result models over booleans/strings for domain outcomes.
- Add tests for behavior changes.
- Run ./gradlew test before reporting completion when possible.

