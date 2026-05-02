# Verification Loop

Core verification guide for `/camel-verify`. Executes a 3-phase loop that builds, runs Citrus integration tests, and reports results — iterating with diagnosis and fixes until the application passes or the iteration limit is reached.

**Always load `error-taxonomy.md` alongside this guide** — it contains the error classification tables referenced in Phases 1 and 2.

---

## Prerequisites Check

Before entering the phase loop, check which tools are available. Report explicitly — no silent assumptions.

### Steps

1. Read `.camel-kit/config.properties` → extract `project.runtime` (one of: `quarkus`, `springboot`, `jbang`)
2. Check for Maven wrapper: does `./mvnw` exist in the project root?
   - If yes → use `./mvnw` for all Maven commands
   - If no → check for system `mvn` (`mvn --version`)
   - If neither → Maven is unavailable
3. Check Docker: `docker --version`
   - If available → Testcontainers can manage external services in Phase 2
   - If unavailable → test verification will be skipped
4. Check JDK: `java --version`
5. If runtime is JBang → also check `jbang --version`
6. Check Camel test CLI: `camel test --help` — needed for Phase 2 test verification

### Report

Print the environment check report before proceeding:

```
ENVIRONMENT CHECK
Runtime:        {runtime from config.properties}
Maven:          {./mvnw (wrapper) | mvn (system) | not found}
Docker:         {docker {version} | not found}
JDK:            {{vendor} {version} | not found}
JBang:          {{version} | not found | (not needed)}
Camel test CLI: {available | not found}

Ready for: {list of phases that can run}
Skipped:   {list of phases that cannot run, with reason}
```

### Graceful Degradation

If a tool is missing, skip the phases that depend on it. Never fail silently — always report what was skipped and why.

| Missing Tool | Phases Affected | Message |
|---|---|---|
| Maven (no `./mvnw`, no `mvn`) | Phase 1 (Build) | "Build verification skipped — Maven not available" |
| JDK | Phase 1 (Build) | "Build verification skipped — JDK not available" |
| Docker | Phase 2 (Test) | "Test verification skipped — Docker not available (Testcontainers requires Docker)" |
| `camel test` CLI | Phase 2 (Test) | "Test verification skipped — camel test not available" |

---

## Phase 1: Build Verification

Compile the project and verify it builds successfully.

**Skip this phase entirely for JBang runtime** — JBang compiles at runtime, there is no separate build step. Proceed directly to Phase 2.

### Steps

1. Run: `./mvnw compile -q` (capture stdout + stderr)
2. If output contains `BUILD SUCCESS` → proceed to Phase 2
3. If output contains `BUILD FAILURE` → enter the iteration loop:

**Iteration loop (Phase 1):**

```
iteration_count = 0
previous_error = null

while iteration_count < 15:
    1. Run: ./mvnw compile -q (capture output)
    2. If BUILD SUCCESS → break (proceed to Phase 2)
    
    3. Extract the error message from the output
    4. If this is the SAME error as previous_error:
       → Short-circuit: "Fix did not resolve the error. Same error after {iteration_count} iterations."
       → Check Tier 1/Tier 2 promotion (see Re-Plan Trigger below)
    
    5. Classify the error using error-taxonomy.md (Build Errors section)
    6. If UNCLASSIFIED:
       → Escalate: "Unknown build error" + raw output
       → Stop Phase 1
    
    7. Read the Fix target from the classification:
       - Self-repair → edit the file directly (pom.xml, application.properties)
       - camel-validate → load and run camel-validate skill
       - camel-implement → load and run camel-implement for the affected flow
       - Escalate → report to user and stop Phase 1
    
    8. Apply the fix
    9. previous_error = current_error
    10. iteration_count += 1

if iteration_count >= 15:
    → "Iteration limit reached. 15 fixes attempted without resolving build errors."
    → Escalate to user
```

---

## Phase 2: Test Verification

Run Citrus integration tests via the Camel CLI test runner. Citrus tests are self-contained: they start Testcontainers for external services, start the Camel integration via `camel:jbang:run`, send test messages, validate responses, and tear everything down.

### Skip Conditions

Skip Phase 2 (with explicit message) when:
- **Phase 1 failed** (code does not compile — skip for Maven projects): "Test verification skipped — build failed."
- **No test files exist** (no `*.it.yaml` files in test directories): "No Citrus test files found. Generate with camel-test or provide manually."
- **`camel test` CLI is not available**: "Test verification skipped — `camel test` CLI not available."
- **Docker is not available** (Testcontainers requires Docker): "Test verification skipped — Docker not available (Testcontainers requires Docker)."

### Steps

1. **Discover test files:** find all `*.it.yaml` files in the project
2. **Run:** `camel test run {test-files}` (capture stdout + stderr)
3. **Parse results:**
   - Success: output contains test pass summary (e.g., "X tests passed, 0 failures")
   - Failure: extract failing test name, assertion message, expected vs actual values
4. If all tests pass → proceed to Phase 3

### Iteration Loop (Phase 2)

When tests fail, classify and route the fix:

```
iteration_count = 0
previous_error = null

while iteration_count < 15:
    1. Run: camel test run {test-files} (capture output)
    2. If all tests pass → break (proceed to Phase 3)
    
    3. Extract the failing test details from the output
    4. If this is the SAME error as previous_error:
       → Short-circuit: "Fix did not resolve the test failure."
       → Check Tier 1/Tier 2 promotion (see Re-Plan Trigger below)
    
    5. Classify the error using error-taxonomy.md (Test Errors section)
    6. If UNCLASSIFIED:
       → Escalate: "Unknown test error" + raw output
       → Stop Phase 2
    
    7. Read the Fix target from the classification:
       - camel-implement → fix the route/transformation logic
       - camel-test → re-generate the test from TDD
       - Self-repair → fix Docker/service config
       - Escalate → report to user and stop Phase 2
    
    8. Apply the fix
    9. previous_error = current_error
    10. iteration_count += 1

if iteration_count >= 15:
    → "Iteration limit reached. 15 fixes attempted."
    → Escalate to user
```

### Re-Plan Trigger

When the same error class persists after fix attempts:

**Tier 1 (immediate):** After 1 failed fix, query MCP catalog. If MCP confirms the component/extension/feature does not exist for this runtime+version, trigger `camel-execute/guides/re-plan-loop.md` immediately.

**Tier 2 (progressive):** After 3 failed fix attempts on the same error class, trigger `camel-execute/guides/re-plan-loop.md`.

See `camel-execute/guides/re-plan-loop.md` for the full re-plan process.

---

## Phase 3: Report

Generate a structured verification report summarizing all phases.

### Report Template

```
VERIFICATION REPORT
Runtime:          {runtime}
Maven:            {status}

Phase 1 — Build:  {PASS [(N fixes)] | SKIPPED (reason) | FAILED after N iterations}
Phase 2 — Test:   {PASS: N/N tests passed [(N fixes)] | SKIPPED (reason) | FAILED: N/N tests failed}

{If any fixes were applied:}
Fixes applied:
  1. [{Phase}] {description of fix}
  2. [{Phase}] {description of fix}

{If any phases were skipped:}
Skipped checks:
  - {description} ({reason})

{If a phase failed — show last error:}
Last error:
  {error detail or assertion message}
  Classification: {category from error-taxonomy.md}
  Fix attempted: {what was tried}

  Escalated: {suggestion for manual resolution}
```

### Report Examples

**Full success:**

```
VERIFICATION REPORT
Runtime:          Quarkus
Maven:            ./mvnw (wrapper)

Phase 1 — Build:  PASS (1 fix: added camel-quarkus-jdbc)
Phase 2 — Test:   PASS: 5/5 tests passed

Fixes applied:
  1. [Build] Added camel-quarkus-jdbc to pom.xml (ClassNotFoundException)

Skipped checks:
  (none)
```

**Test failure with fix:**

```
VERIFICATION REPORT
Runtime:          Spring Boot
Maven:            ./mvnw (wrapper)

Phase 1 — Build:  PASS
Phase 2 — Test:   PASS: 3/3 tests passed (2 fixes)

Fixes applied:
  1. [Test] Fixed order-transform route — wrong XPath for customer/name
  2. [Test] Added missing namespace declaration in XSLT

Skipped checks:
  (none)
```

**No tools available:**

```
VERIFICATION REPORT
Runtime:          Quarkus
Maven:            not found (no ./mvnw, no system mvn)

Phase 1 — Build:  SKIPPED (Maven not available)
Phase 2 — Test:   SKIPPED (Docker not available)

No verification could be performed.
Install Maven wrapper (./mvnw) or system Maven to enable build verification.
Install Docker to enable test verification (Testcontainers requires Docker).
```

---

## Fix Routing Summary

Quick reference for the verify loop. For full details on each error pattern, see `error-taxonomy.md`.

| Error Classification | Fix Target | Action |
|---|---|---|
| Missing Maven dependency | Self-repair | Add `<dependency>` to pom.xml (runtime-aware artifact naming) |
| Missing `application.properties` entry | Self-repair | Add property with placeholder value |
| Missing `camel.jbang.dependencies` | Self-repair | Add to `application.properties` |
| Version incompatibility | Self-repair | Check BOM version, align dependencies |
| Wrong component options | camel-validate | Re-validate against MCP catalog |
| Constitution violation at runtime | camel-validate | Re-run constitution compliance checks |
| YAML schema error | camel-validate | Re-run YAML schema validation |
| Route YAML structurally broken | camel-implement | Re-generate route from TDD (affected flow only) |
| Wrong component URI | camel-implement | Re-check TDD, re-generate |
| Missing bean | camel-implement | Re-generate with correct annotations |
| XSLT/Groovy transformation error | camel-implement | Re-run DataMapper validation + re-generate |
| Expression evaluation failure | camel-implement | Re-generate expression from TDD |
| Citrus assertion mismatch | camel-implement | Fix the route/transformation logic to match expected output |
| Citrus test timeout | camel-implement or Self-repair | Fix slow route logic, or increase timeout in test config |
| Testcontainer launch failure | Self-repair | Fix Docker/service configuration |
| Test YAML parse/compilation error | camel-test | Re-generate the test from TDD |
| Wrong test assertion | camel-test | Re-generate the test with correct expected values |
| Same error after fix | Tier 1/Tier 2 promotion | Check MCP catalog, then trigger `camel-execute/guides/re-plan-loop.md` |
| 15 iterations reached | Escalate | "Iteration limit reached" |
| Unclassified error | Escalate | Raw output + suggestion |
| Required tool unavailable | Escalate | "Need {tool} but not available" |
