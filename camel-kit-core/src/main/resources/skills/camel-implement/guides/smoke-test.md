# Smoke Test Guide

**MANDATORY — DO NOT SKIP.** Start the application and verify it boots. If it fails, fix the error and retry. Repeat until it starts cleanly or the attempt limit is reached.

**Context variables:** `FLOW_NAME`, `MODULE_DIR`, `RUNTIME`, `CAMEL_VERSION`.

---

## Step 1: Start External Services

If `docker-compose.yaml` exists in `MODULE_DIR`:

```bash
cd {MODULE_DIR} && docker compose up -d
```

Wait a few seconds for services to initialize before proceeding.

If no `docker-compose.yaml` exists, skip this step.

---

## Step 2: Smoke Test Loop

**Maximum 6 attempts.** For each attempt:

### 2.1 Run the Startup Command

Use a **timeout of 60 seconds**.

**JBang:**
```bash
cd {MODULE_DIR} && timeout 60 camel run {flow-name}.camel.yaml *.xsl application.properties 2>&1
```

**Spring Boot:**
```bash
cd {MODULE_DIR} && timeout 60 ./mvnw spring-boot:run 2>&1
```
If `./mvnw` is not present, use `mvn spring-boot:run`.

**Quarkus:**
```bash
cd {MODULE_DIR} && timeout 60 ./mvnw quarkus:dev -Dquarkus.analytics.disabled=true -Dquarkus.console.enabled=false 2>&1
```
If `./mvnw` is not present, use `mvn quarkus:dev`.

The flags `-Dquarkus.analytics.disabled=true -Dquarkus.console.enabled=false` prevent interactive prompts that would block the process.

### 2.2 Check for Success

**Success markers** (any of these in output means the application started):

| Runtime | Success Markers |
|---------|----------------|
| JBang | `Routes startup summary`, `routes started`, `Apache Camel (camel-jbang) started` |
| Spring Boot | `Started` followed by `in` and `seconds`, `routes started` |
| Quarkus | `Listening on:`, `installed features:`, `routes started` |

**If a success marker is found**, perform a secondary health check (Spring Boot / Quarkus only):

```bash
# Spring Boot (default actuator port)
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health

# Quarkus (default SmallRye Health port)
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/q/health/ready
```

- If HTTP 200 → **PASS.** Stop the application and go to Step 3.
- If health endpoint fails (connection refused, 404, 503) → still count as PASS if log markers were found. The health endpoint may not be configured. Note it in the report.
- **JBang:** No health endpoint available — log markers alone are sufficient.

Stop the application and go to Step 3.

### 2.3 If Startup Failed

Analyze the error output. Common issues:

| Error Pattern | Likely Cause | Fix |
|--------------|-------------|-----|
| `Cannot resolve dependencies` | Missing Maven dependency | Add to `pom.xml` |
| `ClassNotFoundException` | Missing dependency or wrong artifactId | Check dependency groupId/artifactId for runtime |
| `No such component` | Component not on classpath | Add component dependency |
| `Connection refused` | External service not running | Check docker-compose is up |
| `Property placeholder` | Missing property | Add to `application.properties` |
| `Invalid URI` | Malformed endpoint URI | Fix URI in route YAML |
| `BUILD FAILURE` | Compilation error | Check pom.xml, plugin versions |
| `bean with name ... not found` | Missing bean definition | Follow the Configuration Ladder in `skills/shared/forage.md` (rung 1 forage.* / rung 3 camel.beans.*) |
| `PropertyBindingException` / `No such property` | Invalid component-level key in `application.properties` (often version-gated) | Re-run `camel_configuration_validate` with `platformBom` (properties-generation.md §5.4) and fix from its suggestions |
| Startup error not matching any row above | Unknown | Call `camel_error_diagnose` with the full error output; apply its suggested fix |

**Before fixing:** Note the current state of the file being modified. If a fix introduces a NEW error that wasn't present before, revert that specific change and try a different approach.

**Fix the issue** in the relevant file (route YAML, `application.properties`, `pom.xml`), then report:

```
⚠️ Smoke test attempt [N]/6 failed.

Error: [one-line error summary]
Fix applied: [what was changed and in which file]

Retrying...
```

**Go back to Step 2.1** for the next attempt.

### 2.4 Attempt Limit Reached

If the application still fails after 6 attempts:

```
⚠️ SMOKE TEST FAILED: {flow-name}

The application failed to start after 6 attempts.

Errors encountered:
  1. [error] → [fix applied]
  2. [error] → [fix applied]
  ...

Files modified during fix attempts:
  - [file1]: [what changed]
  - [file2]: [what changed]

Manual investigation may be needed.
```

---

## Step 3: Report Result

**On success:**

```
✅ SMOKE TEST PASSED: {flow-name}

Application started successfully on {RUNTIME} runtime.
Attempts: [N]/6
```

**On failure:** Use the report from Step 2.4.

---

## Step 4: Stop External Services

After the smoke test completes (pass or fail):

```bash
cd {MODULE_DIR} && docker compose down
```

Skip if docker-compose was not started in Step 1.
