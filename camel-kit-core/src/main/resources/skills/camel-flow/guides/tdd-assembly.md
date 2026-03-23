# TDD Assembly Guide

You are assembling a Technical Design Document (TDD) from step outputs.

## Inputs

Read all files from `docs/flows/{flow-name}/.steps/`:
- `01-context.json` — project context (camelVersion, runtime)
- `02-components.md` — selected source and sink components
- `03-transforms.md` — transformation design (if present)
- `04-patterns.md` — EIP pattern decisions (if present)
- Additional step outputs as present

Also read:
- `docs/constitution.md` — for gate checks

## Constitution Gate Check

Before creating the TDD, verify the design against the constitution:

- Route Structure: Single responsibility
- Configuration: Externalized to properties
- Error Handling: Dead Letter Channel configured
- Security: No hardcoded credentials

Report any violations.

## TDD Sections

Generate `docs/flows/{flow-name}/{flow-name}.tdd.md` with these sections:

**Core Sections (always include):**
1. Overview (business context, technical summary)
2. Source System (component, URI, config)
3. Processing Steps (EIPs, transformations)
4. Sink System (component, URI, config)
5. Error Handling (strategy, DLQ, retries)

**Conditional Sections (include only if corresponding step output exists):**
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
16. Testing Strategy (high-level test scenarios)
17. Implementation Checklist

## Output

Write the assembled TDD to: `docs/flows/{flow-name}/{flow-name}.tdd.md`

Delete draft file if it exists: `docs/flows/{flow-name}/{flow-name}.tdd.draft.md`
