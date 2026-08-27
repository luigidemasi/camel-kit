## Agent Optimization: OpenCode

### Primary-Session Brainstorming

`/camel-brainstorm` loads this skill in the primary session so user questions, approval, command arguments, and the plan handoff remain available. Do not delegate the complete workflow to the generated `brainstormer` subagent.

Perform requirements discovery and the ordered interview in the primary session. The generated `brainstormer` profile is only for explicitly bounded, non-interactive discovery work and cannot own approval or phase handoff.

### Parallel Tool Calls

Independent reads, searches, and MCP lookups may be issued together in one response. Keep user interview questions ordered and wait for each answer before advancing the design.

### Opt-In LSP for Design Validation

If `lsp` is available and the user has an existing codebase, use it to verify referenced endpoints, bean types, and method signatures. Fall back to read and search tools when LSP is unavailable.
