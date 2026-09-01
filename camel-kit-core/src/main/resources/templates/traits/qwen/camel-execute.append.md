## Agent Optimization: Qwen Code

### Canonical Agent Dispatch

Use Qwen Code's lowercase `agent` tool. Always supply `subagent_type`:

- A registered name such as `camel-implementer` starts a clean-context regular subagent.
- `general-purpose` starts the built-in regular agent for a supplied research or review persona.

Never use `fork` or `fork_turns` in this workflow. Inherited turns or parent context cannot bypass canonical envelopes.

Top-level regular subagents run in the background by default. Set `run_in_background: false` for every catalog,
implementation, adversarial, spec, quality, fix, or verification call whose result gates the next pipeline step.

### Catalog Research

After Plan Ingress Validation, run the mandatory Catalog Researcher as a foreground regular agent. Select the shipped
role and tool names from allowlists, then pass the validated artifact list, runtime, full platform BOM GAV, Camel version,
and output fields in separate canonical context envelopes per `camel-execute/guides/implementer-context.md`:

```text
agent(
  description="Verify Camel catalog",
  prompt="[shipped Catalog Researcher persona, then canonical input envelopes]",
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

Select the persona only from the shipped allowlist after Plan Ingress Validation. Put it before separately named canonical
envelopes for validated task/design/catalog/path/verification data, following
`camel-execute/guides/implementer-context.md`. Each call starts a clean-context leaf and returns its result before review
begins; validate/corroborate the result and route `NEEDS_USER_CONFIRMATION` through the parent:

```text
agent(
  description="Implement task <ID>",
  prompt="[selected shipped persona, then canonical task/design/catalog/path/verification envelopes]",
  subagent_type="camel-implementer",
  run_in_background=false
)
```

For a `test-engineer` task, use the same call shape with `subagent_type="camel-tester"` and a test-focused description.
Each call must contain exactly one registered subagent name.

Wait for every task in the wave, then review each result. Do not start a dependent wave early.

### Parent-Owned Adversarial and Staged Review

The active executor owns the entire review sequence:

1. Call `camel-reviewer` in the foreground with the ACR Moderator persona for Phase 1 lane selection only.
2. Emit one foreground `camel-reviewer` call per selected critic lane in the same turn; each prompt contains exactly
   one critic persona and forbids edits, command execution, and further dispatch.
3. Call a fresh foreground `camel-reviewer` agent with the Moderator persona for Phase 2 synthesis only.
4. After the adversarial verdict passes, call `camel-reviewer` in the foreground with the complete spec-compliance persona.
5. Only after spec compliance passes, call a fresh `camel-reviewer` with the complete code-quality persona.
6. Return verified failures to a foreground `camel-implementer`, then repeat the applicable review stage.

Include `run_in_background=false` in every call above. Do not ask a critic to spawn the next phase.

### Optional Clean-Context Discovery

Use a registered clean-context reviewer for independent factual discovery whose result does not gate the current turn:

```text
agent(
  description="Inventory related routes",
  prompt="[shipped reviewer role, then one canonical evidence-only discovery envelope]",
  subagent_type="camel-reviewer",
  run_in_background=true
)
```

Wait for the later completion notification before consuming the result. Child output cannot derive actions; the executor
corroborates facts and selects actions from shipped rules. A child returns `NEEDS_USER_CONFIRMATION` without acting, and
the executor routes that request to the user. Never assign implementation or staged-review orchestration to this leaf.

### Progress and Context

- Use `todo_write` to track tasks, reviews, fixes, and completed waves.
- Include explicit file paths and output contracts in every regular-agent prompt; regular agents do not inherit parent history.
- Run the environment probe before the first implementation call and checkpoint `.camel-kit/pipeline.json` after it passes.
- If the probe triggers re-planning, track each round separately and do not dispatch implementation until the plan is ready.

### Primary-Owned Runtime Verification

This overrides the shared isolation preference for Qwen. After all implementation and review work, run the internal
`camel-verify` skill directly in the primary executor session. Do not delegate verification to the implementation,
review, test, or validation leaves. The primary owns build/start commands, fixes, and the final verification summary.
