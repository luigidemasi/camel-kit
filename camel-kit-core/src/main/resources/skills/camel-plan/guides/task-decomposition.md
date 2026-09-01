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
2. **Missing schemas** (conditional) — one per-flow schema task only when the approved design requires generation;
   provides schemas consumed by the route
3. **Route YAML** — depends on scaffold/required schemas and owns the selected inline Groovy or XSLT DataMapper
   implementation
4. **Application properties** — depends on route (needs to know property names)
5. **Maven dependencies** (Spring Boot/Quarkus only) — depends on route
6. **Integration tests** — one Citrus YAML task per route, after that route's implementation artifacts exist

Cross-flow tasks come after all flows:
- Exactly one module-wide Main `run.sh` task after every route/XSL artifact is known; omit it for Spring Boot/Quarkus
- Exactly one Docker Compose task, only when at least one flow requires an external service; it consumes the complete
  module route/XSL inventory and all external-service requirements (never create per-flow Compose files)
- Integration tests whose dependencies span multiple flows

Do not add a static validation task or a separate smoke-test task. After all
planned implementation and test-generation tasks, `camel-execute` owns the
cross-cutting review and internal runtime verification; chained execution then
continues to the report-only `/camel-validate` Phase 4.

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

1. **Agent persona** — which exact entry from the installed shipped persona library to dispatch
2. **Files** — exact paths to create/modify (from orchestrator path table)
3. **Structured metadata** — matching entry in the `yaml plan-metadata` block, including file actions, logical
   `provides`/`consumes`, and explicit `dependsOn`
4. **Guides to load** — exact guide paths from reference skill manifests
5. **MCP tools** — exact tool calls with parameters
6. **Design spec section** — which section of the spec this task implements
7. **Steps** — ordered, checkboxed steps with specific instructions
8. **Verification** — exact command and expected output
9. **Review specification** — what spec compliance and code quality checks to perform

## Structured Metadata Dependency Rules

The plan MUST include a fenced `yaml plan-metadata` block before the first Markdown task. Add one metadata item per
task, and keep `id` aligned with `### Task N`.

Use `dependsOn` for hard sequencing that cannot be inferred from files or logical resources. Use `provides` and
`consumes` for non-file relationships:
- `routes` — route IDs produced or referenced
- `endpoints` — endpoint URIs such as `direct:validate-order`, `seda:orders`, `kafka:orders`
- `properties` — property keys such as `orders.input.dir`
- `schemas` — schema names or schema file identifiers
- `testData` — named test data files or fixtures
- `beans` — bean, processor, or service bean names
- `externalServices` — brokers, databases, HTTP APIs, or other infrastructure services
- `routeContracts` — named contracts between routes or systems

When Task B consumes a resource Task A provides, the analyzer places Task B in a later wave even if they do not touch
the same file. Continue listing shared files because file overlap remains part of wave analysis.

---

## No Placeholders

These are plan failures — never write them:
- "TBD", "TODO", "implement later", "fill in details"
- "Add appropriate error handling" (which error handling? Be specific)
- "Similar to Task N" (repeat the instructions)
- "See the design spec" without specifying which section
- Steps without specific instructions ("Generate the route" — HOW?)
- Verification steps without commands ("Make sure it works")
