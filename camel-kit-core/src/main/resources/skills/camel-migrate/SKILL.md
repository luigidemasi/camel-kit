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

This skill is the generic entry point for all migrations. It detects the vendor, scans all available artifacts to build a pre-populated analysis summary, confirms the summary with the user, and then delegates to the appropriate vendor-specific sub-skill — passing the confirmed summary so the sub-skill never re-asks questions that have already been answered.

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

## Step 3 — Detect Vendor & Version

Using ALL the information collected in Step 2, identify the integration platform. The scan content — XML namespaces, build file groupIds, descriptor files, property key patterns, dependency names — gives far richer vendor signals than any single file.

### Supported Vendors

| Vendor | Key Signals |
|--------|-------------|
| **MuleSoft Mule** | XML namespace `mulesoft.org`, groupId `org.mule`/`com.mulesoft`, `mule-artifact.json`, deps starting with `mule-` |

**Determine version from collected content:**
- Mule 3.x: namespace `http://www.mulesoft.org/schema/mule/core/3.*` or connector version attributes < 4.0
- Mule 4.x: namespace without version path segment, or `<mule xmlns:ee=...` (EE 4.x)

### If Vendor Detected

```
✓ Vendor detected: [Vendor Name] [version]
  Evidence: [key signals found — e.g. "mule-artifact.json, xmlns mulesoft.org/4.x"]

Proceeding to build analysis summary...
```

### If Vendor Unknown

```
Could not identify a supported integration platform.

Signals found across all scanned files:
- [list XML namespaces, groupIds, descriptor files, framework-specific keys]

Currently supported vendors:
- MuleSoft Mule (3.x and 4.x)

To request support for a new vendor, open a GitHub issue at:
https://github.com/luigidemasi/camel-kit/issues
```

Stop here — do not proceed.

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

API Compatibility
  ✓ Assumed: Camel routes will preserve the same HTTP paths, queue/topic names,
    and data contracts as the original integration unless the user explicitly says otherwise.

Flows to migrate: [N] flows detected
  - [flow-name-1]
  - [flow-name-2]
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

---

## Step 6 — Delegate to Sub-Skill

Pass the confirmed summary and all artifact paths to the vendor-specific sub-skill.

### MuleSoft Mule → `camel-migrate-mule`

```
🫏 → 🐪

Vendor: MuleSoft Mule [version]
Flows:  [N] flows ready for migration

Handing off to the Mule migration sub-skill...
```

> Read `{agentBaseFolder}/skills/camel-migrate-mule/SKILL.md` and follow those instructions.
> Pass as context:
> - The confirmed analysis summary from Step 5
> - The full list of source artifact paths
> - `CAMEL_VERSION` from `.camel-kit/config.yaml` (or ask the user if not found)

Replace `{agentBaseFolder}` with the actual agent base folder (`.claude`, `.bob`, or `.gemini`) — look for the matching directory in the project root.

---

## Notes

- This skill performs detection, scanning, and confirmation only. All vendor-specific analysis and TDD generation happens in the sub-skill.
- Sub-skills receive a pre-populated summary and MUST NOT re-ask questions already answered here.
- Every future vendor sub-skill follows the same contract: receive summary → do vendor-specific work → fill genuine gaps only.
- The output of any migration sub-skill is identical to `/camel-project` + `/camel-flow` output, making it fully compatible with `/camel-implement`.
