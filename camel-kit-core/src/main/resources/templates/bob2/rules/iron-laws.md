# Camel-Kit Iron Laws for Bob 2

These rules apply across all Camel-Kit Bob 2 modes.

1. Verify every Camel component, EIP, dataformat, and language through the configured MCP tools before writing it into a design, plan, route, or test.
2. Read and follow `docs/constitution.md` in every pipeline phase.
3. Do not generate implementation artifacts before the design spec and implementation plan exist.
4. Run spec compliance and code quality review after implementation work. Use `explore` subagents for independent read-only review when the review is self-contained.
5. Use the Camel versions from `.camel-kit/config.properties`; do not guess versions from model memory.
6. Keep changes surgical. Do not refactor unrelated code or remove user content.
7. Verify generated applications with the runtime and test commands required by the active skill.
8. Treat project graph data as an enhancement. If graph data is unavailable, continue with the shared skill fallback.

Bob 2-specific rule: the parent Bob task remains the orchestrator. Subagents must perform their assigned focused task and return a summary; they must not spawn subagents.
