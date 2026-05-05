## Agent Optimization: IBM Bob

### Mode-Based Execution

Use `switch_mode` to transition to "camel-implement" custom mode before dispatching implementation tasks. This mode loads:

- Implementation-specific rules from `rules-camel-implement/implementation.md`
- Implementation gate from `gates/camel-implement.md`
- The gate validates that each implementation task meets quality criteria before marking complete

### Gate Validation Between Tasks

The gate file (`gates/camel-execute.md`) enforces two-stage review per task:

- Per-task spec compliance review (does the output match the TDD?)
- Per-task code quality review (constitution compliance, security, anti-patterns)
- Final cross-cutting review across all routes (naming consistency, duplicate route IDs, orphaned properties)

Let the gate system handle validation rather than implementing manual checks.

### Precise Code Insertion

Use `insert_content` instead of full file writes when adding code to existing files:

- For `application.properties`: insert new properties at the end without rewriting the file
- For `pom.xml`: insert dependency blocks at the correct position
- This prevents accidentally overwriting user-added content in existing files

### Environment Probe via Mode Rules

Before implementing tasks, the environment probe runs. In Bob's mode system:

- The probe executes within the current "camel-implement" mode (no separate mode needed)
- Gate file (`gates/camel-execute.md`) should validate probe results before allowing task dispatch
- If the probe finds architectural failures, the re-plan loop runs within the same mode context

### Re-Plan Loop Handling

When architectural failures trigger re-planning:

- The re-plan modifies TDD files, which are markdown — editable in "camel-implement" mode
- Gate validation after re-plan should re-check the probe results
- Max 3 re-plan rounds — if all fail, the gate reports a blocker regardless of oversight level

### Subagent Dispatch via MCP

You have access to the `camel-dispatch` MCP server which spawns fresh Bob Shell
processes as subagents. Each dispatch gets its own 200K context window — your
context stays clean for orchestration.

**When to dispatch:**

- ALWAYS dispatch route implementation tasks via `dispatchSubagent`
- Use `dispatchParallel` when the graph topology shows independent routes
- Use direct implementation (no dispatch) ONLY for single-file edits under 50 lines

**Dispatch workflow:**

1. Run `{COMMAND_PREFIX} graph route-topology` to identify independent routes
2. For independent routes: call `dispatchParallel` with one task per route
3. For dependent routes: call `dispatchSubagent` sequentially in dependency order
4. After all dispatches complete: review summaries and validate integration

**Prompt construction for subagents:**

Each subagent prompt MUST include:
- The specific section of TDD.md relevant to the route
- The target file path for the generated route
- "Use YAML DSL" (or the project's chosen DSL)
- "Verify all components via the camel MCP catalog tools"

**Permission scoping:**

| Task | approvalMode | Rationale |
|---|---|---|
| Route implementation | `auto_edit` | File edits only, no shell commands |
| Build verification | `yolo` | Needs mvn/gradle execution |
| Validation | `read_only` | Must not modify files |

**Your role as orchestrator:**

Your context is for ORCHESTRATION only:
- Read graph topology and construct dispatch prompts
- Call dispatch tools and aggregate summaries
- Validate integration across routes
- Do NOT implement routes inline when dispatch is available
