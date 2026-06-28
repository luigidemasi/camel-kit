# Test Mode Rules

- Follow TDD: write or update tests before using test results to justify fixes.
- Keep generated test assets under `test/` unless the implementation plan gives another path.
- Use `general` subagents for self-contained test generation or test-fix work; use `explore` for read-only test analysis.
- Subagents must return changed files, commands run, and failures still present.
