## Dispatch

Use Codex subagents for self-contained work that benefits from an isolated context:

- Use the built-in `explorer` or `camel_catalog_researcher` for read-only project and MCP research.
- Use the generated `camel_planner`, `camel_implementer`, `camel_tester`, `camel_validator`,
  `camel_migrator`, and `camel_security_reviewer` roles for their named responsibilities.
- Spawn independent tasks from the same implementation wave in parallel. Keep dependent waves sequential.
- The parent remains the orchestrator, supplies exact input and output paths, and verifies returned work.
- Delegated agents must not spawn more agents; they return a concise result to the parent.

Include the Camel version from `.camel-kit/config.properties`, relevant user decisions, the guide paths to read,
prior artifact paths, and verification commands in each delegated task.

### Fallback

If subagent dispatch or a generated role is unavailable, read the guide directly and execute it in the current
session. This uses more context but preserves the workflow.
