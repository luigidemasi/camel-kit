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
