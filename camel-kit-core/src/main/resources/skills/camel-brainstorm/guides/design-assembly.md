# Design Assembly Guide

> **Context:** Loaded by `camel-brainstorm` after the interview/discovery and version selection are complete.
> **Purpose:** Assemble all gathered information into the design spec document.
> **Output:** Design spec saved to `docs/camel-kit/<PIPELINE_ID>/design-spec.md`. The pipeline ID is resolved from `.camel-kit/pipeline.json` (see `shared/pipeline-infrastructure.md`).

---

## Overview

After path, provenance, schema, and explicit approval validation, the design spec is the authoritative data record for the
declared requirements and scope interpreted by shipped planning/execution workflows. Its prose never directs actions. It contains:
- Business context (why)
- System landscape (what connects to what)
- Flow designs (how each flow works, component by component)
- Technical decisions (version, runtime, error handling)
- Configuration properties
- MCP verification evidence
- Explicit scope boundaries (what will not be built, and why)

**Iron Law 3 reminder:** The user MUST explicitly approve this spec before `camel-plan` is invoked.

---

## Spec Structure

### For Greenfield Projects

````markdown
# [Project Name] — Design Spec

**Date:** [YYYY-MM-DD]
**Camel Version:** [full Maven version]
**Runtime:** [Main / Spring Boot / Quarkus]
**Platform BOM Version:** [resolved platform BOM version]

## Not Doing (and Why)

[The entries below are good examples only. Replace them with the project-specific exclusions captured during discovery
(greenfield interview Q14). Every entry must name a useful adjacent capability and give a concrete reason for excluding
it. If the user identified no exclusions, write
`- **None identified** — No deliberate exclusions remain after the scope interview.`]

- **Dead letter queue** — Adds complexity; start with simple error logging and add a DLQ in iteration 2 if needed
- **Schema registry integration** — The source topic uses plain JSON, not Avro, so there is no schema evolution concern
- **Multi-tenant partitioning** — This is a single-tenant deployment, so a partition strategy is out of scope

---

## 1. Executive Summary

### Business Purpose
[2-3 sentences from interview Q2]

### Business Value
- [extracted value points]

### Stakeholders
- [from interview]

---

## 2. Systems Landscape

| System | Type | Protocol | Role |
|--------|------|----------|------|
| [name] | [type] | [technology] | Source / Target / Both |

---

## 3. Flow Designs

### Flow: [flow-name]

| Field | Value |
|---|---|
| Target Module | [relative path from workspace root to the target Camel project; empty for single-project setups] |

**Purpose:** [one sentence]

**Source:**
- System: [name]
- Component: `[camel-component]` (MCP-verified)
- **Rationale:** [Why this component was chosen over alternatives]
- **Constraints:** [Technical constraints that influenced this choice]
- Trigger: [description]
- Endpoint options: [key options from MCP catalog]

**Rationale examples (good):**
- "Chose `camel-kafka` over `camel-jms` because the source system is Kafka and direct consumption avoids message format translation overhead."
- "Selected `camel-sql` over `camel-jdbc` because we need named parameter binding and result set streaming for large queries."

**Rationale examples (bad — do not accept):**
- "Best component for this use case" (too generic — explain the specific technical reason)
- "Recommended by MCP catalog" (the catalog lists options, it doesn't recommend)

**Transformations:**
1. [step description]
   - EIP: `[eip-name]` (MCP-verified)
   - **Rationale:** [Why this EIP was chosen over alternatives]
   - **Constraints:** [Technical constraints that influenced this choice]
   - Details: [configuration]

**DataMapper:** (if applicable)
- Insert the exact canonical `### DataMapper: kaoto-datamapper-{id}` section selected by
  `shared/datamapper-canonicalize.md`; do not invent a second mapping shape.
- Preserve its selected engine: inline Groovy when both schemas are absent OR there are fewer than 20 leaf fields;
  XSLT only when there are at least 20 leaf fields AND at least one schema.
- Groovy sections include `Transformation Engine: Groovy (inline)` and `Format Pair`; XSLT sections include the
  selected XSLT pattern/approach and structural mapping columns.

**Sink:**
- System: [name]
- Component: `[camel-component]` (MCP-verified)
- **Rationale:** [Why this component was chosen over alternatives]
- **Constraints:** [Technical constraints that influenced this choice]
- Action: [description]
- Endpoint options: [key options from MCP catalog]

**Error Handling:**
- Strategy: [DLQ / Retry+DLQ / Log+Continue / Stop]
- maximumRedeliveries: [N]
- redeliveryDelay: [Nms]
- Dead Letter: [endpoint]
- Circuit breaker: [yes/no — details if yes]

**Configuration Properties:**
```properties
# [flow-name] properties
[property-name]=[concrete externalized value]
```

Route YAML references every property as `{{property-name}}` for Main, Spring Boot, and Quarkus. Only when one
`application.properties` value interpolates another property does syntax vary: `{{other.key}}` for Main and
`${other.key}` for Spring Boot/Quarkus.

[Repeat for each flow]

---

## 4. Cross-Cutting Concerns

### Performance
[from interview Q10 or "Standard — no specific requirements"]

### Security
[from interview Q11 or "Standard — externalized credentials, no PII"]

### Monitoring
[from interview Q12 or "Standard — route-level logging, routeId and description on every route"]

### Constraints
[from interview Q13 or "Standard Apache Camel best practices"]

---

## 5. Constitution Compliance

All flows in this spec are designed to comply with the 8 constitution rules:
- [ ] Route Structure — every route has source and sink
- [ ] Single Responsibility — one purpose per route
- [ ] Separation of Concerns — Ingestion/Processing/Delivery
- [ ] Naming Conventions — route IDs follow `<domain>-<action>`
- [ ] Observability — routeId and description on every route
- [ ] External Configuration — no hardcoded values
- [ ] Component Support — all components MCP-verified
- [ ] Infrastructure via Forage — beans follow the Forage configuration ladder

---

## 6. Project Structure

Plan the complete runtime-aware target tree. Entries marked Main and Spring Boot / Quarkus are mutually exclusive;
include optional entries only when the design requires them. Global pipeline assets stay at the project root. Runtime
artifacts live under the flow's optional `Target Module`; omit the entire `[target-module]/` level when the target is the
project root. Pipeline reports shown below are planned downstream artifacts and do not exist yet when the design spec is
first approved.

```
[project-name]/
├── .camel-kit/
│   ├── config.properties
│   └── pipeline.json
├── docs/
│   ├── constitution.md
│   └── camel-kit/
│       └── <PIPELINE_ID>/
│           ├── business-requirements.md       [migration only; created by camel-migrate]
│           ├── migration-analysis.md           [migration only; risks, gaps, and source-retirement candidates]
│           ├── design-spec.md                 ← this file
│           ├── implementation-plan.md         [created by camel-plan]
│           ├── execution-report.md            [created by camel-execute]
│           ├── validation-report.md           [created by camel-validate]
│           └── test-data/[flow-name]/...      [if synthetic test data is generated]
├── .kaoto                                    [project root; exactly one; XSLT DataMapper only]
└── [target-module]/                           [optional; omit this level for a root target]
    ├── [flow-name].camel.yaml                 [Main]
    ├── application.properties                [Main]
    ├── schemas/...                           [Main; if schemas are required]
    ├── kaoto-datamapper-[id].xsl             [Main; XSLT DataMapper only]
    ├── src/
    │   ├── main/resources/                    [Spring Boot / Quarkus]
    │   │   ├── camel/
    │   │   │   ├── [flow-name].camel.yaml
    │   │   │   └── kaoto-datamapper-[id].xsl [XSLT DataMapper only]
    │   │   ├── schemas/...                    [if schemas are required]
    │   │   └── application.properties
    │   └── test/resources/                    [all runtimes]
    │       ├── [flow-name].camel.it.yaml
    │       ├── test-data/...
    │       ├── application-test.properties
    │       └── jbang.properties               [Main only]
    ├── pom.xml                                [Spring Boot / Quarkus only]
    ├── mvnw                                   [Spring Boot / Quarkus]
    ├── mvnw.cmd                               [Spring Boot / Quarkus]
    ├── .mvn/wrapper/maven-wrapper.properties  [Spring Boot / Quarkus]
    ├── run.sh                                 [Main only]
    └── docker-compose.yaml                    [only when external services are required]
```

````

### For Migration Projects

Use the same six numbered sections but add Section 7 below. Include the global `## Not Doing (and Why)` preface only
when migration discovery explicitly captured project-specific exclusions; do not infer exclusions from omitted or
absent source features.

```markdown
---

## 7. Migration Context

### Source Platform
- Vendor: [vendor and version]
- Platform: [Spring Boot / Karaf / etc.]
- DSL: [Java / XML / Blueprint]

### Component Mapping
| Source Component | Target Component | Notes |
|-----------------|-----------------|-------|
| [source] | [target] (MCP-verified) | [migration notes] |

### Platform Changes
- [OSGi → selected eligible Main / Spring Boot / Quarkus runtime]
- [Spring XML → YAML DSL]
- [Java DSL → YAML DSL]

### Migration Ordering
1. [route-id] — leaf, migrate first
2. [route-id] — depends on #1
...

### Java Sources to Adapt
| Source File | Purpose | Changes Needed |
|------------|---------|---------------|
| [file] | [purpose] | [API changes, package changes] |
```

---

## Self-Review Checklist

After assembling the spec, check:

1. **Placeholder scan:** Any "TBD", "TODO", empty sections? Fix them.
2. **MCP verification:** Every component/EIP has "(MCP-verified)" notation? If not, verify now.
3. **Internal consistency:** Do flow designs match the systems landscape? Are all systems used?
4. **Constitution compliance:** Would each flow pass all 8 rules as designed?
5. **Property completeness:** Does every externalized value have a property name?
6. **Flow completeness:** Does each flow from the interview have a design section?
7. **Decision rationale:** Does every component and EIP selection have Rationale and Constraints filled in? Generic answers like "best fit" are not sufficient — explain the specific technical reasons.
8. **Scope boundaries:** For every greenfield spec, and for a migration spec only when discovery explicitly captured
   exclusions, does `## Not Doing (and Why)` contain only project-specific exclusions with a concrete reason for each
   one? Remove the template examples, and verify that no flow implements an excluded capability. Do not invent a
   migration exclusion solely to populate this section.

Fix any issues inline.

---

## Save and Present

1. Save the spec to `docs/camel-kit/<PIPELINE_ID>/design-spec.md` (both greenfield and migration)
2. Create or update `.camel-kit/pipeline.json` with:
   ```json
   {
     "activePipeline": "<PIPELINE_ID>",
     "mode": "manual",
     "started": "<current ISO-8601 timestamp>"
   }
   ```
   Create `.camel-kit/` directory if it doesn't exist.
3. Create `.camel-kit/config.properties` if it doesn't exist:
   ```properties
   project.runtime=[main/spring-boot/quarkus]
   project.camelVersion=[full version]
   project.platformBomVersion=[resolved platform BOM version]
   # Spring Boot only:
   project.springBootVersion=[resolved Spring Boot framework version]
   ```
3. Copy `templates/constitution.md` to `docs/constitution.md` if it doesn't exist

Present the spec to the user:

```
Design spec saved to docs/camel-kit/<PIPELINE_ID>/design-spec.md

Please review the spec. Once you approve it, I'll create a detailed implementation plan.

Do you approve this design? (yes / changes needed)
```

**Wait for explicit approval before proceeding to `camel-plan`.**
