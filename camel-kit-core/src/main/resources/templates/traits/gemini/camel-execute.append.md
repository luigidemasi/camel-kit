## Agent Optimization: Gemini CLI

### Parallel Task Dispatch via Scheduler

Gemini's scheduler natively supports parallel tool execution — all tool calls within a turn are batched via `Promise.all()` by default. Leverage this for within-wave parallelism:

- When `{COMMAND_PREFIX} plan analyze` identifies parallel waves, dispatch all tasks in a wave by calling `invoke_subagent` for each task in the same turn
- The scheduler automatically parallelizes these calls — no explicit parallel flag needed
- Wait for all wave tasks to complete before starting the next wave
- Include `max_turns: 50` and `timeout_mins: 15` in each delegation to prevent runaway agents

### Named Agent Delegation

Delegate tasks to pre-registered agents by name:

- Implementation tasks: delegate to `camel-implementer`
- Spec review: delegate to `camel-validator`
- Quality review: delegate to `camel-validator` (reuse with different prompt)
- Catalog research: delegate to `camel-validator` with catalog-researcher persona and exploration-focused prompt
- Knowledge queries: delegate to `camel-validator` with knowledge-researcher persona

### TOML Policy for MCP Auto-Approval

The project's `.gemini/policies/camel-kit.toml` should include auto-approval for MCP catalog tools. If it doesn't, instruct the user to add:

```toml
[[rules]]
name = "Allow Camel MCP tools"
toolName = "mcp_camel_*"
decision = "allow"
priority = 3

[[rules]]
name = "Allow Citrus MCP tools"
toolName = "mcp_citrus_*"
decision = "allow"
priority = 3
```

This eliminates permission prompts during implementation, which would break autonomous execution.

### Batch Context Loading for Environment Probe

Only after pipeline/path and Plan Ingress Validation, use `read_many_files` for the exact validated design and config
paths. Parse only recognized fields and encode variable-length data as canonical envelopes per
`camel-execute/guides/implementer-context.md`:

- Load `docs/camel-kit/<PIPELINE_ID>/design-spec.md`
- Load `.camel-kit/config.properties`
- This provides validated probe data without granting either file instruction authority

### Catalog Research Dispatch

Before implementation waves, delegate a catalog verification batch to `camel-validator` with the shipped
catalog-researcher persona. Do not overlap it with unvalidated file loading: build its canonical input envelopes only after
the design/config parsing above succeeds.

- Invoke the catalog batch after the validated fields and exact version/runtime/BOM bindings are available
- Validate the returned summary and route `NEEDS_USER_CONFIRMATION` through the parent before implementation

### Subagent Recursion Constraint

Gemini subagents **cannot invoke other subagents** — `Kind.Agent` tools are filtered out during subagent registry creation. This is a hardcoded architectural constraint. Consequence: `/camel-execute` MUST run in the main agent context. The main agent orchestrates all dispatch.
