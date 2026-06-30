---
name: camel-test
description: Use when you want to generate integration tests for Camel routes using Citrus framework and Testcontainers — writes tests, runs them, verifies they pass
---

# Camel Test — Testing Pipeline (Bob)

Generate integration tests for Apache Camel routes using Citrus framework and Testcontainers. Follow every step in order. Do NOT skip steps.

**Core principle:** Test-Driven Development (TDD) — failing test first, then implementation, then passing test.

## Guide Locations

All testing guides are in `.bob/skills/camel-test/guides/`. When this file says `guides/X.md`, read `.bob/skills/camel-test/guides/X.md`. Do NOT explore or list directories to find guides.

<Steps>
<Step>
## Switch to Test Mode

Switch to **camel-test** mode using the mode selector.
This enables test generation and execution capabilities.
</Step>

<Step>
## Verify Routes Exist

Find all YAML route files:
```bash
find src/main/resources/camel -name "*.camel.yaml"
```

List all discovered routes. If none found, report and stop.
</Step>

<Step>
## Load Testing Context

Read these files:
1. `docs/constitution.md` — constitution rules
2. `.camel-kit/config.properties` — Camel version, runtime, platform BOM
3. `docs/camel-kit/<PIPELINE_ID>/design-spec.md` — approved design spec
4. `docs/camel-kit/<PIPELINE_ID>/test-data/<flow-name>/` — synthetic I/O pairs when present

Load testing guides:
- `guides/route-analysis.md` — analyze routes to identify testable behaviors
- `guides/test-generation.md` — test generation patterns and templates
- `guides/test-configuration.md` — test infrastructure setup
- `guides/test-runner.md` — test execution and verification
</Step>

<Step>
## Analyze Each Route

For EACH route file:

Load `guides/route-analysis.md`.

Analyze:
- **Source endpoint** — what triggers this route? (HTTP, file, timer, Kafka, etc.)
- **Sink endpoint** — where does data go? (HTTP, database, file, JMS, etc.)
- **Transformations** — what data transformations occur? (XSLT, JSONPath, etc.)
- **Error handling** — what errors are expected? How are they handled?
- **Side effects** — what external systems are called? (HTTP APIs, databases, queues)

Identify testable behaviors:
- Happy path: valid input → expected output
- Error paths: invalid input → expected error handling
- Edge cases: empty input, large input, malformed input
- External service failures: HTTP timeout, database connection error, etc.
</Step>

<Step>
## Generate Test Infrastructure

Load `guides/test-configuration.md`.

Create or update Citrus YAML test infrastructure:

**1. Testcontainers actions** (if external services are used):
```yaml
actions:
  - testcontainers:
      start:
        postgresql: {}
```

**2. Test properties** (`src/test/resources/application-test.properties`):
```properties
# Override with Testcontainer URLs
camel.component.jdbc.url=$\{CITRUS_TESTCONTAINERS_POSTGRESQL_URL\}
external.api.url=http://localhost:8080
```

**3. JBang test dependencies** (`src/test/resources/jbang.properties` or the plan-specified test directory):
```properties
run.deps=org.citrusframework:citrus-camel:<CITRUS_VERSION>,\
org.citrusframework:citrus-testcontainers:<CITRUS_VERSION>,\
org.citrusframework:citrus-yaml:<CITRUS_VERSION>
```
</Step>

<Step>
## Generate Tests for Each Route

For EACH route:

**CHECKPOINT** — Create a checkpoint before generating tests for this route.

Load `guides/test-generation.md`.

### Test Generation Process

1. **Create test file:**
   - Location: `src/test/resources/<flow-name>.camel.it.yaml`
   - Naming: `<flow-name>.camel.it.yaml` (e.g., `order-processing.camel.it.yaml`)

2. **Write happy path test:**
   ```yaml
   name: <flow-name>-happy-path
   variables:
     - name: kafka.brokers
       value: localhost:9092

   actions:
     - camel:
         jbang:
           run:
             integration:
               file: "../../main/resources/camel/<flow-name>.camel.yaml"
             wait:
               for:
                 log:
                   message: "started and consuming"
               timeout: 30000

     - send:
         endpoint:
           uri: <source-endpoint>
         message:
           body:
             file: test-data/valid-input.json

     - receive:
         endpoint:
           uri: <sink-endpoint>
         timeout: 10000
         message:
           body:
             file: test-data/expected-output.json
   ```

3. **Write error path tests:**
   ```yaml
     - send:
         endpoint:
           uri: <source-endpoint>
         message:
           body:
             data: '{"invalid": "data"}'

     - receive:
         endpoint:
           uri: <dead-letter-endpoint>
         timeout: 10000
   ```

4. **Write edge case tests:**
   ```yaml
     - send:
         endpoint:
           uri: <source-endpoint>
         message:
           body:
             data: ''

     - send:
         endpoint:
           uri: <source-endpoint>
         message:
           body:
             file: test-data/large-payload.json
   ```

5. **Write external service failure tests:**
   ```yaml
     - send:
         endpoint:
           uri: <source-endpoint>
         message:
           body:
             file: test-data/valid-input.json

     - receive:
         endpoint:
           uri: <fallback-endpoint>
         timeout: 30000
   ```

Follow the test criteria from the route's design spec section.
</Step>

<Step>
## Run Tests

For each test file:

```bash
camel test run src/test/resources/<flow-name>.camel.it.yaml
```

**Expected outcome:**
- All tests PASS
- No compilation errors
- No runtime errors
- Test coverage includes happy path + error paths + edge cases

If tests FAIL:
1. Read the failure message
2. Check if route implementation is correct (compare with the design spec)
3. Check if test expectations are correct
4. Fix the issue (route OR test)
5. Re-run tests

Do NOT proceed until all tests pass.
</Step>

<Step>
## Verify Test Coverage

For each route, verify behavioral scenario coverage:

**Minimum coverage:**
- 1 happy path test
- 1+ error path tests (one per `onException` handler)
- 1+ edge case tests
- 1+ external service failure tests (if route calls external systems)

If scenario coverage is below threshold, add more Citrus YAML scenarios.
</Step>

<Step>
## Cross-Route Integration Tests

Load `guides/graph-project-context.md` if `.camel-kit/project-graph.json` exists.

If routes interact with each other (e.g., Route A → Queue → Route B):

Write integration tests that:
1. Send input to Route A
2. Verify Route B receives and processes the message
3. Verify end-to-end data flow

Example (`end-to-end.camel.it.yaml`):
```yaml
name: order-end-to-end

actions:
  - camel:
      jbang:
        run:
          integration:
            file: "../../main/resources/camel/order-processing.camel.yaml"
          wait:
            for:
              log:
                message: "started and consuming"
            timeout: 30000

  - send:
      endpoint:
        uri: http://localhost:8080/orders
      message:
        body:
          data: '{"id": "123"}'

  - receive:
      endpoint:
        uri: kafka:order-queue
      timeout: 10000
      message:
        body:
          data: '{"id": "123"}'

  - receive:
      endpoint:
        uri: kafka:confirmation-queue
      timeout: 10000
      message:
        body:
          data: '{"id": "123", "status": "confirmed"}'
```
</Step>

<Step>
## Test Documentation

Each test file should include a descriptive `name` field and inline comments:

```yaml
# Integration tests for the Order Processing route.
#
# This route consumes orders from the input queue, validates them,
# transforms them, and sends confirmations to the output queue.
#
# Test coverage:
#   - Happy path: valid order → confirmation
#   - Error path: invalid order → error queue
#   - Edge case: empty order → validation error
#   - Failure case: external API timeout → retry

name: order-processing-integration-tests
# ...test actions...
```
</Step>

<Step>
## CHECKPOINT

After all tests pass and coverage is verified:

**CHECKPOINT** — Create a post-test checkpoint.

This checkpoint captures:
- All route implementations
- All test implementations
- Passing test results
- Coverage reports

Label: `post-test-<date>`
</Step>

<Step>
## Generate Test Report

Create a test report at `docs/test-report.md`:

```markdown
# Test Report

**Date:** [current date]
**Camel Version:** [from config.properties]
**Routes Tested:** [N]

---

## Summary

| Metric | Value |
|--------|-------|
| Total Routes | N |
| Total Tests | N |
| Passing Tests | N |
| Failing Tests | 0 |
| Scenario Coverage | happy path, error paths, edge cases |

---

## Tests by Route

### Route: <route-name>

**File:** `src/main/resources/camel/<route-name>.camel.yaml`
**Test File:** `src/test/resources/<route-name>.camel.it.yaml`

**Tests:**
- ✓ Happy path: valid input → expected output
- ✓ Error path: invalid input → error handling
- ✓ Edge case: empty input → validation error
- ✓ Failure case: external API timeout → retry

**Scenario Coverage:**
- Happy path: yes
- Error paths: yes
- Edge cases: yes

---

## Recommendations

1. Add more edge case tests for <route-name>
2. Add a failure scenario for <route-name> external service timeout
```

Present the report to the user.
</Step>

<Step>
## Commit Tests

After all tests pass:

```bash
git add src/test/resources docs/test-report.md
git commit -m "test: add integration tests for all routes"
```
</Step>
</Steps>

## Iron Laws

Testing enforces:
- **Iron Law 1**: MCP Catalog Verification — understand component behavior via MCP for accurate test assertions

## Test Frameworks

- **Citrus:** Integration testing framework for Camel routes
- **Testcontainers:** Lightweight Docker containers for external services
- **Camel JBang test plugin:** Runs Citrus YAML tests with `camel test run`

## Guide Reference

| Guide | When to Load |
|-------|-------------|
| `guides/route-analysis.md` | Always |
| `guides/test-generation.md` | Always |
| `guides/test-configuration.md` | Always |
| `guides/test-runner.md` | Always |
| `guides/graph-project-context.md` | When `.camel-kit/project-graph.json` exists |

## TDD Enforcement

Tests MUST be written in this order:
1. Write the failing test (route doesn't exist yet or incomplete)
2. Implement the route (or fix it)
3. Run the test — it MUST pass

Do NOT write tests after implementation. Tests come FIRST.
