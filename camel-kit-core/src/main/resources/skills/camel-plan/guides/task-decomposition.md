# Task Decomposition Rules

> **Context:** Loaded by `camel-plan` to break the design spec into bite-sized tasks.
> **Purpose:** Rules for granularity, ordering, and dependencies.

---

## The Granularity Rule

**Each task produces one testable outcome.** If you can't verify the outcome with a single command or file check, the task is too big.

Good tasks:
- "Scaffold project structure" — verified by `ls` showing expected directories
- "Generate route YAML for flow X" — verified by `test -f` and route YAML inspection
- "Generate application.properties" — verified by `test -f` and property inspection
- "Generate Docker Compose for external services" — verified by `docker compose config`
- "Validate all routes" — verified by validation command output
- "Generate tests for flow X" — verified by test compilation/run

Bad tasks:
- "Implement flow X" — too big, combines route + properties + docker + validation
- "Set up project" — vague, what does "set up" mean?
- "Handle errors" — error handling is per-flow, not standalone

---

## Task Ordering

Tasks follow dependency order within each flow:

1. **Scaffold** (project structure, POM, config) — no dependencies
2. **DataMapper XSLT** (if needed) — before route, because route references XSLT files
3. **Route YAML** — depends on scaffold, DataMapper
4. **Application properties** — depends on route (needs to know property names)
5. **Docker Compose** — depends on route (needs to know external services)
6. **Maven dependencies** (Spring Boot/Quarkus only) — depends on route
7. **Validation** — depends on all files being generated
8. **Testing** — depends on validation passing

Cross-flow tasks come after all flows:
- Cross-cutting validation (constitution compliance across all routes)
- Docker Compose consolidation (merge per-flow compose files)
- Integration tests

---

## Task Independence

Each task MUST be completable by a fresh subagent with zero context beyond:
- The task description itself
- The design spec section referenced by the task
- The guides listed in the task

A task MUST NOT require:
- Reading other tasks to understand what to do
- "Similar to Task N" references — repeat the instructions
- Implicit knowledge from previous tasks
- File outputs from tasks not listed as dependencies

---

## Per-Task Requirements

Every implementation task MUST include:

1. **Agent persona** — which agent from `agents/` to dispatch
2. **Files** — exact paths to create/modify (from orchestrator path table)
3. **Guides to load** — exact guide paths from reference skill manifests
4. **MCP tools** — exact tool calls with parameters
5. **Design spec section** — which section of the spec this task implements
6. **Steps** — ordered, checkboxed steps with specific instructions
7. **Verification** — exact command and expected output
8. **Review specification** — what spec compliance and code quality check

---

## No Placeholders

These are plan failures — never write them:
- "TBD", "TODO", "implement later", "fill in details"
- "Add appropriate error handling" (which error handling? Be specific)
- "Similar to Task N" (repeat the instructions)
- "See the design spec" without specifying which section
- Steps without specific instructions ("Generate the route" — HOW?)
- Verification steps without commands ("Make sure it works")
