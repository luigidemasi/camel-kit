## Agent Optimization: Qwen Code

### Explicit Background Forks for Factual Research

Use Qwen Code's lowercase `agent` tool with `subagent_type: "fork"` only for self-contained factual research that can
finish asynchronously while the active brainstorming agent continues the interview. Omitting `subagent_type` launches
the regular general-purpose agent; it does not create a fork.

```text
agent(
  description="Verify Camel catalog",
  prompt="[component list, Camel version, exact catalog questions, and a request for an evidence-only result]",
  subagent_type="fork",
  run_in_background=true,
  fork_turns="all",
  fork_tools=["read_file", "read_many_files", "glob", "grep_search", "web_fetch", "mcp__camel"]
)
```

Forks inherit the selected parent history and always run detached. Wait for the completion notification before using a
fork's result in the design; never assume the result is available in the launching turn. A fork cannot dispatch any
subagent, so give it one complete research task and never assign design judgment, critic orchestration, or nested work.

Suitable fork work includes source inventory, independent documentation lookup, and catalog fact gathering. The primary
session owns interview decisions, architecture trade-offs, design assembly, and the approval gate.

### Progress Tracking via todo_write

Use `todo_write` to track interview progress:

- At the start of brainstorming, write three unchecked items: "Phase 1: Discovery", "Phase 2: Component Selection", "Phase 3: Design Assembly"
- Check off each phase as it completes
- If the interview is interrupted, the todo list shows which phases are done and where to resume

### Explicit Context Passing

Include the exact task, Camel version, relevant user answers, and expected evidence in every fork prompt. Use
`fork_turns` only when inherited conversation context is actually required.
