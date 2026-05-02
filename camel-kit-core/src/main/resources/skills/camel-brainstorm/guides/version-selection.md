# Version Selection Guide

> **Context:** Loaded by `camel-brainstorm` after the interview/discovery phase.
> **Purpose:** Select the runtime platform and Apache Camel version.
> **Output:** `project.runtime`, `project.camelVersion`, `project.platformBomVersion` written to `.camel-kit/config.properties`.

---

## Step 1: Select Runtime Platform

Ask the user:

```
Which runtime platform would you like to use?

a) Camel JBang (lightweight) — recommended for prototyping and YAML-only projects
b) Spring Boot (Maven layout) — recommended for production Spring ecosystem
c) Quarkus (Maven layout) — recommended for cloud-native, fast startup
```

Record: `project.runtime` (`main`, `spring-boot`, or `quarkus`)

---

## Step 2: Select Camel Version

Present the supported versions for the selected runtime.

### If runtime is `main` (JBang)

| Version | Status |
|---------|--------|
| {CAMEL_MAIN_VERSION} | **recommended** (latest LTS) |

Other supported versions: {CAMEL_MAIN_SUPPORTED}

All listed versions are LTS (Long-Term Support) and receive patch updates.

Default: `{CAMEL_MAIN_VERSION}`

### If runtime is `spring-boot`

| Camel Version | Spring Boot BOM | Status |
|--------------|----------------|--------|
| {CAMEL_SPRINGBOOT_VERSION} | {SPRINGBOOT_BOM_VERSION} | **recommended** (latest LTS) |

Other supported versions: {CAMEL_SPRINGBOOT_SUPPORTED}

All listed versions are LTS (Long-Term Support) and receive patch updates.

For Spring Boot, the BOM version equals the Camel version.

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

## Step 3: Resolve Platform BOM Version

- **If runtime is `main`:** no platform BOM needed — JBang uses the Camel version directly
- **If runtime is `spring-boot`:** platform BOM version = Camel version (same release train)
- **If runtime is `quarkus`:** look up the Quarkus platform BOM from the mapping table above

---

## Step 4: Store in Config

Append the selected values to `.camel-kit/config.properties`:

```properties
project.runtime=quarkus
project.camelVersion={CAMEL_QUARKUS_VERSION}
project.platformBomVersion={QUARKUS_PLATFORM_VERSION}
```

These values are the single source of truth for all subsequent skills.

<HARD-RULE>
Do NOT guess or derive versions. Read `project.camelVersion` and `project.platformBomVersion` from `.camel-kit/config.properties` in all skills.

The runtime determines the available versions. Never present Quarkus users with versions that have no Quarkus support.
</HARD-RULE>
