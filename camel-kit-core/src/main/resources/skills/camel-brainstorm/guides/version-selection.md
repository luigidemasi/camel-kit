# Version Selection Guide

> **Context:** Loaded by `camel-brainstorm` after the interview/discovery phase.
> **Purpose:** Select the Red Hat Build of Apache Camel version for the project.
> **Output:** `project.camelVersion` (full Maven version with `.redhat-XXXXX` qualifier) and `project.runtime`.

---

## Iron Law 2: Red Hat Build Only

**ONLY Red Hat supported Camel versions are allowed.** Community-only versions (e.g., `4.18.0`, `4.12.0`) are FORBIDDEN.

---

## Step 1: Discover Available Versions

Fetch the directory listing from `https://maven.repository.redhat.com/ga/org/apache/camel/camel-bom/` to get the up-to-date list of Red Hat Build versions. Parse the version directories to build the supported versions list (only `4.x`). The highest base version is the recommended default.

**If the fetch fails** (network error, timeout), fall back to this static table:

| Base Version | Full Maven Version |
|-------------|-------------------|
| `4.14.4` | `4.14.4.redhat-00008` |
| `4.10.7` | `4.10.7.redhat-00009` |
| `4.8.5` | `4.8.5.redhat-00008` |
| `4.4.0` | `4.4.0.redhat-00046` |
| `4.0.0` | `4.0.0.redhat-00036` |

---

## Step 2: Present to User

```
Which Red Hat Build of Apache Camel version would you like to use?

Supported versions:
  [highest base version]  (recommended — latest)
  [next base version]
  ...

(Press Enter for recommended: [highest base version])
```

---

## Step 3: Reject Non-Supported Versions

If the user specifies a version NOT in the discovered or fallback list:

```
Version [version] is not supported by Red Hat Build of Apache Camel.

Only these versions are supported:
  [list from discovery or fallback]

Please select a supported version.
```

Do NOT proceed with a non-supported version. Ask again until a supported version is selected.

---

## Step 4: Select Runtime

If not already determined during interview/discovery:

```
Which runtime would you like to use?

a) Camel JBang (lightweight) — recommended for prototyping and YAML-only projects
b) Spring Boot (Maven layout) — recommended for production Spring ecosystem
c) Quarkus (Maven layout) — recommended for cloud-native, fast startup
```

Record: `project.runtime` (`main`, `spring-boot`, or `quarkus`)

---

## Step 5: Resolve Platform BOM Version

After selecting the Camel version and runtime, resolve the platform-specific BOM version. This is critical — the BOM version is NOT the same as the Camel version.

### If runtime is Quarkus

The Quarkus platform version uses the Quarkus version scheme (3.x), NOT the Camel version scheme (4.x).

**To discover:** Fetch the directory listing from:
`https://maven.repository.redhat.com/ga/com/redhat/quarkus/platform/quarkus-camel-bom/`

**Fallback mapping table** (if fetch fails):

| Camel Version | Quarkus Platform Version |
|--------------|--------------------------|
| `4.14.4.redhat-00008` | `3.27.2.redhat-00002` |
| `4.10.7.redhat-00009` | `3.20.0.redhat-00011` |
| `4.8.5.redhat-00008` | `3.15.0.redhat-00010` |
| `4.4.0.redhat-00046` | `3.8.0.redhat-00018` |
| `4.0.0.redhat-00036` | `3.2.0.redhat-00030` |

Record: `project.platformBomVersion` — the resolved Quarkus platform version (e.g., `3.27.2.redhat-00002`)

### If runtime is Spring Boot

The Spring Boot BOM version uses the same Camel base version but a different `.redhat-XXXXX` qualifier.

**To discover:** Fetch the directory listing from:
`https://maven.repository.redhat.com/ga/com/redhat/camel/springboot/platform/camel-spring-boot-bom/`

**Fallback mapping table** (if fetch fails):

| Camel Version | Spring Boot BOM Version |
|--------------|------------------------|
| `4.14.4.redhat-00008` | `4.14.4.redhat-00010` |
| `4.10.7.redhat-00009` | `4.10.7.redhat-00013` |
| `4.8.5.redhat-00008` | `4.8.5.redhat-00008` |
| `4.4.0.redhat-00046` | `4.4.0.redhat-00039` |
| `4.0.0.redhat-00036` | `4.0.0.redhat-00045` |

Record: `project.platformBomVersion` — the resolved Spring Boot BOM version (e.g., `4.14.4.redhat-00010`)

### If runtime is JBang

No platform BOM needed — JBang uses Camel version directly.

<HARD-RULE>
The platform BOM version MUST have a `.redhat-XXXXX` suffix. Community versions are FORBIDDEN (Iron Law 2).
</HARD-RULE>

---

## Step 6: Store Configuration

After selection, record:
- `project.camelVersion` — full Maven version with `.redhat-XXXXX` qualifier (e.g., `4.14.4.redhat-00008`)
- `project.runtime` — selected runtime
- `project.platformBomVersion` — resolved platform BOM version (e.g., `3.27.2.redhat-00002` for Quarkus)

These values will be written to `.camel-kit/config.yaml` during spec assembly.

For MCP calls, use the `platformBom` from `catalog/versions.properties` matching the selected version and runtime. See `shared/mcp-setup.md` for the version mapping table.

---

## Version Mapping for MCP Calls

Strip the `.redhat-XXXXX` suffix when needed for MCP catalog calls (community catalog uses base versions). The `platformBom` parameter handles this automatically — always prefer `platformBom` over `camelVersion` in MCP calls.
