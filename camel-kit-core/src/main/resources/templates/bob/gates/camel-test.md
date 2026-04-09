---
name: camel-test
description: Use when you want to generate integration tests for Camel routes using Citrus framework and Testcontainers — writes tests, runs them, verifies they pass
---

# Camel Test — Testing Pipeline (Bob)

Generate integration tests for Apache Camel routes using Citrus framework and Testcontainers. Follow every step in order. Do NOT skip steps.

**Core principle:** Test-Driven Development (TDD) — failing test first, then implementation, then passing test.

<Steps>
<Step>
## Switch to Test Mode

Switch to **camel-test** mode using the mode selector or `/camel-test` command.
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
2. `.camel-kit/config.yaml` — Camel version, runtime, platform BOM
3. `docs/design-spec.md` — approved design spec (if exists)
4. `docs/flows/\{flow-name\}/\{flow-name\}.tdd.md` — Technical Design Document for each route

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

Create or update test infrastructure:

**1. Testcontainers setup** (if external services are used):
```java
@Testcontainers
class RouteIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");
    
    @Container
    static GenericContainer<?> mockApi = new GenericContainer<>("wiremock/wiremock:3.0.0")
        .withExposedPorts(8080);
}
```

**2. Citrus test configuration:**
```java
@CitrusSpringSupport
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RouteIntegrationTest {
    @CitrusEndpoint
    @JmsEndpoint(destinationName = "input.queue")
    private Endpoint inputQueue;
    
    @CitrusEndpoint
    @JmsEndpoint(destinationName = "output.queue")
    private Endpoint outputQueue;
}
```

**3. Test properties** (`src/test/resources/application-test.properties`):
```properties
# Override with Testcontainer URLs
camel.component.jdbc.url=$\{postgres.jdbcUrl\}
external.api.url=$\{mockApi.url\}
```
</Step>

<Step>
## Generate Tests for Each Route

For EACH route:

**CHECKPOINT** — Create a checkpoint before generating tests for this route.

Load `guides/test-generation.md`.

### Test Generation Process

1. **Create test class:**
   - Location: `src/test/java/.../routes/\{RouteNameTest\}.java`
   - Naming: `\{RouteName\}Test` (e.g., `OrderProcessingRouteTest`)

2. **Write happy path test:**
   ```java
   @Test
   @CitrusTest
   void shouldProcessValidOrder(@CitrusResource TestRunner runner) {
       runner.given(
           send(inputQueue)
               .message()
               .body("<order><id>123</id></order>")
       );
       
       runner.when(
           receive(outputQueue)
               .message()
               .body("<orderConfirmation><id>123</id></orderConfirmation>")
       );
       
       runner.then(
           // Verify database state, external API calls, etc.
       );
   }
   ```

3. **Write error path tests:**
   ```java
   @Test
   @CitrusTest
   void shouldHandleInvalidOrderGracefully(@CitrusResource TestRunner runner) {
       runner.given(
           send(inputQueue)
               .message()
               .body("<order><invalid/></order>")
       );
       
       runner.when(
           receive(errorQueue)
               .message()
               .body(contains("Validation failed"))
       );
   }
   ```

4. **Write edge case tests:**
   ```java
   @Test
   @CitrusTest
   void shouldHandleEmptyInput(@CitrusResource TestRunner runner) {
       // Test empty body
   }
   
   @Test
   @CitrusTest
   void shouldHandleLargePayload(@CitrusResource TestRunner runner) {
       // Test 10MB+ payload
   }
   ```

5. **Write external service failure tests:**
   ```java
   @Test
   @CitrusTest
   void shouldRetryOnExternalServiceTimeout(@CitrusResource TestRunner runner) {
       // Mock external API timeout
       // Verify circuit breaker activates
       // Verify retry logic
   }
   ```

Follow TDD's test criteria from the route's TDD file.
</Step>

<Step>
## Run Tests

For each test class:

```bash
mvn test -Dtest=\{RouteNameTest\}
```

**Expected outcome:**
- All tests PASS
- No compilation errors
- No runtime errors
- Test coverage includes happy path + error paths + edge cases

If tests FAIL:
1. Read the failure message
2. Check if route implementation is correct (compare with TDD)
3. Check if test expectations are correct
4. Fix the issue (route OR test)
5. Re-run tests

Do NOT proceed until all tests pass.
</Step>

<Step>
## Verify Test Coverage

For each route, verify test coverage:

**Minimum coverage:**
- 1 happy path test
- 1+ error path tests (one per `onException` handler)
- 1+ edge case tests
- 1+ external service failure tests (if route calls external systems)

**Coverage report:**
```bash
mvn verify
```

Check `target/site/jacoco/index.html` for coverage metrics.

**Thresholds:**
- Line coverage: > 80%
- Branch coverage: > 70%

If coverage is below threshold, add more tests.
</Step>

<Step>
## Cross-Route Integration Tests

Load `guides/graph-project-context.md` if `.camel-kit/project-graph.json` exists.

If routes interact with each other (e.g., Route A → Queue → Route B):

Write integration tests that:
1. Send input to Route A
2. Verify Route B receives and processes the message
3. Verify end-to-end data flow

Example:
```java
@Test
@CitrusTest
void shouldProcessOrderEndToEnd(@CitrusResource TestRunner runner) {
    runner.given(
        http()
            .client("orderApi")
            .send()
            .post("/orders")
            .body("<order><id>123</id></order>")
    );
    
    runner.when(
        receive(orderQueue)
            .message()
            .body("<order><id>123</id></order>")
    );
    
    runner.then(
        receive(confirmationQueue)
            .message()
            .body("<confirmation><id>123</id></confirmation>")
    );
}
```
</Step>

<Step>
## Test Documentation

For each test class, add JavaDoc:

```java
/**
 * Integration tests for the Order Processing route.
 * 
 * <p>This route consumes orders from the input queue, validates them,
 * transforms them, and sends confirmations to the output queue.</p>
 * 
 * <p>Test coverage:</p>
 * <ul>
 *   <li>Happy path: valid order → confirmation</li>
 *   <li>Error path: invalid order → error queue</li>
 *   <li>Edge case: empty order → validation error</li>
 *   <li>Failure case: external API timeout → retry</li>
 * </ul>
 * 
 * @see OrderProcessingRoute
 */
@CitrusSpringSupport
@SpringBootTest
class OrderProcessingRouteTest {
    // tests...
}
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

Label: `post-test-\{date\}`
</Step>

<Step>
## Generate Test Report

Create a test report at `docs/test-report.md`:

```markdown
# Test Report

**Date:** [current date]
**Camel Version:** [from config.yaml]
**Routes Tested:** [N]

---

## Summary

| Metric | Value |
|--------|-------|
| Total Routes | N |
| Total Tests | N |
| Passing Tests | N |
| Failing Tests | 0 |
| Line Coverage | X% |
| Branch Coverage | Y% |

---

## Tests by Route

### Route: \{route-name\}

**File:** `src/main/resources/camel/\{route-name\}.camel.yaml`
**Test Class:** `src/test/java/.../routes/\{RouteNameTest\}.java`

**Tests:**
- ✓ Happy path: valid input → expected output
- ✓ Error path: invalid input → error handling
- ✓ Edge case: empty input → validation error
- ✓ Failure case: external API timeout → retry

**Coverage:**
- Line: 85%
- Branch: 78%

---

## Recommendations

1. Add more edge case tests for \{route-name\}
2. Increase branch coverage in \{route-name\} (currently 78%, target 80%)
```

Present the report to the user.
</Step>

<Step>
## Commit Tests

After all tests pass:

```bash
git add src/test/java src/test/resources docs/test-report.md
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
- **JUnit 5:** Test runner
- **AssertJ:** Fluent assertions

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
