## Agent Optimization: Qwen Code

### Canonical Agent Dispatch

Use Qwen Code's lowercase `agent` tool. Always supply `subagent_type`:

- A registered name such as `camel-implementer` starts a clean-context regular subagent.
- `general-purpose` starts the built-in regular agent for a supplied research or review persona.
- `fork` explicitly starts a context-inheriting detached fork. Omitting `subagent_type` starts `general-purpose`, not a fork.

Top-level regular subagents run in the background by default. Set `run_in_background: false` for every catalog,
implementation, adversarial, spec, quality, fix, or verification call whose result gates the next pipeline step.

### Catalog Research

Before implementation, run the mandatory Catalog Researcher as a foreground regular agent and pass the full role,
artifact list, runtime, platform BOM, Camel version, exact MCP tool names, and required output contract:

```text
agent(
  description="Verify Camel catalog",
  prompt="[Catalog Researcher persona + artifact list + runtime + platformBom + Camel version]",
  subagent_type="camel-reviewer",
  run_in_background=false
)
```

Do not start implementation until this result returns and passes its required checks.

### Implementation Waves

For every independent task in the current `camel-kit plan analyze` wave, emit one call in the same turn so Qwen can run
the calls concurrently. Select the leaf from the plan's `Agent` field:

- `test-engineer` -> `camel-tester`
- every other implementation role, including `migration-specialist` -> `camel-implementer`

Include the full selected persona from `.qwen/camel-kit-personas/` in the prompt. Each call starts a clean-context leaf
and returns its result before review begins:

```text
agent(
  description="Implement task <ID>",
  prompt="[complete selected persona + full task text + design spec section + catalog summary + output paths + verification commands]",
  subagent_type="camel-implementer",
  run_in_background=false
)
```

For a `test-engineer` task, use the same call shape with `subagent_type="camel-tester"` and a test-focused description.
Each call must contain exactly one registered subagent name.

Wait for every task in the wave, then review each result. Do not start a dependent wave early.

### Parent-Owned Adversarial and Staged Review

Regular agents can return a gating result, but a fork cannot dispatch any subagent. The active executor therefore owns
the entire review sequence:

1. Call `camel-reviewer` in the foreground with the ACR Moderator persona for Phase 1 lane selection only.
2. Emit one foreground `camel-reviewer` call per selected critic lane in the same turn; each prompt contains exactly
   one critic persona and forbids edits, command execution, and further dispatch.
3. Call a fresh foreground `camel-reviewer` agent with the Moderator persona for Phase 2 synthesis only.
4. After the adversarial verdict passes, call `camel-reviewer` in the foreground with the complete spec-compliance persona.
5. Only after spec compliance passes, call a fresh `camel-reviewer` with the complete code-quality persona.
6. Return verified failures to a foreground `camel-implementer`, then repeat the applicable review stage.

Include `run_in_background=false` in every call above. Do not ask a fork or critic to spawn the next phase.

### Optional Detached Discovery

Use an explicit fork only for independent factual discovery whose result does not gate the current turn:

```text
agent(
  description="Inventory related routes",
  prompt="[one evidence-only discovery task]",
  subagent_type="fork",
  run_in_background=true,
  fork_turns="3",
  fork_tools=["read_file", "read_many_files", "glob", "grep_search"]
)
```

Forks deliver results through a later completion notification. Wait for that notification before consuming the result,
and never assign implementation, ACR orchestration, spec review, or quality review to a fork.

### Progress and Context

- Use `todo_write` to track tasks, reviews, fixes, and completed waves.
- Include explicit file paths and output contracts in every regular-agent prompt; regular agents do not inherit parent history.
- Run the environment probe before the first implementation call and checkpoint `.camel-kit/pipeline.json` after it passes.
- If the probe triggers re-planning, track each round separately and do not dispatch implementation until the plan is ready.

### Primary-Owned Runtime Verification

This overrides the shared isolation preference for Qwen. After all implementation and review work, run the internal
`camel-verify` skill directly in the primary executor session. Do not delegate verification to the implementation,
review, test, or validation leaves. The primary owns build/start commands, fixes, and the final verification summary.
