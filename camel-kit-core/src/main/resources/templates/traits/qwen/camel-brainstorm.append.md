## Agent Optimization: Qwen Code

### Fork-Based Research Isolation

Use the fork model to isolate research tasks during brainstorming:

- **Migration scanning:** Fork a research task to scan source project files while the main agent continues the interview. The fork inherits the full conversation context and runs in background.
- **MCP catalog verification:** Fork a verification task to batch-check components via MCP while the main agent assembles design decisions.
- **Documentation lookup:** Fork a knowledge-researcher query when the user mentions an unfamiliar protocol or system.

```text
# Fork runs in background — parent continues interview
Agent({
  prompt: "[catalog-researcher persona + component list from interview answers]"
  // No subagent_type → fork mode, inherits parent context
})
```

**DashScope cache benefit:** Forks share the parent's system prompt prefix, so both hit the same cache — saving 80%+ tokens on concurrent research.

### Progress Tracking via todo_write

Use `todo_write` to track interview progress:

- At the start of brainstorming, write three unchecked items: "Phase 1: Discovery", "Phase 2: Component Selection", "Phase 3: Design Assembly"
- Check off each phase as it completes
- If the interview is interrupted, the todo list shows which phases are done and where to resume

### Named Subagent for Architecture Analysis

For complex design decisions (multi-flow coordination, error handling strategy, migration mapping), delegate to a named subagent:

```text
Agent({
  subagent_type: "camel-brainstormer",
  prompt: "[integration-architect persona + specific design question + user's answers so far]"
})
```

Named subagents block the parent — use them for design decisions that must complete before the interview continues.

### Explicit Context Passing

Include the full interview state (user's answers, selected components, version decisions) in every fork and subagent prompt. Don't assume the subagent will discover context from files — pass it explicitly.
