## Dispatch

For each computational step in the Guide Manifest, use Copilot CLI subagents when useful:

- Prefer the project custom agents in `.github/agents/` for specialized work.
- Use the `task` tool when the current session has it available.
- Include the guide path, Camel version from `.camel-kit/config.properties`, prior artifact paths, and the required output path in each subagent prompt.
- Keep implementation, test, validation, catalog research, and security review work in separate agents when the task is large enough to benefit from isolation.
- Keep validator agents read-only; the primary session writes their returned validation report content.

### Fallback

If subagent dispatch is unavailable, read the guide directly into the main context and execute its instructions inline. This uses more tokens but produces equivalent results.
