# Camel Error Classification Taxonomy

Pure reference guide. No procedural logic here — `verify-loop.md` drives the process and uses this guide to classify errors.

For each error encountered during verification, find the matching pattern below. The classification tells the verify loop what to do.

---

## How to Use This Guide

1. Capture the error message or stack trace from the build/startup output
2. Search through the sections below for a matching **Pattern**
3. Read the **Category**, **Fix target**, and **Fix action**
4. The verify loop applies the fix and retries

If no pattern matches → the error is **Unclassified** → escalate to the user with the raw log output.

---

## Entry Format

Each entry follows this structure:

- **Pattern:** regex or string to match in log output
- **Phase:** Build | Startup
- **Category:** the error family
- **Fix target:** Self-repair | camel-validate | camel-implement | camel-test | re-plan | Escalate
- **Fix action:** what to do

---

## Build Errors (Phase 2)

These errors appear during `./mvnw compile`.

### Missing Camel Component Dependency

**Pattern:** `java.lang.ClassNotFoundException: org.apache.camel.component.{name}` or `package org.apache.camel.component.{name} does not exist`
**Phase:** Build
**Category:** Missing dependency
**Fix target:** Self-repair
**Fix action:** Add the Camel component dependency to pom.xml. The artifact name depends on the runtime:

| Runtime | GroupId | ArtifactId |
|---|---|---|
| Quarkus | `org.apache.camel.quarkus` | `camel-quarkus-{name}` |
| Spring Boot | `org.apache.camel.springboot` | `camel-{name}-starter` |
| JBang | `org.apache.camel` | `camel-{name}` (add to `camel.jbang.dependencies` in `application.properties`) |

No `<version>` tag for Quarkus/Spring Boot — the BOM manages versions. For JBang, the version resolves via the `camel@apache/camel` alias.

### Missing Third-Party Dependency

**Pattern:** `cannot find symbol: class {ClassName}` where the class is NOT in `org.apache.camel` packages
**Phase:** Build
**Category:** Third-party dependency
**Fix target:** Self-repair
**Fix action:** Read the import statement for the missing class, determine the Maven coordinates (groupId:artifactId), and add to pom.xml `<dependencies>`. For JBang, add to `camel.jbang.dependencies` in `application.properties`.

### Version Incompatibility

**Pattern:** `java.lang.NoSuchMethodError` or `java.lang.AbstractMethodError`
**Phase:** Build
**Category:** Version incompatibility
**Fix target:** Self-repair
**Fix action:** Check the Camel BOM version in pom.xml. Ensure all Camel dependencies use the same version managed by the BOM. If a third-party dependency conflicts, check for a compatible version. Look for mixed artifact versions.

### Missing Maven Plugin

**Pattern:** `Unknown lifecycle phase` or `Could not find goal`
**Phase:** Build
**Category:** Build tool
**Fix target:** Escalate
**Fix action:** Maven plugin issues typically require manual intervention. Report the missing plugin and suggest checking pom.xml `<build><plugins>` section. Do NOT attempt to add plugins automatically — plugin configuration is too varied.

---

## Startup Errors (Phase 3)

These errors appear during `./mvnw quarkus:dev` or `./mvnw spring-boot:run` or `camel run`.

### Route Creation Failure

**Pattern:** `org.apache.camel.FailedToCreateRouteException`
**Phase:** Startup
**Category:** Route creation
**Fix target:** camel-implement
**Fix action:** The route YAML is structurally broken. Identify the affected flow from the route ID in the exception message (e.g., `Failed to create route order-processing-route`). Load the `camel-implement` skill and re-generate only the affected flow's route YAML from its TDD. Do NOT re-generate the entire project — only the broken route.

### Unknown Component

**Pattern:** `org.apache.camel.NoSuchEndpointException` or `Cannot find component '{name}'`
**Phase:** Startup
**Category:** Component/endpoint
**Fix target:** camel-implement
**Fix action:** The component URI in the route YAML references a component that Camel cannot find. Possible causes:
1. Component name is misspelled in the YAML
2. Component dependency is missing from pom.xml (check Phase 2 first)
3. Component does not exist in this Camel version

Re-check TDD Section 2/4 for the correct component name. Verify against MCP catalog via `camel-validate`. Re-generate the route if the component name is wrong.

### Wrong Endpoint Options

**Pattern:** `org.apache.camel.ResolveEndpointFailedException`
**Phase:** Startup
**Category:** Component/endpoint
**Fix target:** camel-validate
**Fix action:** The component endpoint options are invalid for this Camel version. Load `camel-validate` and re-validate the component options against the MCP catalog (`camel_catalog_component`). Common cause: option names changed between Camel versions.

### Missing Bean

**Pattern:** `org.apache.camel.NoSuchBeanException`
**Phase:** Startup
**Category:** Bean/injection
**Fix target:** camel-implement
**Fix action:** A bean referenced in the route YAML does not exist in the application context. Re-generate the bean class with the correct annotation:
- **Spring Boot:** `@Component` (for auto-discovery) or `@Bean` method in a `@Configuration` class
- **Quarkus:** `@ApplicationScoped` + `@Named("{beanName}")`

### Spring/CDI Injection Failure

**Pattern:** `UnsatisfiedDependencyException` or `AmbiguousResolutionException`
**Phase:** Startup
**Category:** Bean/injection
**Fix target:** camel-implement
**Fix action:** Dependency injection wiring is wrong. For Spring Boot, this often means a missing `*-starter` dependency (the auto-configuration class isn't on the classpath). For Quarkus, check CDI scope annotations. Re-generate the bean with correct annotations and verify the dependency is in pom.xml.

### External Service Connection

**Pattern:** `Connection refused` or `java.net.ConnectException` or `ConnectionException`
**Phase:** Startup
**Category:** External service
**Fix target:** Self-repair
**Fix action:** An external service (database, message broker, etc.) is not reachable at the configured host:port.

1. If Docker is available and docker-compose.yaml exists → `docker compose restart {service}`
2. If Docker is available but no docker-compose.yaml → load `camel-implement/guides/docker-compose.md` to generate one, then `docker compose up -d`
3. If Docker is not available → report which service and port are unreachable. Suggest the user start the service manually.

Identify the service from the port number or connection URL in the error message.

### Quarkus Build-Time Augmentation Error

**Pattern:** `io.quarkus.builder.BuildException`
**Phase:** Startup (Quarkus only — appears during `quarkus:dev` augmentation phase)
**Category:** Build tool
**Fix target:** Escalate
**Fix action:** Quarkus augmentation errors are complex and varied. Report the full error to the user. Common causes include:
- Missing Quarkus extension for a Camel component (e.g., need `camel-quarkus-{name}` not just `camel-{name}`)
- Conflicting extensions
- CDI validation errors

---

## Runtime Errors (Phase 3 — during log capture)

These errors appear after the application starts but during the log capture window (message processing failures).

### Expression Evaluation Failure

**Pattern:** `org.apache.camel.language.ExpressionEvaluationException` or `groovy.lang.GroovyRuntimeException` or `javax.script.ScriptException`
**Phase:** Startup (runtime)
**Category:** Expression/language
**Fix target:** camel-implement
**Fix action:** An inline expression (Groovy, Simple, XPath) failed during message processing. This is common with DataMapper Groovy scripts.

For Groovy DataMapper errors: re-run `datamapper-validation.md` to verify the Groovy script against the TDD field mappings, then re-generate if validation fails.

For Simple or XPath expressions: check the expression syntax in the route YAML against the TDD processing steps.

### Type Conversion Error

**Pattern:** `org.apache.camel.TypeConversionException` or `org.apache.camel.InvalidPayloadException`
**Phase:** Startup (runtime)
**Category:** Type conversion
**Fix target:** camel-implement
**Fix action:** Data type mismatch between components in the route. Check TDD Section 2/4 for source/target formats and ensure the route has appropriate marshal/unmarshal steps. Common issue: JSON body arriving as `InputStream` when the next processor expects `String` — add a `convertBodyTo: String` step.

### XSLT Transformation Error

**Pattern:** `net.sf.saxon.trans.XPathException` or `javax.xml.transform.TransformerException`
**Phase:** Startup (runtime)
**Category:** Transformation
**Fix target:** camel-implement
**Fix action:** The XSLT stylesheet has an error. Re-run `datamapper-validation.md` to verify the XSLT against TDD field mappings. If validation finds issues, re-generate the XSLT using the appropriate approach guide (`datamapper-approach-a.md` or `datamapper-approach-b.md`).

---

## Test Errors (Phase 2)

These errors appear during `camel test run *.it.yaml`.

### Citrus Assertion Mismatch

**Pattern:** `CitrusRuntimeException` or `Failed validation` or `expected:<` followed by `> but was:<`
**Phase:** Test
**Category:** Assertion mismatch
**Fix target:** camel-implement
**Fix action:** The route produces incorrect output for the test input. Read the TDD field mappings and compare against the actual route transformation. Fix the route logic (Groovy script, XSLT, Simple expression), not the test. Re-check with `datamapper-validation.md` if the flow uses DataMapper.

### Citrus Test Timeout

**Pattern:** `ActionTimeoutException` or `Timeout waiting for message` or `timeout after`
**Phase:** Test
**Category:** Timeout
**Fix target:** Self-repair
**Fix action:** The test waited for a response message that never arrived. Possible causes:
1. Route is not processing (check startup logs within Citrus test output)
2. Endpoint configuration mismatch between test YAML and route YAML (different topic name, queue name, etc.)
3. Timeout value too low for the processing pipeline

Increase timeout first. If still failing after timeout increase, check route endpoint URIs match the test endpoint URIs — this becomes a camel-implement fix target.

### Testcontainer Launch Failure

**Pattern:** `ContainerLaunchException` or `Could not start container` or `container exited`
**Phase:** Test
**Category:** Container startup
**Fix target:** Self-repair
**Fix action:** A Testcontainer failed to start within the Citrus test. Possible causes:
1. Docker daemon not running → report and skip
2. Docker image not available or tag invalid → fix the image reference in the test YAML
3. Port conflict with an already-running container → stop conflicting containers

Check Docker status first. If Docker is running, inspect the container name and image in the test YAML. Fix the image reference or version tag.

### Test YAML Parse Error

**Pattern:** `Failed to parse test file` or `Invalid test action` or `Unknown action type` or YAML syntax error in test file
**Phase:** Test
**Category:** Test syntax
**Fix target:** camel-test
**Fix action:** The Citrus test YAML has a syntax or schema error. The test itself is malformed — not the route. Re-generate the test using `camel-test/guides/test-generation.md`. Common causes: wrong YAML indentation, unknown action name, incorrect endpoint format, variable syntax error.

### Test Logic Error

**Pattern:** Test fails but manual inspection of route output shows correct behavior. The test assertion itself expects the wrong value.
**Phase:** Test
**Category:** Test logic
**Fix target:** camel-test
**Fix action:** The test expectations are wrong, not the route. This happens when the TDD was modified after the test was generated, or when the test-data expectations in `docs/flows/{flow-name}/test-data/` are incorrect. Re-read the TDD field mappings, regenerate the synthetic I/O pairs via `shared/flow-test-data.md`, then regenerate the test from the updated TDD.

---

## Re-Plan Promotion Rules

When fix attempts fail to resolve an error, it may indicate an architectural problem requiring TDD changes rather than code fixes.

### Tier 1: Immediate Promotion

After 1 failed fix attempt, query the MCP catalog to check if the failure is structural:
- Component does not exist for this runtime/version → `re-plan`
- Required EIP pattern not available in this Camel version → `re-plan`
- Incompatible component combination confirmed → `re-plan`

**Detection:** After the first fix attempt fails, call `camel_catalog_component(name={component}, runtime={runtime}, platformBom={bom})`. If MCP returns no result, the failure is structural. Route to `re-plan` immediately — do not consume additional fix attempts.

### Tier 2: Progressive Promotion

After 3 failed fix attempts on the same error class (each with a different fix strategy):
- Build error with same root cause after 3 dependency changes → `re-plan`
- Startup error with same exception after 3 route modifications → `re-plan`
- Test error with same assertion failure after 3 transformation fixes → `re-plan`

**Detection:** Track the error class (the category from this taxonomy) and the fix attempts count per class. When attempt count for a class reaches 3, route to `re-plan`.

### Short-Circuit Rule

If the error after a fix attempt is the exact same pattern and message as before the fix, short-circuit immediately:
- If this is a Tier 1 candidate (component/dependency error) → verify with MCP, then `re-plan`
- If this is a Tier 2 candidate → increment attempt count, trigger `re-plan` if count >= 3

### Fix Target: re-plan

**Fix target:** re-plan
**Fix action:** Load `camel-execute/guides/re-plan-loop.md`. Pass the failure details, affected TDD file(s), error output, and MCP catalog response (if applicable). The re-plan loop modifies the affected TDD sections and re-executes. Maximum 3 re-plan rounds.

---

## Runtime-Specific Notes

### Quarkus

- **Dev mode command:** `./mvnw quarkus:dev -Dquarkus.console.enabled=false` (disables the interactive terminal that breaks log capture)
- **Success pattern:** `Listening on: http://0.0.0.0:8080`
- **Build-time augmentation:** errors surface as `io.quarkus.builder.BuildException` — treat as a startup error, not build error
- **CDI injection:** uses `@ApplicationScoped` + `@Named`, not Spring's `@Component`
- **Component dependencies:** always `camel-quarkus-{name}` (not `camel-{name}`)

### Spring Boot

- **Dev mode command:** `./mvnw spring-boot:run`
- **Success pattern:** `Started {AppName} in {N} seconds`
- **Auto-configuration:** `UnsatisfiedDependencyException` often means a missing `*-starter` dependency (the auto-configuration class is not on the classpath)
- **Bean injection:** uses `@Component` + `@Autowired`
- **Component dependencies:** always `camel-{name}-starter` (not `camel-{name}`)

### JBang

- **Run command:** `camel run *.camel.yaml *.xsl application.properties`
- **No build step:** JBang compiles at runtime — Phase 2 (build) is **skipped entirely** for JBang
- **Dependencies:** declared in `application.properties` as `camel.jbang.dependencies=org.apache.camel:camel-{name}` (comma-separated for multiple)
- **Auto-discovery limitation:** JBang auto-discovers components from `to:` URIs but NOT from inline language expressions in `transform:` blocks. Groovy language requires explicit `camel.jbang.dependencies=org.apache.camel:camel-groovy`
- **Success pattern:** `Routes startup` or `Total X routes started`
- **Component dependencies:** always `org.apache.camel:camel-{name}` (no starter, no quarkus prefix)
