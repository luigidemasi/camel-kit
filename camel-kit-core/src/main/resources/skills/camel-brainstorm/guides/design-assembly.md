# Design Assembly Guide

> **Context:** Loaded by `camel-brainstorm` after the interview/discovery and version selection are complete.
> **Purpose:** Assemble all gathered information into the design spec document.
> **Output:** Design spec saved to `docs/camel-kit/<PIPELINE_ID>/design-spec.md`. The pipeline ID is resolved from `.camel-kit/pipeline.json` (see `shared/pipeline-infrastructure.md`).

---

## Overview

The design spec is the single source of truth for what gets built. It contains:
- Business context (why)
- System landscape (what connects to what)
- Flow designs (how each flow works, component by component)
- Technical decisions (version, runtime, error handling)
- Configuration properties
- MCP verification evidence

**Iron Law 3 reminder:** The user MUST explicitly approve this spec before `camel-plan` is invoked.

---

## Spec Structure

### For Greenfield Projects

```markdown
# [Project Name] — Design Spec

**Date:** [YYYY-MM-DD]
**Camel Version:** [full Maven version]
**Runtime:** [JBang / Spring Boot / Quarkus]
**Platform BOM Version:** [resolved platform BOM version]

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
- Source format: [JSON/XML]
- Target format: [JSON/XML]
- Approach: [A (useJsonBody) / B (header param)]
- Field mappings:
  | Source XPath | Target Element | Transform |
  |-------------|---------------|-----------|
  | [xpath] | [element] | [logic] |

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
```yaml
# [flow-name] properties
[property-name]: "{{PLACEHOLDER}}"
```

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

```
[project-name]/
├── .camel-kit/
│   ├── config.properties
│   └── pipeline.json
├── docs/
│   ├── constitution.md
│   └── camel-kit/
│       └── <PIPELINE_ID>/
│           └── design-spec.md          ← this file
├── src/main/resources/
│   ├── camel/
│   │   ├── [flow-1].camel.yaml
│   │   ├── [flow-2].camel.yaml
│   │   └── ...
│   └── application.properties
├── [xslt files if DataMapper used]
└── pom.xml / docker-compose.yml
```
```

### For Migration Projects

Use the same structure but add:

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
- [OSGi → Spring Boot/Quarkus]
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
   Create `.camel-kit/` directory if it doesn't exist. If `pipeline.json` already exists with `mode: "ship"`, preserve the existing ship state and only update `activePipeline`.
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
