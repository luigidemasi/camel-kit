# Task Template — Test Generation

> **Context:** Loaded by `camel-plan` for test generation tasks.
> **Purpose:** Template for generating integration test tasks using Citrus + Testcontainers.

---

## Test Task Sequence

Test tasks come after the implementation artifacts they exercise. Generate one test task per flow.

The plan MUST include testing tasks in the `yaml plan-metadata` block. Test tasks should consume the route, endpoint,
schema, test data, bean, and external service resources they need, and provide generated `testData` and test files.
Use `dependsOn` for implementation tasks whose outputs the test consumes. Do not
depend on a separate validation or smoke-test task: `camel-execute` always runs runtime
verification once after all planned tasks. In chained mode it then invokes static
validation automatically; after a standalone execution, validation is an explicit next command.

### Task Template: Generate Integration Tests (per flow)

```markdown
### Task N: Generate Integration Tests — [flow-name]

Resolve `TEST_DIR` as the optional `[MODULE_DIR]` prefix plus `src/test/resources/`. It is always relative and ends in
`/`; omit the entire `[MODULE_DIR]` prefix when the target module is the project root.

**Agent:** test-engineer

**Files:**
- Create: `[MODULE_DIR]src/test/resources/[flow-name].camel.it.yaml`
- Create: `[MODULE_DIR]src/test/resources/test-data/[flow-name]-input.[ext]`
- Create: `[MODULE_DIR]src/test/resources/test-data/[flow-name]-expected.[ext]`
- Create/Modify: `{TEST_DIR}jbang.properties` (Main only; add test dependencies if not present)
- Modify: `[MODULE_DIR]pom.xml` (Spring Boot/Quarkus only; add test dependencies if not present)

**Guides to Load:**
- `camel-test/guides/route-analysis.md` — identify testable behaviors
- `camel-test/guides/test-generation.md` — test patterns and templates
- `camel-test/guides/test-configuration.md` — Testcontainers setup
- `camel-test/guides/test-runner.md` — execution and verification

**MCP Tools:**
- `camel_catalog_component_doc(component="[source-component]", runtime="{RUNTIME}", platformBom="[project platform BOM GAV]")` — understand trigger behavior
- `camel_catalog_component_doc(component="[sink-component]", runtime="{RUNTIME}", platformBom="[project platform BOM GAV]")` — understand assertion points

**Design Spec Section:** Section 3, Flow: [flow-name]

**Input:** Generated route at `[ROUTE_DIR][flow-name].camel.yaml`

- [ ] Read the generated route YAML for [flow-name]
- [ ] Load route-analysis.md to identify testable behaviors:
  - Happy path: [data flows from source to sink correctly]
  - Error path: [error handling triggers correctly]
  - Edge cases: [empty messages, malformed data, timeouts]
- [ ] Load test-generation.md to create test file:
  - Citrus YAML DSL test structure
  - Mock endpoints for external systems
  - Test data preparation
- [ ] Load test-configuration.md to set up infrastructure:
  - Testcontainers for: [containerized databases/brokers required by this flow]
  - Mock endpoints for: [external HTTP/SaaS APIs and other services without a required local container]
  - Application properties override for test environment
- [ ] Create test data files:
  - Input: representative test message in [format]
  - Expected: expected output message
- [ ] Generate test scenarios in the `.camel.it.yaml` file:
  - Happy path — end-to-end success
  - Error handling — error triggers DLQ/retry
  - Invalid input — malformed input handled
- [ ] For Main, add test dependencies to `{TEST_DIR}jbang.properties`; do not create a `pom.xml`
- [ ] For Spring Boot/Quarkus, add test dependencies to `[MODULE_DIR]pom.xml` if not present:
  - `org.citrusframework:citrus-*`
  - `org.testcontainers:testcontainers`
  - `org.testcontainers:[module]` for each required containerized database/broker
- [ ] Verify: `camel test run [MODULE_DIR]src/test/resources/[flow-name].camel.it.yaml` exits 0 with a passing test
  summary; omit the entire `[MODULE_DIR]` prefix at the project root

**Review:**
- [ ] Spec compliance: tests cover all behaviors in design spec
- [ ] Code quality: proper assertions, realistic test data, no flaky patterns
```

---

## Test Design Principles

Include these in every test task:

1. **One test = one behavior** — each test method validates a single route behavior
2. **Realistic test data** — use representative data, not `{"key": "value"}`
3. **Infrastructure isolation** — Testcontainers for databases, brokers; mock endpoints for external APIs
4. **Assertion completeness** — verify headers, body, and exchange properties
5. **Negative testing** — test error paths, not just happy paths
6. **Idempotent** — tests can run repeatedly with same results

---

## Test Dependencies Reference

For Spring Boot and Quarkus projects, add standard test dependencies to `pom.xml`:

```xml
<!-- Citrus -->
<dependency>
    <groupId>org.citrusframework</groupId>
    <artifactId>citrus-spring-boot</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.citrusframework</groupId>
    <artifactId>citrus-camel</artifactId>
    <scope>test</scope>
</dependency>

<!-- Testcontainers -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

For Main projects, declare the required artifacts through `run.deps` in `{TEST_DIR}jbang.properties` as described in
`camel-test/guides/test-generation.md`; do not create a `pom.xml`.

Add module-specific Testcontainers dependencies based on external services:
- PostgreSQL: `org.testcontainers:postgresql`
- Kafka: `org.testcontainers:kafka`
- MongoDB: `org.testcontainers:mongodb`
- etc.
