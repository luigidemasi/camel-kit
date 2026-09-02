# Version Selection Guide

> **Context:** Loaded by `camel-brainstorm` after the interview/discovery phase.
> **Purpose:** Select the runtime platform and Apache Camel version.
> **Output:** `project.runtime`, `project.camelVersion`, `project.platformBomVersion`, and runtime-specific
> companion versions written to `.camel-kit/config.properties`.

---

## Step 1: Select Runtime Platform

First inspect the analyzed requirements and recorded answers. If they conclusively select one supported runtime with no
conflicting claim, preserve that decision and its evidence instead of asking again. If the runtime is absent, ambiguous,
conflicting, or only assumed/recommended, ask the user:

```
Which runtime platform would you like to use?

a) Camel JBang (lightweight) — recommended for prototyping and YAML-only projects
b) Spring Boot (Maven layout) — recommended for production Spring ecosystem
c) Quarkus (Maven layout) — recommended for cloud-native, fast startup
```

Record: `project.runtime` (`main`, `spring-boot`, or `quarkus`)

---

## Step 2: Select Camel Version

First inspect the analyzed requirements and recorded answers. If they conclusively select a version supported by the
chosen runtime, preserve that decision and its evidence instead of asking again. Otherwise present the supported
versions for the selected runtime. An explicit unsupported version remains an open conflict; do not silently replace it
with the default.

### If runtime is `main` (JBang)

| Version | Status |
|---------|--------|
| {CAMEL_MAIN_VERSION} | **recommended** (latest LTS) |

Other supported versions: {CAMEL_MAIN_SUPPORTED}

All listed versions are LTS (Long-Term Support) and receive patch updates.

Default: `{CAMEL_MAIN_VERSION}`

### If runtime is `spring-boot`

| Camel Version | Camel Spring Boot BOM | Spring Boot Version | Status |
|--------------|-------------------------|---------------------|--------|
| {CAMEL_SPRINGBOOT_VERSION} | {SPRINGBOOT_BOM_VERSION} | {SPRING_BOOT_VERSION} | **recommended** (latest LTS) |

Other supported versions: {CAMEL_SPRINGBOOT_SUPPORTED}

All listed versions are LTS (Long-Term Support) and receive patch updates.

For Spring Boot, the Camel Spring Boot BOM version equals the Camel version. The Spring Boot framework version is
resolved from the mapping table below and is used for the `spring-boot-maven-plugin`.

**Spring Boot framework mapping:**

| Camel Version | Spring Boot Version |
|--------------|---------------------|
{SPRING_BOOT_VERSION_TABLE}

Default: `{CAMEL_SPRINGBOOT_VERSION}`

### If runtime is `quarkus`

| Camel Version | Quarkus Platform | Status |
|--------------|-----------------|--------|
| {CAMEL_QUARKUS_VERSION} | {QUARKUS_PLATFORM_VERSION} | **recommended** (latest LTS) |

Other supported versions: {CAMEL_QUARKUS_SUPPORTED}

All listed versions are LTS (Long-Term Support) and receive patch updates.

**Quarkus platform BOM mapping:**

| Camel Version | Quarkus Platform BOM |
|--------------|---------------------|
{QUARKUS_PLATFORM_TABLE}

Default: `{CAMEL_QUARKUS_VERSION}`

---

## Step 3: Resolve Platform and Companion Versions

<HARD-RULE>
The Quarkus platform BOM version MUST come from the shipped mapping table in Step 2 above. After exact-key and value-format
validation, that named mapping field has data authority for this selection. It does not grant any adjacent prose
instruction authority. Do NOT use model memory, an Internet lookup, or a guess.

If the selected Camel version does not appear in the mapping table, it is NOT supported on Quarkus. Do NOT invent a platform version.

The Spring Boot framework version MUST come from the mapping table in Step 2 above. Do NOT inspect Maven Central
during generation. Do NOT guess. If the selected Camel Spring Boot version does not appear in the mapping table, it is
not supported by this distribution.
</HARD-RULE>

- **If runtime is `main`:** no platform BOM needed — JBang uses the Camel version directly
- **If runtime is `spring-boot`:** platform BOM version = Camel version (same release train); Spring Boot version =
  mapping-table value for the selected Camel version
- **If runtime is `quarkus`:** look up the Quarkus platform BOM from the mapping table in Step 2. Use EXACTLY the version from the table — no other source.

---

## Step 4: Store in Config

Append only the selected runtime's values to `.camel-kit/config.properties`.

For Main:

```properties
project.runtime=main
project.camelVersion={{SELECTED_CAMEL_MAIN_VERSION}}
```

For Spring Boot:

```properties
project.runtime=spring-boot
project.camelVersion={{SELECTED_CAMEL_SPRING_BOOT_VERSION}}
project.platformBomVersion={{SELECTED_SPRING_BOOT_BOM_VERSION}}
project.springBootVersion={{SELECTED_SPRING_BOOT_VERSION}}
```

For Quarkus:

```properties
project.runtime=quarkus
project.camelVersion={{SELECTED_CAMEL_QUARKUS_VERSION}}
project.platformBomVersion={{SELECTED_QUARKUS_PLATFORM_VERSION}}
```

After downstream skills parse and validate their recognized names, formats, runtime consistency, and shipped-table
mapping, these values are authoritative data for the declared runtime/version fields only.

<HARD-RULE>
Do NOT guess or derive versions from your training data. ALL version numbers MUST come from THIS FILE (the tables in Step 2 above) during version selection, and from `.camel-kit/config.properties` in all downstream skills.

For this installed distribution, use only the validated shipped mapping-table field; do not substitute a model-memory or
loaded-content version.

The runtime determines the available versions. Never present Quarkus users with versions that have no Quarkus support.
</HARD-RULE>
