---
name: camel-migrate
description: Migrate an existing integration from another product to Apache Camel
user-invocable: true
metadata:
  version: "2.0.0"
  author: "camel-kit"
  category: "migration"
  license: "Apache-2.0"
---

# Camel Migrate - Integration Migration Orchestrator

You are acting as a **Migration Specialist** that analyses existing integration artifacts from any vendor and orchestrates their migration to Apache Camel.

This skill is the generic entry point for all migrations. It detects the vendor, scans all available artifacts to build a pre-populated analysis summary, confirms the summary with the user, and then delegates to the appropriate vendor-specific migration guide — passing the confirmed summary so the guide never re-asks questions that have already been answered.

> **"Load" means READ and FOLLOW.** When this document says "Load `guides/xyz.md`", you MUST read that file from the `guides/` subdirectory next to this SKILL.md and execute its instructions. The guide files are always present — do NOT report them as missing.

## Parameters

```
/camel-migrate
```

No arguments. The command asks the user for the path interactively.

---

## Step 1 — Locate the Source Artifacts

Ask:

```
Please provide the path to your integration project. This can be:
- A project directory
- A single configuration file (e.g. mule-config.xml)
- A ZIP archive

Path:
```

Once the path is known, list all files found recursively. Note their types:
- XML configuration files
- Build files (`pom.xml`, `build.gradle`, `package.json`, etc.)
- Properties / config files (`*.properties`, `*.yaml`, `*.yml`, `*.env`)
- Documentation files (`*.md`, `*.txt`, `*.adoc`)
- Source files (`*.java`, `*.groovy`, `*.py`, `*.js`, etc.)
- Test files (`*Test*`, `*Spec*`, `test/**`, `src/test/**`)
- Container / deployment files (`Dockerfile`, `docker-compose*.yml`, `k8s/**`, `*.yaml` in deploy dirs)

---

## Step 2 — Scan All Artifacts

Read ALL available files before attempting vendor detection. The richer the picture from the artifacts, the more reliable and complete both vendor detection and the analysis summary will be.

For each file type, extract:

### Build files (`pom.xml`, `build.gradle`, `package.json`, etc.)
- Project `<name>` / `<description>` → business purpose candidate
- `<groupId>` / `<artifactId>` / dependencies → vendor signals + technology stack
- Min runtime version → compatibility constraint

### Descriptor files (`mule-artifact.json`, `spring-context.xml`, `ibm-mq.properties`, etc.)
- Platform-specific identifiers → primary vendor signal
- Application name and description → business purpose

### Documentation files (`README.md`, `docs/**`, `*.adoc`, `*.txt`)
- Business purpose, system description
- Owning team / contact
- SLA or performance targets
- Architecture overview

### Properties / config files (`*.properties`, `*.yaml`, `*.env`, `*.yml`)
- Endpoint URLs, queue/topic names → system landscape
- Retry counts, timeout values → SLA hints
- Property key names containing `GDPR`, `PCI`, `HIPAA`, `TLS`, `AUTH`, `SECRET` → compliance/security hints
- Platform-specific keys (e.g. `mule.home`, `spring.application.name`) → vendor signals

### XML / configuration files
- Root element namespaces → primary vendor signal
- Flow/route/pipeline definitions → integration topology

### Container / deployment files (`Dockerfile`, `docker-compose*.yml`, `k8s/**`)
- Presence of Kubernetes manifests → deployment target = Kubernetes
- Replica counts → scaling requirement
- Resource limits → performance profile
- Kubernetes Secrets references → sensitive config management

### Source files (`src/main/java/**`, `*.groovy`, etc.)
- Custom processor / transformer class names → business logic summary
- External service calls → integration points

### Test files (`src/test/**`, `*Test.java`, etc.)
- Test method names and scenarios → expected behaviour and edge cases
- Mock endpoints → system landscape confirmation

---
e
## Step 2b — Detect Project Layout

After scanning all artifacts, determine whether this is a **single-project** or **multi-project** layout.

**CRITICAL — Use `pom.xml` discovery to find ALL projects:**

Run a recursive search for ALL `pom.xml` (or `build.gradle`, `mule-artifact.json`) files under the source path. Projects can be nested multiple levels deep (e.g., `sssds/sdfsf/sfsfsfwe/pom.xml` is 3 levels deep). Do NOT rely on listing only the first level of subdirectories.

For each `pom.xml` found, check if it is a **leaf project** (has `src/` directory or route files) vs a **parent POM** (has `<modules>` element and no `src/`). Only leaf projects are independent applications to migrate.

**Single-project** — only one leaf `pom.xml` found:

```
workspace/
├── pom.xml                          # One build file
├── src/main/resources/
│   ├── camel/
│   │   ├── order-route.xml          # Multiple routes, but ONE app
│   │   └── notification-route.xml
│   └── application.properties
└── src/main/java/...
```

**Multi-project** — multiple leaf `pom.xml` files in different subdirectories:

```
workspace/
├── aaaaa/
│   ├── bbbbb/bberw/
│   │   ├── pom.xml                  # Leaf project (has src/)
│   │   └── src/main/resources/...
│   ├── sdadasdas/wer43rgdg/
│   │   ├── pom.xml                  # Leaf project (has src/)
│   │   └── src/main/resources/...
│   └── fsdfq/
│       ├── 5435fsdsac/
│       │   ├── pom.xml              # Leaf project (has src/)
│       │   └── src/main/resources/...
│       └── hlaskfdsdakl/
│           ├── pom.xml              # Leaf project — DO NOT MISS siblings!
│           └── src/main/resources/...
```

**Multi-project signals:**
- Multiple leaf `pom.xml` or `build.gradle` files in different subdirectories
- Multiple independent route/flow definition directories
- Multiple `mule-artifact.json` files in different subdirectories
- A parent directory containing multiple independent integration projects as subfolders

**Key distinction:** A single project with 5 routes in one `camel/` directory is still single-project. Multi-project means each sub-application has its own build file and can be deployed independently.

**If multi-project:** List ALL discovered leaf projects and build a source-to-target module mapping. Each source sub-project becomes a separate target module. The target module name should be derived from the flow name (kebab-case). Example:

```
Source projects detected (4 projects):
  aaaaa/bbbbb/  → target: https-jetty-consumer/
  wewrerewq/rwerwer/rewrwwr/             → target: migrated/wewrerewq-rwerwer-rewrwwr/
  reweior/twetq/qwewqwrwqr/              → target: migrated/reweior-twetq-qwewqwrwqr
  rewwqerk/dfask/sdafaaft/               → target: rewwqerk-dfask-sdafaaft/
```

**Verify completeness:** Cross-check the count of discovered projects against the recursive `pom.xml` search. If you found N `pom.xml` files but only listed M < N projects, you missed some. Go back and check.

Include this mapping in the analysis summary (Step 4) and pass it to the sub-skill. Each TDD produced by the sub-skill MUST include `Source Module` and `Target Module` fields in the "Overview" section so that `/camel-implement` places generated files in the correct sub-project directory.

**If single-project:** Set layout to `single-project`. The `Target Module` in the TDD can be omitted or set to `.` — files will be placed at the workspace root.

---

## Step 3 — Detect Vendor & Version

Using ALL the information collected in Step 2, identify the integration platform. The scan content — XML namespaces, build file groupIds, descriptor files, property key patterns, dependency names — gives far richer vendor signals than any single file.

### Supported Vendors

| Vendor | Key Signals |
|--------|-------------|
| **MuleSoft Mule** | XML namespace `mulesoft.org`, groupId `org.mule`/`com.mulesoft`, `mule-artifact.json`, deps starting with `mule-` |
| **Apache Camel 2.x/3.x** | groupId `org.apache.camel`, `camel-core`/`camel-spring`/`camel-blueprint` deps, XML namespace `camel.apache.org`, `RouteBuilder` classes |

**Determine version from collected content:**
- Mule 3.x: namespace `http://www.mulesoft.org/schema/mule/core/3.*` or connector version attributes < 4.0
- Mule 4.x: namespace without version path segment, or `<mule xmlns:ee=...` (EE 4.x)
- Camel 2.x: `<camel.version>2.*</camel.version>`, dependency `camel-core` version `2.*`, namespace `http://camel.apache.org/schema/spring` with `camelContext` element
- Camel 3.x: `<camel.version>3.*</camel.version>`, dependency `camel-core` version `3.*`
- Platform detection:
  - ServiceMix/Karaf: `camel-blueprint`, `karaf-maven-plugin`, `maven-bundle-plugin`, `<blueprint>` XML
  - Spring Boot: `camel-spring-boot-starter`
  - Spring XML: `camel-spring`, `<camelContext>` elements
  - Plain Java: `RouteBuilder` classes, no Spring/Blueprint deps

### Red Hat Product Detection

When the Camel version contains a `.redhat-*` or `.fuse-*` qualifier, identify the specific Red Hat product. The qualifier pattern and BOM artifacts encode the product and version:

| Signal | Product |
|--------|---------|
| `camel-core` version `2.12.x.redhat-6100XX` | Red Hat JBoss Fuse 6.1.0 |
| `camel-core` version `2.15.1.redhat-621XXX` | Red Hat JBoss Fuse 6.2.1 |
| `camel-core` version `2.17.0.redhat-630XXX` | Red Hat JBoss Fuse 6.3.0 |
| `camel-core` version `2.21.x.fuse-7XXXXX-redhat-XXXXX` | Red Hat Fuse 7.x (Karaf or Spring Boot) |
| `camel-core` version `2.23.x.fuse-7XXXXX-redhat-XXXXX` | Red Hat Fuse 7.x (Karaf or Spring Boot) |
| BOM `org.jboss.redhat-fuse:fuse-springboot-bom` | Red Hat Fuse 7.x on Spring Boot |
| BOM `org.jboss.redhat-fuse:fuse-karaf-bom` | Red Hat Fuse 7.x on Karaf |
| `camel-core` version `3.x.x.fuse-8XXXXX-redhat-XXXXX` | Red Hat Fuse Online / Camel Extensions |
| BOM `com.redhat.camel.springboot:camel-spring-boot-bom` | Red Hat Build of Apache Camel for Spring Boot |
| BOM with groupId `com.redhat.quarkus.platform` + camel artifacts | Red Hat Build of Apache Camel for Quarkus |
| `camel-core` version `3.x.x.redhat-XXXXX` or `4.x.x.redhat-XXXXX` | Red Hat Build of Apache Camel |

**Qualifier decoding rules:**
- `redhat-6XXXXX` → Fuse 6.x (digits after `6` encode minor/patch/SP)
- `fuse-7XXXXX-redhat-XXXXX` → Fuse 7.x
- `fuse-8XXXXX-redhat-XXXXX` → Fuse Online / Camel Extensions
- `redhat-XXXXX` (no `fuse-` prefix, on Camel 3.x/4.x) → Red Hat Build of Apache Camel

Include the detected product name in the analysis summary (Step 4) as `Source Product`.

### If Vendor Detected

```
✓ Vendor detected: [Vendor Name] [version]
  Product: [Red Hat product name, if detected — e.g. "Red Hat JBoss Fuse 6.3.0"]
  Evidence: [key signals found — e.g. "camel-core 2.17.0.redhat-630187, karaf-maven-plugin"]

Proceeding to build analysis summary...
```

### If Vendor Unknown

```
Could not identify a supported integration platform.

Signals found across all scanned files:
- [list XML namespaces, groupIds, descriptor files, framework-specific keys]

Currently supported vendors:
- MuleSoft Mule (3.x and 4.x)
- Apache Camel (2.x and 3.x → 4.x version migration)
```

**Recovery options — present to user:**

```
How would you like to proceed?

1. Specify vendor manually — Tell me which platform this is (e.g., "This is Mule 4")
2. Try a different path — Point me to a specific config file that identifies the platform
3. Abort — Open a GitHub issue for vendor support:
   https://github.com/luigidemasi/camel-kit/issues
```

If the user specifies a vendor manually, validate it against the supported vendors list and proceed with that vendor. If the user points to a specific file, re-scan that file for vendor signals. If neither works, abort.

---

## Step 4 — Build Pre-Populated Analysis Summary

Synthesise all findings from Step 3 into a structured summary. Mark each field as:
- ✓ **Confirmed** — found in artifacts with high confidence
- ~ **Inferred** — derived from indirect signals; should be confirmed
- ? **Unknown** — not found; must ask the user

```
MIGRATION ANALYSIS SUMMARY
══════════════════════════════════════════════════════

Vendor & Version
  ✓ [Vendor Name] [version]

Source Product
  [✓/~/? ] [Red Hat product name — e.g. "Red Hat JBoss Fuse 6.3.0" or "Community Apache Camel"]

Business Purpose
  [✓/~/? ] [extracted text or "Not found in artifacts"]

Owning Team
  [✓/~/? ] [extracted text or "Not found in artifacts"]

SLA / Performance
  Throughput:        [✓/~/? ] [value or "Not found"]
  Latency target:    [✓/~/? ] [value or "Not found"]
  Peak load periods: [✓/~/? ] [value or "Not found"]
  Deployment target: [✓/~/? ] Kubernetes / single instance / unknown

Compliance & Security
  [✓/~/? ] [findings or "No compliance signals detected"]

Failure Behaviour
  Error strategy:    [✓/~/? ] [DLQ / retry / log-and-continue / not defined]
  Retry policy:      [✓/~/? ] [count + delay extracted or "Not found"]
  DLQ endpoint:      [✓/~/? ] [name or "Not found"]
  Alert mechanism:   [✓/~/? ] [found or "Not found"]

Target Camel Version
  [✓/~/? ] [Red Hat supported versions ONLY: 4.14.4 ⭐ / 4.10.7 / 4.8.5 / 4.4.0 / 4.0.0]
  Default: 4.14.4 (latest Red Hat supported version)

Target Runtime
  [✓/~/? ] [quarkus / spring-boot / camel-main / jbang]

API Compatibility
  ✓ Assumed: Camel routes will preserve the same HTTP paths, queue/topic names,
    and data contracts as the original integration unless the user explicitly says otherwise.

Project Layout
  [✓/~/? ] [single-project / multi-project]

Flows to migrate: [N] flows detected
  - [flow-name-1] — source: [relative/path/to/source/project] → target: [target-module/]
  - [flow-name-2] — source: [relative/path/to/source/project] → target: [target-module/]
  - ...

══════════════════════════════════════════════════════
```

---

## Step 5 — Confirm with User

Present the summary and ask the user to confirm or correct it. Ask only about **? Unknown** fields and invite corrections on **~ Inferred** fields. Do not ask about **✓ Confirmed** fields.

```
I've analysed your project and built the following migration summary.
Please confirm, correct, or fill in any gaps.

[show summary from Step 4]

Assumptions:
- API compatibility: Camel routes will preserve the same HTTP paths, queue/topic names,
  and data contracts as the original integration. Say "not compatible" if this should change.

[List any remaining ? Unknown fields — if none, skip this section]

Are there any ~ Inferred fields above that need correcting? (or say "looks good")
```

Wait for the user's response. Update the summary with their answers. This is the **only** interaction before delegating to the sub-skill.

**CRITICAL — Target version MUST be a Red Hat supported version:**

Fetch the directory listing from `https://maven.repository.redhat.com/ga/org/apache/camel/camel-bom/` to discover available Red Hat Build versions and their latest `.redhat-XXXXX` qualifiers. Parse the version directories to build the supported versions list (only `4.x` versions). The highest base version is the recommended default.

**If the fetch fails** (network error, timeout, etc.), fall back to this static table:

| Base Version | Full Maven Version |
|-------------|-------------------|
| `4.14.4` | `4.14.4.redhat-00008` |
| `4.10.7` | `4.10.7.redhat-00009` |
| `4.8.5` | `4.8.5.redhat-00008` |
| `4.4.0` | `4.4.0.redhat-00046` |
| `4.0.0` | `4.0.0.redhat-00036` |

The target Camel version MUST be one of the versions from the discovered or fallback list. If the user requests a community-only version (e.g., `4.18.0`, `4.12.0`), warn them and ask to select a supported version:

```
⚠️ Version [version] is not supported by Red Hat Build of Apache Camel.

Only the following versions are supported:
  [list from discovery or fallback]

Please select a supported version.
```

If the user does not specify a version, default to the highest base version (latest).

**CRITICAL — Persist confirmed settings to `.camel-kit/config.yaml`:**

After the user confirms the summary, create or update `.camel-kit/config.yaml` with the confirmed target runtime and Camel version (with `.redhat-XXXXX` Maven qualifier — use the latest qualifier discovered from the repository listing). This is REQUIRED so that downstream skills (`/camel-implement`, `/camel-validate`, `/camel-test`) know where to place generated files and which Camel version to target.

```yaml
project:
  camelVersion: "{{CAMEL_VERSION_WITH_REDHAT_QUALIFIER}}"
  runtime: "quarkus"  # or "spring-boot" or "camel-main" or "jbang"
```

If `.camel-kit/config.yaml` already exists, merge the `project.runtime` key without overwriting other settings. If it does not exist, create it with both `camelVersion` and `runtime`.

---

## Step 6 — Delegate to Vendor-Specific Guide

Pass the confirmed summary and all artifact paths to the vendor-specific migration guide.

### MuleSoft Mule

```
🫏 → 🐪

Vendor: MuleSoft Mule [version]
Flows:  [N] flows ready for migration

Starting MuleSoft migration...
```

→ Load `guides/mulesoft-migration.md` and follow those instructions.

Pass as context:
- The confirmed analysis summary from Step 5
- The full list of source artifact paths
- `CAMEL_VERSION` from `.camel-kit/config.yaml` (or ask the user if not found)
- `RUNTIME` from `.camel-kit/config.yaml` (`project.runtime`)
- `PLATFORM_BOM` — resolved from `CAMEL_VERSION` + `RUNTIME` via `skills/shared/mcp-setup.md`

### Apache Camel (older version)

Use the version-aware easter egg based on the detected source major version:

- **Camel 2.x → 4.x:** `🐪₂ → 🐪₄`
- **Camel 3.x → 4.x:** `🐪₃ → 🐪₄`
- **Camel 4.x → 4.y (minor upgrade):** `🐪₄ ⬆ 🐪₄`

```
[version-aware easter egg]

Vendor: Apache Camel [version]
Platform: [ServiceMix/Karaf | Spring Boot | Spring XML | Plain Java]
Routes: [N] routes ready for migration

Starting Camel version migration...
```

→ Load `guides/camel-version-migration.md` and follow those instructions.

Pass as context:
- The confirmed analysis summary from Step 5
- The full list of source artifact paths
- The detected Camel source version (2.x or 3.x) and platform type
- `CAMEL_VERSION` from `.camel-kit/config.yaml` (target version — or ask the user if not found)
- `RUNTIME` from `.camel-kit/config.yaml` (`project.runtime`)
- `PLATFORM_BOM` — resolved from `CAMEL_VERSION` + `RUNTIME` via `skills/shared/mcp-setup.md`

---

## MCP Server Configuration

→ **For MCP setup, version mapping, and fallback policy:** see `skills/shared/mcp-setup.md`

Migration guides use the Camel MCP server for catalog verification and the camel-knowledge MCP server for migration-specific documentation. See individual guides for their specific MCP tool usage.

---

## Notes

- This skill performs detection, scanning, and confirmation only. All vendor-specific analysis and TDD generation happens in the migration guides.
- Migration guides receive a pre-populated summary and MUST NOT re-ask questions already answered here.
- Every future vendor migration guide follows the same contract: receive summary → do vendor-specific work → fill genuine gaps only.
- The output of any migration guide is identical to `/camel-project` + `/camel-flow` output, making it fully compatible with `/camel-implement`.
