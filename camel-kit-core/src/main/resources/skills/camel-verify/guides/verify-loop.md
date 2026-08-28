# Verification Loop

Core verification guide for `/camel-verify`. Executes a 3-phase loop that builds, runs Citrus integration tests, and reports results — iterating with diagnosis and fixes until the application passes or the iteration limit is reached.

**Always load `error-taxonomy.md` alongside this guide** — it contains the error classification tables referenced in Phases 1 and 2.

---

## Prerequisites Check

Before entering the phase loop, check which tools are available. Report explicitly — no silent assumptions.

### Steps

1. Read `.camel-kit/config.properties` → extract `project.runtime` (one of: `main`, `spring-boot`, `quarkus`)
2. If runtime is Spring Boot/Quarkus, check for Maven wrapper: does `./mvnw` exist in the project root?
   - If yes → use `./mvnw` for all Maven commands
   - If no → check for system `mvn` (`mvn --version`)
   - If neither → Maven is unavailable
   2.1 Set `MAVEN_CMD` to the selected Maven executable (`./mvnw` or `mvn`). For Main, report Maven as `(not needed)`.
   2.2 For Spring Boot/Quarkus, inventory every distinct target module from the approved design and plan. Treat
       `MODULE_DIR` as an optional relative prefix ending in `/`; omit the entire prefix at the project root. For each
       module, resolve `MAVEN_COMPILE_CMD` from the project-root working directory:
       - root POM: `{MAVEN_CMD} compile -q`
       - nested POM: `{MAVEN_CMD} -f {MODULE_DIR}pom.xml compile -q`
       Run Phase 1 for every resolved command. Do not `cd` into a nested module before invoking the project-root wrapper.
3. Check Docker: `docker --version`
   - If available → tests that declare Testcontainers can run in Phase 2
   - If unavailable → Phase 2 can still run discovered container-free and mock-only tests
4. Check JDK: `java --version`
5. If runtime is `main` → also check `jbang --version`
6. Check Camel test CLI: `camel test --help` — needed for Phase 2 test verification

### Report

Print the environment check report before proceeding:

```text
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
| Maven (no `./mvnw`, no `mvn`) | Spring Boot/Quarkus Phase 1 | "Build verification skipped — Maven not available" |
| JDK | Phase 1 for every runtime | "Runtime verification skipped — JDK not available" |
| JBang | Main Phase 1 | "Startup smoke verification skipped — JBang not available" |
| Docker | Phase 2 tests that declare Testcontainers | "Test skipped — Docker not available and this test declares Testcontainers" |
| `camel test` CLI | Phase 2 (Test) | "Test verification skipped — camel test not available" |

---

## Phase 1: Build / Startup Smoke Verification

Compile the project and verify it builds successfully.

**For the main runtime there is no compile step.** Instead of Maven build verification, run the smoke test from `camel-implement/guides/smoke-test.md` as this phase (startup + log markers + fix loop). Only proceed to Phase 2 after the smoke test passes.

Runtime gates:

- Spring Boot/Quarkus Phase 1 runs only when both Maven and the JDK are available; JBang is irrelevant.
- Main Phase 1 runs only when both JBang and the JDK are available; Maven is irrelevant.
- Phase 2 requires the `camel test` CLI. Docker gates only the individual tests classified as Testcontainers-dependent.

### Steps

(spring-boot/quarkus only — for the main runtime, the smoke test above IS Phase 1; skip these steps and proceed to Phase 2 once it passes.)

1. Run each resolved `{MAVEN_COMPILE_CMD}` (capture stdout + stderr and its module). All module builds must pass.
2. If output contains `BUILD SUCCESS` → proceed to Phase 2
3. If output contains `BUILD FAILURE` → enter the iteration loop:

**Iteration loop (Phase 1):**

```text
iteration_count = 0
previous_error = null

while iteration_count < 15:
    1. Re-run the failing module's {MAVEN_COMPILE_CMD} (capture output)
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
       - camel-validate → load and run camel-validate for static diagnosis, then load camel-implement to apply the correction to the affected flow
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

Run Citrus integration tests via the Camel CLI test runner. Each test starts the Camel integration via
`camel:jbang:run`, sends messages, and validates responses. Tests that declare Testcontainers also start and tear down
their external services; container-free and mock-only tests do not require Docker.

### Skip Conditions

Skip Phase 2 (with explicit message) when:
- **Phase 1 failed** (code does not compile — skip for Maven projects): "Test verification skipped — build failed."
- **No test files exist** (no `*.it.yaml` files in test directories): "No Citrus test files found. Generate with camel-test or provide manually."
- **`camel test` CLI is not available**: "Test verification skipped — `camel test` CLI not available."
- **Docker is not available and every discovered test declares Testcontainers**: "Test verification skipped — all
  discovered tests require Docker." Record each dependent test file as skipped.

### Steps

1. **Discover test files:** find all `*.it.yaml` files in the project.
2. **Classify Docker dependency:** inspect every discovered file. A file is Docker-dependent when it declares a
   `testcontainers:` action or references a `CITRUS_TESTCONTAINERS_*` value; otherwise it is container-free/mock-only.
3. **Select runnable tests:** when Docker is unavailable, record each Docker-dependent file as skipped and retain every
   container-free/mock-only file. Skip the phase only when no runnable files remain.
4. **Run:** `camel test run {runnable-test-files}` (capture stdout + stderr).
5. **Parse results:**
   - Success: output contains test pass summary (e.g., "X tests passed, 0 failures")
   - Failure: extract failing test name, assertion message, expected vs actual values
6. If all runnable tests pass → proceed to Phase 3 and preserve any per-file Docker skips in the report.

### Iteration Loop (Phase 2)

When tests fail, classify and route the fix:

```text
iteration_count = 0
previous_error = null

while iteration_count < 15:
    1. Run: camel test run {runnable-test-files} (capture output)
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
       - camel-test → re-generate the test from the design spec
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

```text
VERIFICATION REPORT
Runtime:          {runtime}
Maven:            {status}

Phase 1 — Build / Startup Smoke:  {PASS [(N fixes)] | SKIPPED (reason) | FAILED after N iterations}
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

```text
VERIFICATION REPORT
Runtime:          Quarkus
Maven:            ./mvnw (wrapper)

Phase 1 — Build / Startup Smoke:  PASS (1 fix: added camel-quarkus-jdbc)
Phase 2 — Test:   PASS: 5/5 tests passed

Fixes applied:
  1. [Build] Added camel-quarkus-jdbc to pom.xml (ClassNotFoundException)

Skipped checks:
  (none)
```

**Test failure with fix:**

```text
VERIFICATION REPORT
Runtime:          Spring Boot
Maven:            ./mvnw (wrapper)

Phase 1 — Build / Startup Smoke:  PASS
Phase 2 — Test:   PASS: 3/3 tests passed (2 fixes)

Fixes applied:
  1. [Test] Fixed order-transform route — wrong XPath for customer/name
  2. [Test] Added missing namespace declaration in XSLT

Skipped checks:
  (none)
```

**No tools available:**

```text
VERIFICATION REPORT
Runtime:          Quarkus
Maven:            not found (no ./mvnw, no system mvn)

Phase 1 — Build / Startup Smoke:  SKIPPED (Maven not available)
Phase 2 — Test:   SKIPPED (all discovered tests require Docker)

No verification could be performed.
Install Maven wrapper (./mvnw) or system Maven to enable build verification.
Install Docker to enable the discovered Testcontainers-dependent tests.
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
| Wrong component options | camel-validate → camel-implement | Diagnose against the MCP catalog, then correct the affected flow |
| Constitution violation at runtime | camel-validate → camel-implement | Diagnose the violation, then correct the affected flow |
| YAML schema error | camel-validate → camel-implement | Diagnose the schema error, then correct the affected flow |
| Route YAML structurally broken | camel-implement | Re-generate route from the design spec (affected flow only) |
| Wrong component URI | camel-implement | Re-check design spec, re-generate |
| Missing bean | camel-implement | Re-generate with correct annotations |
| XSLT/Groovy transformation error | camel-implement | Re-run DataMapper validation + re-generate |
| Expression evaluation failure | camel-implement | Re-generate expression from design spec |
| Citrus assertion mismatch | camel-implement | Fix the route/transformation logic to match expected output |
| Citrus test timeout | camel-implement or Self-repair | Fix slow route logic, or increase timeout in test config |
| Testcontainer launch failure | Self-repair | Fix Docker/service configuration |
| Test YAML parse/compilation error | camel-test | Re-generate the test from design spec |
| Wrong test assertion | camel-test | Re-generate the test with correct expected values |
| Same error after fix | Tier 1/Tier 2 promotion | Check MCP catalog, then trigger `camel-execute/guides/re-plan-loop.md` |
| 15 iterations reached | Escalate | "Iteration limit reached" |
| Unclassified error | Escalate | Raw output + suggestion |
| Required tool unavailable | Escalate | "Need {tool} but not available" |
