# Smoke Test Guide

Try to start the application and verify it boots successfully. If startup fails, analyze the error, fix the issue, and retry.

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

## Step 2: Start the Application

Run the startup command for the runtime. Use a **timeout of 60 seconds** — the goal is to check if the application boots, not to run it indefinitely.

### JBang

```bash
cd {MODULE_DIR} && timeout 60 camel run {flow-name}.camel.yaml application.properties 2>&1
```

**Success markers** (any of these in output):
- `Routes startup summary`
- `routes started`
- `Apache Camel (camel-jbang) started`

### Spring Boot

```bash
cd {MODULE_DIR} && timeout 60 ./mvnw spring-boot:run 2>&1
```

If `./mvnw` is not present, use `mvn spring-boot:run`.

**Success markers** (any of these in output):
- `Started` followed by `in` and `seconds`
- `routes started`

### Quarkus

```bash
cd {MODULE_DIR} && timeout 60 ./mvnw quarkus:dev -Dquarkus.analytics.disabled=true -Dquarkus.console.enabled=false 2>&1
```

If `./mvnw` is not present, use `mvn quarkus:dev`.

The flags `-Dquarkus.analytics.disabled=true -Dquarkus.console.enabled=false` prevent interactive prompts that would block the process.

**Success markers** (any of these in output):
- `Listening on:`
- `installed features:`
- `routes started`

---

## Step 3: Evaluate Result

### If startup succeeds (success marker found):

```
✅ SMOKE TEST PASSED: {flow-name}

Application started successfully on {RUNTIME} runtime.
```

Proceed to Implementation Summary.

### If startup fails (error in output or timeout with no success marker):

Analyze the error output and identify the root cause. Common issues:

| Error Pattern | Likely Cause | Fix |
|--------------|-------------|-----|
| `Cannot resolve dependencies` | Missing Maven dependency | Add to `pom.xml` |
| `ClassNotFoundException` | Missing dependency or wrong artifactId | Check dependency groupId/artifactId for runtime |
| `No such component` | Component not on classpath | Add component dependency |
| `Connection refused` | External service not running | Check docker-compose is up |
| `Property placeholder` | Missing property | Add to `application.properties` |
| `Invalid URI` | Malformed endpoint URI | Fix URI in route YAML |
| `BUILD FAILURE` | Compilation error | Check pom.xml, plugin versions |

**Fix the issue** in the relevant file (route YAML, `application.properties`, `pom.xml`), then **retry from Step 2**.

**Maximum 3 attempts.** If the application still fails after 3 tries:

```
⚠️ SMOKE TEST FAILED: {flow-name}

The application failed to start after 3 attempts.

Last error:
  [error summary]

Files modified during fix attempts:
  [list of files changed]

Manual investigation may be needed. Proceed to Implementation Summary.
```

---

## Step 4: Stop External Services

After the smoke test completes (pass or fail):

```bash
cd {MODULE_DIR} && docker compose down
```

Skip if docker-compose was not started in Step 1.
