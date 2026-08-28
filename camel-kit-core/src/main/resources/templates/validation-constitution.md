# Camel-Kit Validation — Constitution & Dependency Checks

Verify routes against `docs/constitution.md` and resolve internal dependencies.
Validation is static and report-only.

## Constitution Compliance Checks

| Check ID | Rule | Severity | Message |
|----------|------|----------|---------|
| `CONST-001` | Route structure has a source and sink | ERROR | Route is missing a source or required sink |
| `CONST-002` | Route has one responsibility | WARNING | Route is too complex; consider splitting it |
| `CONST-003` | Ingestion, processing, and delivery concerns are separated | WARNING | Business and integration concerns are mixed |
| `CONST-004` | Route IDs, internal endpoints, and custom headers follow naming conventions | WARNING | Identifier does not follow the required convention |
| `CONST-005` | Route declares observability metadata and required correlation | WARNING | Route ID, description, or correlation is missing |
| `CONST-006` | Environment values and secrets use external configuration | ERROR | Connection or environment value is hardcoded |
| `CONST-007` | Every Camel artifact is catalog-verified | ERROR | Component, EIP, data format, language, or option is unverified |
| `CONST-008` | Infrastructure follows the Forage configuration ladder | ERROR/WARNING | Unknown `forage.*` key or unexplained hand-written bean |

## Dependency Checks

| Check ID | Rule | Severity | Message |
|----------|------|----------|---------|
| `DEP-001` | Referenced `direct:` endpoint has a corresponding route | ERROR | No route consumes the endpoint |
| `DEP-002` | Referenced `seda:` endpoint has a corresponding route | WARNING | No route consumes the endpoint |
| `DEP-003` | Route dependencies are acyclic | ERROR | Circular dependency detected |
| `DEP-004` | Aggregation has a completion condition | ERROR | Aggregate has no completion condition |
| `DEP-005` | Split is aggregated or intentionally fire-and-forget | WARNING | Split has no aggregation contract |
| `DEP-006` | Bean references are documented and resolvable | WARNING | Bean reference is missing or undocumented |

## Reporting Contract

Record errors, warnings, evidence, and recommendations in exactly one Markdown
report selected by `camel-validate`:

- Pipeline-scoped: `docs/camel-kit/<PIPELINE_ID>/validation-report.md`
- Project-scoped: `docs/validation-report-YYYY-MM-DD_HH-mm.md`

A failed check is reported and routed to the owning implementation path;
validation does not generate or modify application/test artifacts. There are no
strict/lenient command modes and no retired dotted generation commands in this
workflow.
