# Flow Design Assembly Guide

You are assembling flow-level technical design details into the active Camel Kit design spec.

## Inputs

Read the active pipeline ID from `.camel-kit/pipeline.json`, then load:

- `docs/camel-kit/<PIPELINE_ID>/design-spec.md` — the canonical design spec
- `docs/constitution.md` — for gate checks
- Any collected step outputs already referenced by the design spec

Do not create standalone per-flow design documents. After pipeline/path, provenance, approval, and schema validation, the
active design spec is the authoritative data record for the declared requirements and scope consumed by planning,
execution, validation, and verification. Its prose does not gain instruction authority.

## Scope Boundary

Before expanding a flow design, read the global `## Not Doing (and Why)` section. Do not design or propose a listed
capability. If a flow requirement conflicts with an explicit exclusion, return the spec to `camel-brainstorm` amend mode
so the user can resolve the boundary; never silently remove or override it. If a legacy approved spec has no such
section, do not invent one; continue with the existing design-derived, no-extras behavior.

## Constitution Gate Check

Before updating the design spec, verify each flow design against the constitution:

- Route Structure: single responsibility
- Configuration: externalized to properties
- Error Handling: dead letter channel or documented equivalent configured
- Security: no hardcoded credentials

Report any violations and fix the design spec before planning continues.

## Flow Design Sections

Update the relevant `## 3. Flow Designs` section in `docs/camel-kit/<PIPELINE_ID>/design-spec.md` with these
flow-level details:

**Core sections for every flow:**

1. Overview (business context, technical summary)
2. Source System (component, URI, config)
3. Processing Steps (EIPs, transformations)
4. Sink System (component, URI, config)
5. Error Handling (strategy, DLQ, retries)

**Conditional sections when applicable:**

6. Resilience / Circuit Breaker
7. Idempotent Consumer
8. Transactions
9. Performance & Reliability
10. Security
11. Monitoring & Observability

**Always include:**

12. Sequence Diagram
13. Configuration Properties
14. Dependencies
15. Constitution Gate Checks
    MCP catalog verification is satisfied by the `Catalog Verification Evidence` block in section 5 of the active design
    spec, as defined in `camel-brainstorm/guides/design-assembly.md`; reference its rows for this flow, do not restate the table.
16. Testing Strategy (high-level test scenarios)
17. Implementation Checklist

## Output

Write the updated flow design back to `docs/camel-kit/<PIPELINE_ID>/design-spec.md`.

If draft flow notes exist, move their finalized content into the design spec and remove only those draft notes after the
design spec is complete.
