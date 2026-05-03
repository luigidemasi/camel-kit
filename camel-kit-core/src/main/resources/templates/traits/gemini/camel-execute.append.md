## Agent Optimization: Gemini CLI

### Named Agent Delegation

Delegate implementation tasks to pre-registered agents by name:

- Implementation tasks: delegate to `camel-implementer`
- Spec review: delegate to `camel-validator`
- Quality review: delegate to `camel-validator` (reuse with different prompt)

Include `max_turns: 50` and `timeout_mins: 15` in each delegation to prevent runaway agents.

### TOML Policy for MCP Auto-Approval

The project's `.gemini/policies/camel-kit.toml` should include auto-approval for MCP catalog tools. If it doesn't, instruct the user to add:

```toml
[[rules]]
name = "Allow Camel MCP tools"
toolName = "mcp_camel_*"
decision = "allow"
priority = 3
```

This eliminates permission prompts during implementation, which would break autonomous execution.

### Batch Context Loading for Environment Probe

Before the environment probe runs, use `read_many_files` to load all TDD files in a single call:

- Load all `docs/flows/{flow-name}/{flow-name}.tdd.md` files
- Load `.camel-kit/config.properties`
- This gives the probe full context for skeleton generation without multiple sequential reads
