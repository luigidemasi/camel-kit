# Verification Loop

Core verification guide for `/camel-verify`. Executes a 5-phase loop that builds, starts, tests, diagnoses, fixes, and retries until the application runs correctly or the iteration limit is reached.

**Always load `error-taxonomy.md` alongside this guide** — it contains the error classification tables referenced in Phases 2, 3, and 4.

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
   - If available → Docker Compose can manage external services
   - If unavailable → external service phases will be skipped
4. Check JDK: `java --version`
5. If runtime is JBang → also check `jbang --version`
6. Check Camel CLI: `camel --version` — needed for Phase 4 behavioral verification

### Report

Print the environment check report before proceeding:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ENVIRONMENT CHECK
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Runtime:    {runtime from config.properties}
Maven:      {✅ ./mvnw (wrapper) | ✅ mvn (system) | ❌ not found}
Docker:     {✅ docker {version} | ❌ not found}
JDK:        {✅ {vendor} {version} | ❌ not found}
JBang:      {✅ jbang {version} | ❌ not found | (not needed)}
Camel CLI: {✅ camel {version} | ❌ not found}

Ready for: {list of phases that can run}
Skipped:   {list of phases that cannot run, with reason}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### Graceful Degradation

If a tool is missing, skip the phases that depend on it. Never fail silently — always report what was skipped and why.

| Missing Tool | Phases Affected | Message |
|---|---|---|
| Maven (no `./mvnw`, no `mvn`) | Phase 2, Phase 3 (Quarkus/Spring Boot) | "Build and startup verification skipped — Maven not available" |
| Docker | Phase 1 (external services) | "External services skipped — Docker not available. Services must be started manually." |
| JBang | Phase 3 (JBang runtime only) | "Startup verification skipped — JBang not available" |
| JDK | Phase 2, Phase 3 | "Build and startup verification skipped — JDK not available" |
| `camel` CLI | Phase 4 | "Behavioral verification skipped — `camel` CLI not available" |

---

## Phase 1: Environment Preparation

Ensure external services required by the application are running.

### Steps

1. Read `.camel-kit/config.properties` → extract runtime
2. Read `docs/business-requirements.md` (if it exists) → extract the **Systems Landscape** table to identify external systems, their roles, and protocols
3. Read all TDD files (`docs/flows/**/*.tdd.md`) → extract from each:
   - **Section 2 (Source System):** component, protocol, authentication requirements
   - **Section 4 (Sink System):** component, protocol, authentication requirements
   - **Section 7 (Configuration Properties):** required connection properties (hosts, ports, credentials)
   - **Section 8 (Dependencies):** Maven coordinates and external service dependencies
4. Cross-reference the BRD systems with TDD components to build a complete picture of required external services (databases, message brokers, mail servers, etc.)
5. **If services are needed AND Docker is available:**
   a. Check if `docker-compose.yaml` exists in the project root
   b. If not → load `camel-implement/guides/docker-compose.md` to generate it
   c. Run `docker compose up -d`
   d. Wait for services to become healthy: poll `docker compose ps` every 5 seconds for up to 60 seconds, looking for "healthy" or "running" status
   e. If a service fails to start → report which service failed and include the Docker logs (`docker compose logs {service} --tail=20`)
6. **If services are needed but Docker is NOT available:**
   ```
   ⚠️ Skipping external services — Docker not available
   External dependencies found:
     - {service} (source: {TDD name}, Section {N})
     - {service} (source: BRD Systems Landscape, role: {role})
   These services must be running for startup verification to succeed.
   ```
7. **If no external services are required:** report "No external services required" and proceed

**No retry loop for Phase 1.** Docker-compose issues are operational (image pull, container start) — not fixable by editing Camel project files. If services cannot start, report the failure and proceed to Phase 2.

---

## Phase 2: Build Verification

Compile the project and verify it builds successfully.

**Skip this phase entirely for JBang runtime** — JBang compiles at runtime, there is no separate build step. Proceed directly to Phase 3.

### Steps

1. Run: `./mvnw compile -q` (capture stdout + stderr)
2. If output contains `BUILD SUCCESS` → proceed to Phase 3
3. If output contains `BUILD FAILURE` → enter the iteration loop:

**Iteration loop (Phase 2):**

```
iteration_count = 0
previous_error = null

while iteration_count < 15:
    1. Run: ./mvnw compile -q (capture output)
    2. If BUILD SUCCESS → break (proceed to Phase 3)
    
    3. Extract the error message from the output
    4. If this is the SAME error as previous_error:
       → Short-circuit: "Fix did not resolve the error. Same error after {iteration_count} iterations."
       → Escalate to user and stop Phase 2
    
    5. Classify the error using error-taxonomy.md (Build Errors section)
    6. If UNCLASSIFIED:
       → Escalate: "Unknown build error" + raw output
       → Stop Phase 2
    
    7. Read the Fix target from the classification:
       - Self-repair → edit the file directly (pom.xml, application.properties)
       - camel-validate → load and run camel-validate skill
       - camel-implement → load and run camel-implement for the affected flow
       - Escalate → report to user and stop Phase 2
    
    8. Apply the fix
    9. previous_error = current_error
    10. iteration_count += 1

if iteration_count >= 15:
    → "Iteration limit reached. 15 fixes attempted without resolving build errors."
    → Escalate to user
```

---

## Phase 3: Startup Verification

Start the application and verify it launches without errors.

### Steps

1. Determine the startup command based on runtime:
   - **Quarkus:** `./mvnw quarkus:dev -Dquarkus.analytics.disabled=true -Dquarkus.console.enabled=false`
   - **Spring Boot:** `./mvnw spring-boot:run`
   - **JBang:** `camel run *.camel.yaml *.xsl application.properties`

2. Start the application and capture logs for up to 60 seconds, or until one of these patterns appears:
   
   **Success patterns (stop capturing, app is running):**
   - Quarkus: `Listening on: http://`
   - Spring Boot: `Started ` followed by ` in ` followed by ` seconds`
   - JBang/Camel: `Routes startup` or `Total` followed by `routes started`
   
   **Failure patterns (stop capturing, app failed):**
   - Any of: `FailedToCreateRouteException`, `NoSuchEndpointException`, `ResolveEndpointFailedException`, `NoSuchBeanException`, `UnsatisfiedDependencyException`, `BUILD FAILURE`
   - Java stack trace starting with `Exception in thread` or `Caused by:`
   - Process exit with non-zero code

3. If started successfully:
   - Note the number of routes started (parse from log: `Total {N} routes started` or `{N} routes started`)
   - Keep the application running for Phase 4
   - Proceed to Phase 4

4. If startup failed → enter the iteration loop:

**Iteration loop (Phase 3):**

```
iteration_count = 0
previous_error = null

while iteration_count < 15:
    1. Start the application (using the runtime-specific command above), capture logs
    2. Wait for success or failure pattern (up to 60 seconds)
    3. If success pattern found → break (proceed to Phase 4)
    
    4. Extract the error from the log output
    5. If this is the SAME error as previous_error:
       → Short-circuit: "Fix did not resolve the error. Same error after {iteration_count} iterations."
       → Escalate to user and stop Phase 3
    
    6. Classify the error using error-taxonomy.md (Startup Errors and Runtime Errors sections)
    7. If UNCLASSIFIED:
       → Escalate: "Unknown startup error" + raw log output
       → Stop Phase 3
    
    8. Special case: if error is "Connection refused" on a known port:
       → Try `docker compose restart {service}` first before other fixes
    
    9. Read the Fix target from the classification:
       - Self-repair → edit the file directly
       - camel-validate → load and run camel-validate skill
       - camel-implement → load and re-generate the affected flow
       - Escalate → report to user and stop Phase 3
    
    10. Apply the fix
    11. Stop the running application before retrying
    12. previous_error = current_error
    13. iteration_count += 1

if iteration_count >= 15:
    → "Iteration limit reached. 15 fixes attempted without resolving startup errors."
    → Escalate to user
```

Note: Phase 3 has its own independent iteration counter (max 15), separate from Phase 2's counter.

5. After the iteration loop, if the application is now running → proceed to Phase 4

---

## Phase 4: Behavioral Verification

Verify that flows produce the expected output for known inputs. Uses synthetic I/O pairs from `docs/flows/{flow-name}/test-data/`.

### Skip Conditions

Skip Phase 4 (with explicit message) when:
- **Phase 3 failed** (app is not running): "Skipping behavioral verification — app did not start."
- **No test data exists** for any flow (no `docs/flows/*/test-data/` directories): "No test data found in `docs/flows/*/test-data/`. Generate with `flow-test-data.md` or provide manually."
- **`camel` CLI is not available** (`camel --version` fails): "Skipping behavioral verification — `camel` CLI not available."

### Lazy Test Data Generation

If no test data directory exists but TDD files are available:
1. Load `skills/shared/flow-test-data.md`
2. For each flow with a TDD (`docs/flows/{flow-name}/{flow-name}.tdd.md`), generate test data
3. Proceed with the generated test data

### Steps (per flow with test data)

1. **Discover test cases:** List all `{NN}-*-input.{json|xml}` files in `docs/flows/{flow-name}/test-data/`
2. **Read the TDD:** Get the source endpoint URI from TDD Section 2
3. **Read ignore-fields:** If `test-data/ignore-fields.txt` exists, parse it (one field path per line)
4. **For each test case** (ordered by number):
   a. Read the input file: `docs/flows/{flow-name}/test-data/{NN}-{description}-input.{json|xml}`
   b. Read the expected output file: `docs/flows/{flow-name}/test-data/{NN}-{description}-expected-output.{json|xml}`
   c. Send the input to the running application:
      ```
      camel cmd send --endpoint={source-endpoint-uri} --body=@docs/flows/{flow-name}/test-data/{NN}-{description}-input.{json|xml}
      ```
   d. Capture the actual output from the sink (see Sink Reading table below)
   e. Compare actual vs expected using semantic comparison
   f. Report field-by-field results

### Sink Reading

Read the actual output from the sink using the appropriate method:

| Sink Component | How to Read Output |
|---|---|
| HTTP (synchronous response) | Response body returned directly from `camel cmd send` |
| Kafka | `camel cmd receive --endpoint=kafka:{topic}` or `docker exec kafka kafka-console-consumer` |
| JMS/AMQP | Consume from queue via broker CLI in Docker container |
| File/FTP | Read the output file from the target directory |
| Database (JDBC/JPA) | Query the target table for the inserted/updated record |
| Log/Mock | Parse the expected content from application log output |
| `direct:`/`seda:` (internal) | Follow the downstream route chain to the actual external sink |

### Semantic Comparison

Compare actual output against expected output **semantically**, not byte-for-byte:

- **JSON:** Parse both as maps/lists. Compare field-by-field. Ignore key ordering. Ignore insignificant whitespace.
- **XML:** Parse both as DOM trees. Compare element-by-element. Ignore insignificant whitespace. Ignore namespace prefix differences if the namespace URI matches.

**Dynamic fields:** Fields listed in `test-data/ignore-fields.txt` are reported as "ignored" rather than matched/mismatched. These are fields that change every run (timestamps, UUIDs, generated correlation IDs).

**Field-level report per test case:**

```
Flow: {flow-name}
Test: {NN}-{description}
  ✅ {field}: "{expected}" == "{actual}"
  ⚠️ {field}: expected "{expected}", got "{actual}"
  ⏭️ {field}: ignored (dynamic field)
Result: {N} mismatches, {N} matched, {N} ignored
```

### Iteration Loop (Phase 4)

When mismatches are found, classify and route the fix:

| Mismatch Type | Fix Target | Action |
|---|---|---|
| Value-level mismatch in DataMapper field | camel-implement | Fix the Groovy/XSLT transformation expression |
| Missing field in output | camel-implement | Check TDD field mappings, re-run DataMapper validation |
| Extra unexpected field in output | camel-implement | Check route processing steps, remove extra mapping |
| Type mismatch (string vs number) | camel-implement | Fix type conversion in transformation |

```
iteration_count = 0
previous_mismatches = null

while iteration_count < 15:
    1. For each test case with mismatches:
       a. Classify the mismatch type using the table above
       b. Route the fix to camel-implement
       c. Apply the fix (re-generate transformation, fix expression, etc.)
    
    2. Stop the running application
    3. Restart the application (Phase 3 start command)
    4. Wait for success pattern (up to 60 seconds)
    5. Re-send the failing test cases via `camel cmd send`
    6. Re-compare actual vs expected output
    
    7. If all test cases now pass → break (proceed to Phase 5)
    
    8. current_mismatches = extract mismatches from comparison
    9. If current_mismatches == previous_mismatches:
       → Short-circuit: "Fix did not resolve the behavioral mismatch."
       → Escalate to user and stop Phase 4
    
    10. previous_mismatches = current_mismatches
    11. iteration_count += 1

if iteration_count >= 15:
    → "Iteration limit reached. 15 fixes attempted without resolving behavioral mismatches."
    → Escalate to user
```

Phase 4 has its own independent iteration counter (max 15), separate from Phase 2 and Phase 3.

---

## Phase 5: Report

Generate a structured verification report summarizing all phases.

### Report Template

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
VERIFICATION REPORT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Runtime:          {runtime}
Maven:            {✅ ./mvnw (wrapper) | ✅ mvn (system) | ❌ not found}

Phase 1 — Environment:  {✅ description | ⚠️ Skipped (reason) | ❌ FAILED (reason)}
Phase 2 — Build:        {✅ BUILD SUCCESS [(N fixes applied)] | ⚠️ Skipped (reason) | ❌ FAILED after N iterations}
Phase 3 — Startup:      {✅ Started in Ns, N routes active [(N fixes applied)] | ⚠️ Skipped (reason) | ❌ FAILED after N iterations}
Phase 4 — Behavioral:   {✅ N/N tests passed [(N fixes applied)] | ⚠️ Skipped (reason) | ❌ N/N tests failed}

{If any fixes were applied:}
Fixes applied:
  1. [{Phase}] {description of fix}
  2. [{Phase}] {description of fix}

{If any behavioral mismatches were found and fixed:}
  Flow: {flow-name}
  Test: {NN}-{description}
    ⚠️ {field}: expected "{expected}", got "{actual}"
    Fix applied: {description of fix}

{If any phases were skipped:}
Skipped checks:
  - {description} ({reason})

{If startup failed — show last error:}
Last error:
  {exception class}: {message}
  Classification: {category from error-taxonomy.md}
  Fix attempted: {what was tried}

  ⚠️ Escalated: {suggestion for manual resolution}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### Report Examples

**Full success with behavioral verification:**

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
VERIFICATION REPORT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Runtime:          Quarkus
Maven:            ✅ ./mvnw (wrapper)

Phase 1 — Environment:  ✅ docker-compose up (PostgreSQL, Kafka)
Phase 2 — Build:        ✅ BUILD SUCCESS (1 fix applied: added camel-quarkus-jdbc)
Phase 3 — Startup:      ✅ Started in 4.2s, 3 routes active
Phase 4 — Behavioral:   ✅ 5/5 tests passed (order-processing)
                         ⏭️ 1 dynamic field ignored (processedAt)

Fixes applied:
  1. [Build] Added camel-quarkus-jdbc to pom.xml (ClassNotFoundException)

Skipped checks:
  (none)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**Startup failure, degraded environment:**

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
VERIFICATION REPORT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Runtime:          Spring Boot
Maven:            ✅ ./mvnw (wrapper)

Phase 1 — Environment:  ⚠️ Skipped (Docker not available)
Phase 2 — Build:        ✅ BUILD SUCCESS
Phase 3 — Startup:      ❌ FAILED after 3 iterations
Phase 4 — Behavioral:   ⚠️ Skipped (app did not start)

Last error:
  FailedToCreateRouteException: Cannot find component 'activemq'
  Classification: Unknown component URI
  Fix attempted: Added camel-activemq-starter — error persists

  ⚠️ Escalated: Component 'activemq' may not be supported in this
  Camel version. Verify the component URI against the catalog manually.

Skipped checks:
  - External services (Docker not available) — PostgreSQL, ActiveMQ
    required by TDD but could not be started
  - Behavioral verification (app did not start)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**No tools available:**

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
VERIFICATION REPORT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Runtime:          Quarkus
Maven:            ❌ not found (no ./mvnw, no system mvn)

Phase 1 — Environment:  ⚠️ Skipped (Docker not available)
Phase 2 — Build:        ⚠️ Skipped (Maven not available)
Phase 3 — Startup:      ⚠️ Skipped (Maven not available)
Phase 4 — Behavioral:   ⚠️ Skipped (app did not start)

No verification could be performed.
Install Maven wrapper (./mvnw) or system Maven to enable verification.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## Fix Routing Summary

Quick reference for the verify loop. For full details on each error pattern, see `error-taxonomy.md`.

| Error Classification | Fix Target | Action |
|---|---|---|
| Missing Maven dependency | Self-repair | Add `<dependency>` to pom.xml (runtime-aware artifact naming) |
| Missing `application.properties` entry | Self-repair | Add property with placeholder value |
| Missing `camel.jbang.dependencies` | Self-repair | Add to `application.properties` |
| Docker service not running | Self-repair | `docker compose up -d` |
| Docker service unhealthy | Self-repair | `docker compose restart {service}` |
| Version incompatibility | Self-repair | Check BOM version, align dependencies |
| Wrong component options | camel-validate | Re-validate against MCP catalog |
| Constitution violation at runtime | camel-validate | Re-run constitution compliance checks |
| YAML schema error at startup | camel-validate | Re-run YAML schema validation |
| Route YAML structurally broken | camel-implement | Re-generate route from TDD (affected flow only) |
| Wrong component URI | camel-implement | Re-check TDD, re-generate |
| Missing bean | camel-implement | Re-generate with correct annotations |
| XSLT/Groovy transformation error | camel-implement | Re-run DataMapper validation + re-generate |
| Expression evaluation failure | camel-implement | Re-generate expression from TDD |
| Behavioral: value-level mismatch | camel-implement | Fix the Groovy/XSLT transformation expression |
| Behavioral: missing field in output | camel-implement | Check TDD field mappings, re-run DataMapper validation |
| Behavioral: extra field in output | camel-implement | Check route processing steps, remove extra mapping |
| Behavioral: type mismatch | camel-implement | Fix type conversion in transformation |
| Same error after fix attempt | Escalate | "Fix did not resolve the error" |
| 15 iterations reached | Escalate | "Iteration limit reached" |
| Unclassified error | Escalate | Raw log output + suggestion |
| Required tool unavailable | Escalate | "Need {tool} but not available" |

---

## After the App Stops

After Phase 4 completes (or is skipped), stop the running application:

- **Quarkus/Spring Boot:** Send interrupt signal (Ctrl+C / kill the Maven process)
- **JBang:** Send interrupt signal (Ctrl+C / kill the JBang process)

Then proceed to Phase 5 (Report).
