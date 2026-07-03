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

**CRITICAL -- verify every property name against the catalog.** Before writing any `camel.component.<component>.<property>`, confirm that `<property>` exists in the component options returned by `camel_catalog_component_doc` in Step 2. Do NOT invent property names -- only use options that the catalog lists for that component. If a needed configuration is not available as a component option (e.g., server port for `platform-http`), check whether it requires a different property prefix (see platform-http rule below).

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
