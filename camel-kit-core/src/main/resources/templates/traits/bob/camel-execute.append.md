## Agent Optimization: IBM Bob

### Mode-Based Execution

Use `switch_mode` to transition to "camel-implement" custom mode before dispatching implementation tasks. This mode loads:

- Implementation-specific rules from `rules-camel-implement/implementation.md`
- Implementation gate from `gates/camel-implement.md`
- The gate validates that each implementation task meets quality criteria before marking complete

### Gate Validation Before Phase Transitions

Before transitioning from implementation to review, the gate file (`gates/camel-execute.md`) automatically validates:

- All tasks in the plan are addressed
- Generated files exist at expected paths
- No YAML syntax errors in generated routes

Let the gate system handle validation rather than implementing manual checks.

### Precise Code Insertion

Use `insert_content` instead of full file writes when adding code to existing files:

- For `application.properties`: insert new properties at the end without rewriting the file
- For `pom.xml`: insert dependency blocks at the correct position
- This prevents accidentally overwriting user-added content in existing files
