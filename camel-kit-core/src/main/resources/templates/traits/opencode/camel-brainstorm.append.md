## Agent Optimization: OpenCode

### LLM-Level Parallel Tool Calls

OpenCode supports parallel tool calls at the LLM level — multiple tool call blocks in a single response execute concurrently. Leverage this during brainstorming:

- Read the input requirements file and existing design spec (if amending) in a single response
- Load multiple guide files in parallel when entering a new interview phase
- Run MCP catalog verification alongside design document reads

### Agent Type for Brainstorming

Use the `Plan` agent type for the brainstorming phase:

- `Plan` is optimized for structured analysis and design output
- Read-only access prevents accidental file modifications during the interview
- Subagent dispatch via `task` is available if needed for migration source scanning

### Step Budget

Budget `steps: 200` for the brainstorming phase. The interview can be lengthy — don't cut it short. Distribute steps across phases:

- Discovery phase: ~80 steps (requirements gathering, migration scanning)
- Component selection: ~60 steps (MCP catalog verification, pattern research)
- Design assembly: ~60 steps (design spec generation, version selection)

### Migration Source Scanning

When the user is migrating from an existing platform, use `Explore` subagents for source analysis:

- Dispatch an `Explore` subagent with `steps: 30` to scan the source project structure
- The explorer returns a summary of classes, endpoints, and integration patterns
- Use the summary to inform component mapping decisions during the interview

### Opt-In LSP for Design Validation

If `lsp` is available and the user has an existing codebase:

- Use `lsp` find-references to verify that endpoints referenced in the design actually exist
- Use `lsp` hover to inspect types when mapping existing beans to Camel processors
- Fall back to grep-based validation if LSP is unavailable
