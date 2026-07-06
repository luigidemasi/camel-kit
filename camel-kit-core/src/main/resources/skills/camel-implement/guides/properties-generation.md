# Properties Generation Guide

This guide generates `application.properties`.

**Context variables:** `FLOW_NAME`, `PROPS_DIR`, `CAMEL_VERSION`, `RUNTIME` (main | spring-boot | quarkus).

## Graph Context (when available)

If `PROJECT_CONTEXT` was populated by Step 0 (graph-project-context):
- **Property naming:** Match `PROJECT_CONTEXT.PROPERTY_CONVENTIONS`. If the project uses `kafka.topic.input` (singular), generate the same pattern — not `kafka.topics.input` (plural).
- **Bean reuse:** Check `PROJECT_CONTEXT.EXISTING_BEANS` before generating `#class:` bean definitions. If a DataSource bean already exists, reference it by name (e.g., `camel.component.sql.dataSource=#existingDataSource`) instead of creating a new one.

If `PROJECT_CONTEXT` is not available, proceed with the template below as-is.

---

## 5.1 Component-Level Configuration

**CRITICAL -- component name in property keys.** The `<component>` in `camel.component.<component>.<property>` MUST be the **exact URI scheme** from the route (the same name verified via `camel_catalog_component_doc` in Step 2). For example, if the route uses `smtp://...`, the properties MUST use `camel.component.smtp.*` -- never a parent or meta component like `camel.component.mail.*`.

**CRITICAL -- verify every property name against the catalog.** Before writing any `camel.component.<component>.<property>`, confirm that `<property>` exists in the component options returned by `camel_catalog_component_doc` in Step 2. Do NOT invent property names -- only use options that the catalog lists for that component. **When a wanted configuration is NOT a component-level option in the project's Camel version, do NOT emit it.** Determine which case applies:

- **(a) Endpoint-level option** (catalog lists it under endpoint options, `kind=parameter`) → move it to the route URI `parameters:` block, never to `camel.component.*`.
- **(b) Runtime server configuration** (HTTP listener host/port and similar) → use the runtime's server properties (`camel.server.*` for main, `server.port` for spring-boot, `quarkus.http.port` for quarkus — see the platform-http rule below for the worked example).
- **(c) Requires an object the component wires via an option** (`connectionFactory`, `dataSource`, …) → define the object as a bean and reference it (`camel.component.<c>.<option>=#beanName`).
- **(d) Version-gated** (the option exists in a newer Camel version's catalog but not this project's) → report the minimum required version and use an alternative from (a)–(c).

If none of the cases resolves it, STOP and ask the user — never guess a property name or prefix.

**Platform-HTTP port configuration.** The `platform-http` component has NO `host` or `port` component options. To change the HTTP listener port, use the runtime's HTTP server properties instead:

```properties
# main runtime
camel.server.enabled=true
camel.server.port=8081

# spring-boot runtime
server.port=8081

# quarkus runtime
quarkus.http.port=8081
```

Never write `camel.component.platform-http.host=...` or `camel.component.platform-http.port=...` -- those options do not exist.

**Placeholder syntax INSIDE properties files (per runtime).** When one property value references another property:

| Runtime | Reference syntax inside `application.properties` |
|---|---|
| main / JBang | `{{key}}` — camel-main resolves `{{}}`; `${key}` is NOT resolved and passes through literally |
| spring-boot | `${key}` (Spring property interpolation) |
| quarkus | `${key}` (MicroProfile Config) |

For `RUNTIME=main`, never write `camel.component.x.y=${other.prop}` — write `camel.component.x.y={{other.prop}}`. (Route-URI rules for `${}` Simple vs `{{}}` placeholders are separate — see yaml-catalog-rules Rule 0f.)

## Properties Template

Based on components used and their catalog documentation, generate component configuration:

```properties
# ============================================
# Application Properties for {FLOW_NAME}
# Generated from design spec
# ============================================

# --------------------------------------------
# COMPONENT CONFIGURATION
# Syntax: camel.component.<component>.<property>=<value>
# <component> = exact URI scheme from the route (verified in Step 2)
# --------------------------------------------

# [Source Component] Configuration (scheme: [exact-scheme-from-route])
camel.component.[exact-scheme-from-route].[property]=[value from design spec]

# [Sink Component] Configuration (scheme: [exact-scheme-from-route])
camel.component.[exact-scheme-from-route].[property]=[value from design spec]

# --------------------------------------------
# BEAN DEFINITIONS
# Syntax: camel.beans.<beanName>=#class:<ClassName>
# --------------------------------------------

# DataSource Bean (if SQL component used)
camel.beans.dataSource=#class:org.apache.commons.dbcp2.BasicDataSource
camel.beans.dataSource.driverClassName=[driver from design spec]
camel.beans.dataSource.url=[jdbc url from design spec]
camel.beans.dataSource.username=[username]
camel.beans.dataSource.password=[password]

# --------------------------------------------
# ROUTE PLACEHOLDERS
# Used in route URIs as {{property.name}}
# --------------------------------------------

# Endpoints from design spec
source.endpoint=[value from design spec]
sink.endpoint=[value from design spec]
dlq.endpoint=[value from design spec]

# Error handling configuration from design spec Error Handling section
error.max.retries=[value from design spec]
error.retry.delay=[value from design spec]
error.backoff.multiplier=[value from design spec]

# Other placeholders from route
[other {{placeholders}} from generated YAML]

# --------------------------------------------
# MAIN RUNTIME DEPENDENCIES
# External libraries (NOT Camel components)
# --------------------------------------------

camel.jbang.dependencies=[dependencies from design spec Dependencies section]
```

**Runtime-specific note:** If `RUNTIME` is `main`, include the `camel.jbang.dependencies` section. If `RUNTIME` is
`spring-boot` or `quarkus`, omit it (dependencies are managed in `pom.xml`).

## 5.2 Environment-Specific Properties

If the design spec Configuration Properties section defines environment-specific configuration, create templates:

```properties
# Create:
# - application.properties (defaults)
# - application-dev.properties
# - application-test.properties
# - application-prod.properties
```

**File location:** Use `PROPS_DIR` context variable for file location (no hardcoded paths).

## 5.4 Properties Validation Gate (MANDATORY)

After writing `application.properties` (and any `application-<env>.properties`), validate every file with the MCP tool before declaring this step complete. Do NOT rely on the Step 2 catalog reading alone — this gate is a fresh, mechanical check.

1. Read the generated properties file content.
2. Remove `camel.beans.*` lines from the text to submit (bean instances are not catalog options; the tool cannot judge them — they are covered by the bean rules in this guide).
3. Call `camel_configuration_validate` with:
   - `properties`: the remaining file content (multi-line)
   - `runtime`: {RUNTIME}
   - `platformBom`: the full GAV derived from the versions in `.camel-kit/config.properties` per `shared/mcp-setup.md` (never a bare version number)
4. Check the `camelVersion` echoed in the result matches the project version; on mismatch, re-call with an explicit `platformBom`.
5. For every line reported invalid (issue kinds: `unknown`, `unknownComponent`, `syntax`, `invalidEnum`, `invalidBoolean`, `invalidInteger`, `invalidNumber`, `invalidDuration`, `required`, `deprecated`): fix the key or value using the tool's `suggestions`, then re-run the validation. Maximum 3 fix attempts.
6. If lines still fail after 3 attempts: STOP and report the failing lines with the tool output. Never silently keep an invalid key.
7. If the tool is unavailable (not found / network error): manually cross-check every `camel.component.*` key against the Step 2 `camel_catalog_component_doc` component-options list and add the note `"properties validated manually — MCP unavailable"` to your report.

The tool call and its result must be visible in your work (Iron Law 1 evidence requirement).
