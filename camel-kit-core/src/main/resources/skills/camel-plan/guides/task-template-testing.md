# Task Template — Test Generation

> **Context:** Loaded by `camel-plan` for test generation tasks.
> **Purpose:** Template for generating integration test tasks using Citrus + Testcontainers.

---

## Test Task Sequence

Test tasks come after all implementation and validation tasks. Generate one test task per flow.

### Task Template: Generate Integration Tests (per flow)

```markdown
### Task N: Generate Integration Tests — [flow-name]

**Agent:** test-engineer

**Files:**
- Create: `[MODULE_DIR]src/test/resources/[flow-name].camel.it.yaml`
- Create: `[MODULE_DIR]src/test/resources/test-data/[flow-name]-input.[ext]`
- Create: `[MODULE_DIR]src/test/resources/test-data/[flow-name]-expected.[ext]`
- Modify: `[MODULE_DIR]pom.xml` (add test dependencies if not present)

**Guides to Load:**
- `camel-test/guides/route-analysis.md` — identify testable behaviors
- `camel-test/guides/test-generation.md` — test patterns and templates
- `camel-test/guides/test-configuration.md` — Testcontainers setup
- `camel-test/guides/test-runner.md` — execution and verification

**MCP Tools:**
- `camel_catalog_component(name="[source-component]")` — understand trigger behavior
- `camel_catalog_component(name="[sink-component]")` — understand assertion points

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
  - Testcontainers for: [list external services]
  - Application properties override for test environment
- [ ] Create test data files:
  - Input: representative test message in [format]
  - Expected: expected output message
- [ ] Generate test scenarios in the `.camel.it.yaml` file:
  - Happy path — end-to-end success
  - Error handling — error triggers DLQ/retry
  - Invalid input — malformed input handled
- [ ] Add test dependencies to pom.xml if not present:
  - `org.citrusframework:citrus-*`
  - `org.testcontainers:testcontainers`
  - `org.testcontainers:[module]` for each external service
- [ ] Verify: `camel test run src/test/resources/[flow-name].camel.it.yaml` passes

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

Standard test dependencies for Camel projects:

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

Add module-specific Testcontainers dependencies based on external services:
- PostgreSQL: `org.testcontainers:postgresql`
- Kafka: `org.testcontainers:kafka`
- MongoDB: `org.testcontainers:mongodb`
- etc.
