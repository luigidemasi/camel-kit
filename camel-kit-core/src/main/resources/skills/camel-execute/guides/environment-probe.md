# Environment Probe

Pre-implementation environment check. Runs BEFORE dispatching any implementer subagents. Generates a throwaway skeleton project in a temp directory and verifies that planned dependencies, external services, and the target runtime are viable.

**Always load `camel-execute/guides/re-plan-loop.md` alongside this guide** — it handles architectural failures that the probe cannot fix mechanically.

---

## Purpose

Catch environment failures early — before implementation begins. A dependency that cannot resolve, a Docker image that does not exist, or a runtime that refuses to boot will waste every implementer cycle that follows. The probe surfaces these failures in seconds using a minimal skeleton, so the orchestrator can fix or re-plan before committing to full implementation.

---

## Step 1: Skeleton Generation

Generate the skeleton in a **temporary directory**. Never write probe files into the real project directory.

### Steps

1. Create a temp directory: `mktemp -d /tmp/camel-kit-probe-XXXXXX`
2. Read ALL TDD files (`docs/flows/{flow-name}/{flow-name}.tdd.md`) and extract:
   - **Section 2 (Source System):** component, protocol, connection properties
   - **Section 4 (Sink System):** component, protocol, connection properties
   - **Section 7 (Configuration Properties):** all connection strings and credentials
   - **Section 8 (Dependencies):** all Maven coordinates
3. Read `.camel-kit/config.properties` to determine the runtime (`quarkus`, `springboot`, `jbang`)
4. Generate the following files in the temp directory:

#### pom.xml

Include the runtime-specific BOM and ALL planned dependencies aggregated across all flows:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>{bom-groupId}</groupId>
            <artifactId>{bom-artifactId}</artifactId>
            <version>{bom-version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- All dependencies from TDD Section 8, de-duplicated -->
</dependencies>
```

Skip `pom.xml` generation for JBang runtime.

#### docker-compose.yaml

Include services for every external system found in TDD Section 2 and Section 4 (databases, message brokers, mail servers, etc.). Use the same image tags and port mappings that the real implementation would use.

Skip if no external services are needed.

#### Route YAML (probe-route.camel.yaml)

A single empty route — just enough to verify the runtime boots:

```yaml
- route:
    id: probe-route
    from:
      uri: timer:probe
    steps:
      - to:
          uri: log:probe
```

#### application.properties

Include connection strings from TDD Section 7. Add runtime-specific entries:

| Runtime | Additional Properties |
|---|---|
| Quarkus | `quarkus.analytics.disabled=true` |
| Spring Boot | (none) |
| JBang | `camel.jbang.dependencies=` with all planned dependencies |

#### Maven Wrapper

Copy `./mvnw` and `.mvn/` from the real project directory into the temp directory, if they exist.

---

## Step 2: Check 1 — Dependency Resolution

Verify that all planned dependencies can be resolved from configured repositories.

### Steps

1. **For Quarkus / Spring Boot:**
   - Run: `./mvnw dependency:resolve -q` (in the temp directory)
   - Check the command exit code (0 = success, non-zero = failure). On failure, capture stderr for Step 5 classification.

2. **For JBang:**
   - Skip this check — JBang resolves dependencies at runtime
   - Record: `SKIPPED (JBang)`

3. If dependency resolution fails → extract the error message and proceed to Step 5 (Error Classification)

---

## Step 3: Check 2 — Docker Services

Verify that all required external services can start.

### Steps

1. **If no external services are needed:**
   - Record: `PASS (no services required)`
   - Proceed to Step 4

2. **If Docker is not available** (`docker --version` fails):
   - Record: `SKIPPED (Docker not available)`
   - Proceed to Step 4

3. **If Docker is available and services are needed:**
   - Run: `docker compose -f {temp-dir}/docker-compose.yaml up -d`
   - Poll `docker compose -f {temp-dir}/docker-compose.yaml ps` every 5 seconds for up to 60 seconds
   - Check each service for `healthy` or `running` status
   - If all services reach healthy/running → record: `PASS (N services)`
   - If any service fails → extract the error and proceed to Step 5 (Error Classification)
   - Capture logs for failed services: `docker compose -f {temp-dir}/docker-compose.yaml logs {service} --tail=20`

---

## Step 4: Check 3 — Runtime Startup

Verify that the runtime can boot with the planned dependencies and configuration.

### Steps

1. Determine the startup command based on runtime:

   | Runtime | Command |
   |---|---|
   | Quarkus | `./mvnw quarkus:dev -Dquarkus.analytics.disabled=true -Dquarkus.console.enabled=false` |
   | Spring Boot | `./mvnw spring-boot:run` |
   | JBang | `camel run probe-route.camel.yaml application.properties` |

2. Start the skeleton application in the temp directory
3. Capture logs for up to 60 seconds, watching for:

   **Success patterns:**
   - Quarkus: `Listening on: http://`
   - Spring Boot: `Started` followed by a class name, then `in` followed by `seconds`
   - JBang: `Routes startup` or `Total` followed by `routes started`

   **Failure patterns:**
   - `FailedToCreateRouteException`, `NoSuchEndpointException`, `ResolveEndpointFailedException`
   - `NoSuchBeanException`, `UnsatisfiedDependencyException`
   - `BUILD FAILURE`
   - Java stack trace starting with `Exception in thread` or `Caused by:`
   - Process exit with non-zero code

4. If success pattern found → record: `PASS (Ns)` with the startup time
5. If failure pattern found → extract the error and proceed to Step 5 (Error Classification)
6. If 60 seconds elapse with no pattern → record: `FAIL (timeout)`
7. Stop the skeleton application after the check completes:
   - The runtime command must be started in the background (capture PID)
   - Logs captured by tailing the output for up to 60 seconds
   - After success/failure/timeout: send SIGINT to the process
   - Wait 5 seconds grace period
   - If still running, send SIGKILL
   - This ensures the process does not remain bound to ports

---

## Step 5: Error Classification

Classify each error found in Steps 2-4. Apply the "Assume Mechanical, Promote on Failure" rule.

### Classification Rule

All probe errors start classified as **MECHANICAL**. Only promote to **ARCHITECTURAL** after:

- MCP catalog confirms the component or extension does not exist, OR
- 1 failed mechanical fix attempt (the fix did not resolve the error)

### Classification Table

| Error | Default Class | MCP Check | Promotion to Architectural |
|---|---|---|---|
| `Could not find artifact` | Mechanical | `camel_catalog_component_doc(component, runtime, platformBom)` — exists? fix the name. Does not exist? promote. | If MCP says the component does not exist |
| `Could not resolve dependencies` (transitive conflict) | Mechanical | Check if both dependencies exist individually | If conflict persists after fix attempt |
| Docker `manifest unknown` | Mechanical | N/A | If the same image fails after 2 attempts |
| Docker `pull access denied` | Architectural | N/A | Immediately — private or licensed image |
| Port conflict (`address already in use`) | Mechanical | N/A | Never promotes (always fixable) |
| Runtime startup failure | Mechanical | Check that the extension or starter exists | If the extension does not exist |
| Version incompatibility (`NoSuchMethodError`, `AbstractMethodError`) | Mechanical | Check BOM alignment | If no compatible version exists |

### MCP Verification

For dependency and runtime errors, verify against the MCP catalog before attempting a fix:

```text
camel_catalog_component_doc(component="{component}", runtime="{runtime}", platformBom="{bom-gav}")
```

- If the component exists → the error is mechanical (wrong artifact name, wrong groupId, typo)
- If the component does not exist → promote to architectural (the TDD references a component that is not available for this runtime/version)

---

## Step 6: Mechanical Fix

Apply a targeted fix for the classified error, then re-run ONLY the check that failed.

### Fix Actions

| Error | Fix |
|---|---|
| Wrong artifact name | Look up the correct artifact via MCP catalog, update `pom.xml` |
| Missing dependency | Add the dependency to `pom.xml` (or `camel.jbang.dependencies` for JBang) |
| Port conflict | Add 10000 offset to the host port in `docker-compose.yaml` (e.g., 5432 becomes 15432, update `application.properties` accordingly) |
| Docker env var mismatch | Fix environment variables in `docker-compose.yaml` to match `application.properties` |
| Wrong BOM version | Align the BOM version with the Camel version from `.camel-kit/config.properties` |

### Re-run Rule

After applying a fix, re-run ONLY the check that failed:

- Dependency fix → re-run Step 2
- Docker fix → re-run Step 3
- Runtime fix → re-run Step 4

Do NOT re-run all checks. Do NOT re-run checks that already passed.

### Fix Limit

Allow at most **1 mechanical fix attempt per error**. If the fix does not resolve the error, promote the error to architectural and proceed to Step 7.

---

## Step 7: Architectural Failure — Trigger Re-Plan

When an error is classified as architectural (either immediately or after a failed mechanical fix), the probe cannot resolve it. The TDD must be revised.

### Steps

1. Load `camel-execute/guides/re-plan-loop.md`
2. Pass the following context to the re-plan loop:
   - **Failure details:** error message, classification, MCP catalog response
   - **Affected TDD file(s):** which TDD(s) reference the failing component or service
   - **Probe error output:** raw log output from the failed check
   - **MCP catalog response:** the full response from the catalog verification call

The re-plan loop will revise the affected TDD sections and re-run the probe.

---

## Step 8: Probe Report

Generate a structured report summarizing all checks.

### Report Template

```text
ENVIRONMENT PROBE
Dependency Resolution: {PASS | FAIL (N fixes) | SKIPPED (JBang)}
Docker Services:       {PASS (N services) | FAIL (service) | SKIPPED (no Docker / no services)}
Runtime Startup:       {PASS (Ns) | FAIL (error) | SKIPPED}

Fixes applied:
  1. [description]

Result: {PROCEED | RE-PLAN (architectural: reason) | ESCALATE (reason)}
```

### Result Interpretation

| Result | Meaning | Next Action |
|---|---|---|
| PROCEED | All checks passed (with or without mechanical fixes) | Continue to implementer dispatch |
| RE-PLAN | Architectural failure detected | Re-plan loop revises TDD, re-run probe |
| ESCALATE | Unresolvable failure (no Docker, no Maven, no JDK) | Report to user, cannot proceed automatically |

---

## Step 9: Cleanup

After the probe completes (regardless of result), clean up all probe artifacts.

### Steps

1. Stop any Docker services started during the probe:
   ```bash
   docker compose -f {temp-dir}/docker-compose.yaml down -v 2>/dev/null
   ```

2. Remove the temp directory:
   ```bash
   rm -rf {temp-dir}
   ```

3. The real implementation will generate proper project files in the actual project directory. The probe skeleton is throwaway — never preserve it.
