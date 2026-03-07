# Properties Generation Guide

This guide generates `application.properties`.

**Context variables:** `FLOW_NAME`, `PROPS_DIR`, `CAMEL_VERSION`, `RUNTIME` (jbang | springboot | quarkus).

---

## 5.1 Component-Level Configuration

**CRITICAL -- component name in property keys.** The `<component>` in `camel.component.<component>.<property>` MUST be the **exact URI scheme** from the route (the same name verified via `camel_catalog_component_doc` in Step 2). For example, if the route uses `smtp://...`, the properties MUST use `camel.component.smtp.*` -- never a parent or meta component like `camel.component.mail.*`.

**CRITICAL -- verify every property name against the catalog.** Before writing any `camel.component.<component>.<property>`, confirm that `<property>` exists in the component options returned by `camel_catalog_component_doc` in Step 2. Do NOT invent property names -- only use options that the catalog lists for that component. If a needed configuration is not available as a component option (e.g., server port for `platform-http`), check whether it requires a different property prefix (see platform-http rule below).

**Platform-HTTP port configuration.** The `platform-http` component has NO `port` component option. To change the HTTP listener port, use the Camel server properties instead:

```properties
camel.server.enabled=true
camel.server.port=8081
```

Never write `camel.component.platform-http.port=...` -- it does not exist.

## Properties Template

Based on components used and their catalog documentation, generate component configuration:

```properties
# ============================================
# Application Properties for {FLOW_NAME}
# Generated from TDD
# ============================================

# --------------------------------------------
# COMPONENT CONFIGURATION
# Syntax: camel.component.<component>.<property>=<value>
# <component> = exact URI scheme from the route (verified in Step 2)
# --------------------------------------------

# [Source Component] Configuration (scheme: [exact-scheme-from-route])
camel.component.[exact-scheme-from-route].[property]=[value from TDD]

# [Sink Component] Configuration (scheme: [exact-scheme-from-route])
camel.component.[exact-scheme-from-route].[property]=[value from TDD]

# --------------------------------------------
# BEAN DEFINITIONS
# Syntax: camel.beans.<beanName>=#class:<ClassName>
# --------------------------------------------

# DataSource Bean (if SQL component used)
camel.beans.dataSource=#class:org.apache.commons.dbcp2.BasicDataSource
camel.beans.dataSource.driverClassName=[driver from TDD]
camel.beans.dataSource.url=[jdbc url from TDD]
camel.beans.dataSource.username=[username]
camel.beans.dataSource.password=[password]

# --------------------------------------------
# ROUTE PLACEHOLDERS
# Used in route URIs as {{property.name}}
# --------------------------------------------

# Endpoints from TDD
source.endpoint=[value from TDD]
sink.endpoint=[value from TDD]
dlq.endpoint=[value from TDD]

# Error handling configuration from TDD "Error Handling" section
error.max.retries=[value from TDD]
error.retry.delay=[value from TDD]
error.backoff.multiplier=[value from TDD]

# Other placeholders from route
[other {{placeholders}} from generated YAML]

# --------------------------------------------
# JBANG DEPENDENCIES
# External libraries (NOT Camel components)
# --------------------------------------------

camel.jbang.dependencies=[dependencies from TDD "Dependencies" section]
```

**Runtime-specific note:** If `RUNTIME` is `jbang`, include the `camel.jbang.dependencies` section. If `RUNTIME` is `springboot` or `quarkus`, omit it (dependencies are managed in `pom.xml`).

## 5.2 Environment-Specific Properties

If the TDD "Configuration Properties" section defines environment-specific configuration, create templates:

```properties
# Create:
# - application.properties (defaults)
# - application-dev.properties
# - application-test.properties
# - application-prod.properties
```

**File location:** Use `PROPS_DIR` context variable for file location (no hardcoded paths).
