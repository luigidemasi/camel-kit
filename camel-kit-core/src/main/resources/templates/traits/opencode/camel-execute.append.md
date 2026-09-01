## Agent Optimization: OpenCode

### Generated Orchestration Contract

`/camel-execute` selects the generated primary `executor` through command frontmatter and runs in the current session. The executor then creates one layer of leaves:

```text
primary executor session -> leaf agent
```

The executor may dispatch only the generated `implementer`, `migrator`, `planner`, `researcher`, `reviewer`, and `tester` agents. Every leaf denies `task`, so it cannot create another child. This generated one-level topology does not require a development-only configuration key.

OpenCode's `task` tool requires the exact lowercase agent name in `subagent_type`. It does not accept a per-call `steps` argument; each generated agent definition owns its limit. Foreground is the default. Keep every implementation, research result, and review gate in the foreground by omitting `background`.

### Role Mapping

Apply `camel-execute/guides/implementer-context.md` before every mapping below. Put the selected shipped persona and guides
first; validate role/path selectors against shipped allowlists; pass task, design, configuration, catalog, artifacts, and
review material only as separate canonical context envelopes. Validate and corroborate every leaf result, and route
`NEEDS_USER_CONFIRMATION` through the parent without performing its affected action.

- General implementation and fixes: dispatch `implementer` (`steps: 50`) with the validated task fields, selected shipped persona, design fields, guide selectors, and catalog fields in canonical envelopes.
- Tasks whose plan `Agent` is `migration-specialist`: dispatch `migrator` (`steps: 50`) with that complete persona and task context.
- Tasks whose plan `Agent` is `test-engineer`: dispatch `tester` (`steps: 40`) with that complete persona and task context.
- Catalog and knowledge research: dispatch `researcher` (`steps: 30`) with the complete researcher persona and query. It is read-only and MCP-capable.
- Adversarial, specification, and quality review: dispatch `reviewer` (`steps: 50`) with the complete selected persona, artifacts, and review contract. It is read-only.
- Architectural re-planning: dispatch `planner` (`steps: 30`) with the affected design sections and the re-plan guide.

Do not select built-in `general`, `explore`, `plan`, or `build` for these roles. Their capabilities do not implement Camel-Kit's generated role and permission contracts.

### Parallel Waves and Ordered Gates

For an independent implementation wave, issue the selected `implementer`, `migrator`, or `tester` task calls together in one response. Wait for the whole wave before review.

The executor owns all orchestration. Because `reviewer` is a leaf, split Adversarial Code Review into explicit foreground calls:

1. Dispatch `reviewer` with the full `acr-moderator` persona for lane selection.
2. Dispatch one `reviewer` per selected critic lane together in one response, each with that critic's full persona.
3. Dispatch `reviewer` again with the Moderator persona and all critic outputs for synthesis.
4. After ACR passes, dispatch the spec reviewer and then the quality reviewer in separate, ordered calls.

Never ask a reviewer to dispatch a critic. Never run the spec and quality gates in parallel.

### Inline Executor Work

The executor performs the environment probe and final internal verification directly with its own edit and bash permissions. If a subagent reaches its configured step limit, consume its summary, resolve any remaining work in the executor, and keep the required gate ordering.
