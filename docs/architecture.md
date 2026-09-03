# Camel-Kit Architecture Guide

This document describes Camel-Kit's internal architecture for contributors and extenders. For user-facing documentation, see the [User Guide](user-guide.md) and [Command Reference](commands.md).

---

## 1. Overview

Camel-Kit combines two mechanisms to give AI agents accurate, efficient access to the Apache Camel ecosystem:

- **Skills** -- markdown instruction files that guide LLM agents through structured workflows (design, implementation, validation, testing, verification)
- **MCP Servers** -- real-time queries against live Camel, documentation, and Citrus catalogs for component verification, validation, security analysis, and test generation

Together they enable AI-powered integration development targeting Apache Camel. Skills carry the process knowledge (how to design a flow, how to generate YAML, how to validate a route), while MCP provides the data knowledge (which components exist, what options they accept, whether an endpoint URI or Citrus test action is valid).

The authoritative workflow contract lives in `camel-kit-core/src/main/resources/workflow/camel-kit-workflow.yaml`. It defines command names and aliases, generated command stubs, skill visibility, pipeline stages, artifacts, transitions, MCP servers, allowed tools, and documentation references. Generator code reads this manifest for command stubs and for tool filters where the target runtime supports them; tests validate skill frontmatter against it. When changing workflow behavior, update the manifest first and then update the Markdown skill bodies and docs to match.

---

## 2. Skills Architecture

### What a Skill Is

A skill is a directory containing a manifest file (`SKILL.md`) and an optional `guides/` subdirectory with instruction files loaded on demand. The manifest uses YAML frontmatter to declare metadata and a table listing which guides exist and when to load them.

**Skill location:** `camel-kit-core/src/main/resources/skills/{skill-name}/`

**Workflow manifest:** `camel-kit-core/src/main/resources/workflow/camel-kit-workflow.yaml` is the source of truth for skill visibility and generated slash-command stubs. The `SKILL.md` frontmatter must match the manifest.

### SKILL.md Format

```markdown
---
name: camel-{name}
description: Brief description with trigger keywords
user_invocable: false
---

# /camel-{name}

> One-line purpose

## Guides

| Guide | When Loaded | Purpose |
|-------|-------------|---------|
| `guides/main-guide.md` | Always | Primary instruction guide |
| `guides/optional-guide.md` | When condition X | Supplementary guide |
```

**Note:** Only `camel-start` sets `user_invocable: true` — it is the single auto-discovered entry point (meta-router). Pipeline and standalone skills (Tier 1/2) are invoked through generated command stubs on slash-command agents and through native project skill selection on Codex CLI and GitHub Copilot CLI. Internal skills are dispatched only by pipeline skills.

The frontmatter fields:
- `name` -- skill identifier, used in cross-references
- `description` -- trigger keywords that help agents match user intent to the correct skill
- `user_invocable` -- `true` for `camel-start` (meta-router) only. Pipeline and standalone skills (Tier 1/2) still have generated entry points despite `user_invocable: false`: slash-command stubs for most agents and project skills for Codex CLI and GitHub Copilot CLI. Internal skills (`camel-verify`, `camel-design`, `camel-implement`, `camel-test`) are dispatched only by pipeline skills

Agent-specific generators may add runtime aliases to copied skill files. For example, Copilot, Pi, and Qwen generated
copies add `user-invocable` alongside Camel-Kit's source `user_invocable` metadata. Codex generated copies
adapt exact `/camel-*` skill invocations to native `$camel-*` mentions while leaving file paths unchanged.

### All Skills

| Skill | User-Invocable | Loaded By | Purpose |
|-------|---------------|-----------|---------|
| `camel-start` | Yes | -- | Meta-router and primary entry point: detects intent, loads appropriate pipeline |
| `camel-brainstorm` | No | `camel-start` (greenfield) | Orchestrate design phase: interview user, produce the pipeline design spec |
| `camel-plan` | No | `camel-brainstorm` (after design approval) | Produce detailed implementation plan from approved design spec |
| `camel-execute` | No | `camel-plan` (auto-invoked after planning) | Environment probe, adversarial pre-filter, then ordered spec and quality review per task |
| `camel-migrate` | No | `camel-start` (migration) | Vendor-aware risk, retirement, and safe-seam analysis plus design; hands an approved design to `camel-plan` |
| `camel-verify` | No | `camel-execute` (internal role: subagent where supported, inline otherwise) | 3-phase runtime verification loop (build, Citrus tests, report) — runs inside execute, not as a standalone pipeline stage |
| `camel-ship` | No | -- (standalone CLI delegate) | Forwards to the configured local Ship command; the controller owns stages, run state, and oversight |
| `camel-design` | No | `camel-brainstorm` | Guides for component selection, EIP catalog, and flow design assembly |
| `camel-implement` | No | `camel-execute` | Guides for YAML generation, properties, Docker Compose, DataMapper |
| `camel-validate` | No | `camel-execute` or direct invocation | Tier 1 quality gate: schema validation, endpoint verification, security analysis |
| `camel-test` | No | `camel-execute` | Guides for route analysis and test generation with Citrus + Testcontainers |
| `camel-knowledge` | No | Direct invocation; pipeline skills as needed | Routes questions to knowledge MCP tools |
| `camel-debug` | No | `camel-start` (ad-hoc troubleshooting) | Standalone debugging: STOP → PRESERVE → DIAGNOSE → FIX → GUARD workflow |

**Note:** Only `camel-start` has `user_invocable: true` in its skill metadata. Pipeline and standalone skills still have generated entry points despite `user_invocable: false`: slash-command stubs for most agents and project skills for Codex CLI and GitHub Copilot CLI. Internal skills (`camel-verify`, `camel-design`, `camel-implement`, `camel-test`) are dispatched only by pipeline skills.

### Shared Guides

Shared guides live at `camel-kit-core/src/main/resources/skills/shared/` and are loaded by multiple skills:

| Guide | Purpose |
|-------|---------|
| `iron-laws.md` | Six non-negotiable pipeline process enforcement rules |
| `camel-security-checklist.md` | Canonical Camel security rules and configuration snippets shared by design, validation, and review |
| `datamapper-canonicalize.md` | Engine selection and field mapping enrichment for DataMapper |
| `flow-test-data.md` | Test data generation patterns for flow design |
| `mcp-setup.md` | MCP version mapping and connection parameters |
| `graph-availability.md` | Graph MCP server availability detection |
| `context-authority.md` | Data versus instruction authority for every loaded context, response, and handoff |
| `discovery-completeness.md` | Shared discovery and completeness semantics for brainstorm interviews and Ship discovery |
| `forage.md` | Forage configuration-driven infrastructure beans and the configuration ladder |
| `pipeline-infrastructure.md` | File-based pipeline handoff, artifact provenance, and staleness conventions |
| `yaml-structure.md` | YAML DSL structure rules and Kaoto compatibility |
| `yaml-components.md` | Component URI syntax and parameter rules |
| `yaml-examples.md` | Reference YAML patterns for common integrations |
| `patterns-foundational.md` | Foundational EIP patterns (content-based routing, splitter, aggregator) |
| `patterns-error-handling.md` | Error handling patterns (dead letter channel, retry, circuit breaker) |
| `patterns-deployment.md` | Deployment patterns (health checks, graceful shutdown, scaling) |

Vendor migration guides (MuleSoft and BizTalk phases and mappings) live under `skills/camel-migrate/guides/`, not in `shared/`.

### Project Graph Parsers

The `camel-kit-graph` module builds a property graph of the project structure by running a set of parsers over the project's source files. Each parser produces typed nodes and edges that the graph consumers (validation, implementation, testing, migration) can query. `GraphBuilder.build(Path)` preserves the original graph-only API, while `GraphBuilder.buildWithDiagnostics(Path)` returns a `GraphBuildResult` containing the graph plus parser diagnostics.

`PomParser` runs first to produce the Maven/runtime context needed by other parsers. The remaining parsers run in parallel against parser-local graph fragments initialized from that base context. `GraphBuilder` then merges successful fragments in the configured parser order, so final graph construction is not coupled to future completion timing. Parser diagnostics include the parser name, scanned files, warnings, failures, timeout state, produced node count, and produced edge count.

**9 content parsers and 2 post-processors** are registered in `GraphBuilder`:

| Parser | What It Parses | Node Types | Execution Order |
|--------|---------------|------------|-----------------|
| `PomParser` | `pom.xml` | `MAVEN_ARTIFACT`, `CONFIG_PROPERTY` | First (synchronous, provides runtime detection and dependency allowlist) |
| `JavaGraphParser` | `.java` files | `CLASS`, `METHOD`, `FIELD`, `CONFIG_PROPERTY` | After PomParser |
| `GroovyGraphParser` | `.groovy` files | `CLASS`, `METHOD`, `FIELD` | After PomParser |
| `XmlRouteParser` | `.camel.xml` | `CAMEL_ROUTE`, `CAMEL_ENDPOINT`, `CAMEL_PROCESSOR` | After PomParser |
| `YamlRouteParser` | `.camel.yaml` | `CAMEL_ROUTE`, `CAMEL_ENDPOINT`, `CAMEL_PROCESSOR` | After PomParser |
| `ConfigParser` | `application.properties` | `CONFIG_PROPERTY` | After PomParser |
| `MuleXmlFlowParser` | MuleSoft XML configs (`*.xml` with `mulesoft.org/schema/mule` namespace) | `MULE_FLOW`, `MULE_SUB_FLOW`, `MULE_CONNECTOR`, `MULE_ENDPOINT`, `MULE_PROCESSOR`, `MULE_TRANSFORM`, `MULE_ERROR_HANDLER` | After PomParser |
| `DataWeaveParser` | `.dwl` files | `DATAWEAVE_SCRIPT` | After PomParser |
| `BizTalkParser` | BizTalk artifacts (`.odx`, `.btm`, `.btp`, binding `.xml`) | `BIZTALK_ORCHESTRATION`, `BIZTALK_SHAPE`, `BIZTALK_MAP`, `BIZTALK_FUNCTOID`, `BIZTALK_SCHEMA`, `BIZTALK_PIPELINE`, `BIZTALK_PIPELINE_COMPONENT`, `BIZTALK_PORT`, `BIZTALK_ADAPTER`, `BIZTALK_MESSAGE` | After PomParser (hybrid parser delegating to 4 internal StAX-based parsers) |
| `CrossLinker` | Graph topology | Creates cross-references and shortcuts | After all content parsers (post-processor) |
| `PropertyBindingParser` | `CONFIG_PROPERTY` values | Extracts bean/type/property references | After CrossLinker (post-processor) |

**All edge types:**

| Edge Type | Meaning | Created By |
|-----------|---------|------------|
| `EXTENDS` | A class extends another class | `JavaGraphParser`, `GroovyGraphParser` |
| `IMPLEMENTS` | A class implements an interface | `JavaGraphParser`, `GroovyGraphParser` |
| `DECLARES` | A class declares a field, method, or route | `JavaGraphParser`, `GroovyGraphParser` |
| `CALLS` | Reserved (not currently created by any parser) | -- |
| `USES_TYPE` | A field or constructor parameter references a type (DI-annotated) | `JavaGraphParser` |
| `ROUTES_FROM` | A route consumes from an endpoint | `XmlRouteParser`, `YamlRouteParser`, `JavaGraphParser`, `GroovyGraphParser` |
| `ROUTES_TO` | A route produces to an endpoint | `XmlRouteParser`, `YamlRouteParser`, `JavaGraphParser`, `GroovyGraphParser` |
| `PROCESSES` | A route contains a processor step | `XmlRouteParser`, `YamlRouteParser`, `JavaGraphParser`, `GroovyGraphParser` |
| `LINKS_TO` | A producer route is linked to a consumer route via direct/seda endpoints | `CrossLinker` |
| `DEPENDS_ON` | A Maven artifact depends on another artifact | `PomParser` |
| `USES_COMPONENT` | An endpoint uses a Maven artifact (scheme to camel-{scheme} convention) | `CrossLinker` |
| `CONFIGURES` | A config property configures an endpoint, datasource, or other target | `CrossLinker`, `PropertyBindingParser` |
| `INJECTS_INTO` | A bean is injected into a field or constructor | `JavaGraphParser` |
| `INSTANTIATES` | A CONFIG_PROPERTY instantiates a class via `#class:` syntax | `PropertyBindingParser` |
| `REFERENCES_BEAN` | A CONFIG_PROPERTY references a bean via `#bean:`, `#autowired`, or `#type:` syntax | `PropertyBindingParser` |
| `REFERENCES_PROPERTY` | A CONFIG_PROPERTY references another property via `#property:` syntax | `PropertyBindingParser` |
| `DEPENDS_ON_VIA_INTERFACE` | A class depends on another class via an interface | `CrossLinker` |
| `MULE_FLOW_CONTAINS` | A Mule flow or sub-flow contains a processor, transform, or endpoint | `MuleXmlFlowParser` |
| `MULE_CALLS_SUBFLOW` | A flow-ref element invokes a sub-flow by name | `MuleXmlFlowParser` |
| `MULE_USES_CONNECTOR` | A flow element uses a connector configuration | `MuleXmlFlowParser` |
| `MULE_REFERENCES_DWL` | A transform step references an external DataWeave script | `MuleXmlFlowParser` |
| `BIZTALK_ORCHESTRATION_CONTAINS` | Orchestration contains shape, port, or message | `BizTalkParser` |
| `BIZTALK_USES_MAP` | Transform shape uses a BizTalk map | `BizTalkParser` |
| `BIZTALK_USES_SCHEMA` | Message or map references an XSD schema | `BizTalkParser` |
| `BIZTALK_CALLS_ORCHESTRATION` | Call/start shape invokes another orchestration | `BizTalkParser` |
| `BIZTALK_PORT_BINDING` | Port uses adapter configuration | `BizTalkParser` |
| `BIZTALK_FUNCTOID_CHAIN` | Map contains functoid | `BizTalkParser` |
| `BIZTALK_PIPELINE_STAGE` | Pipeline contains component | `BizTalkParser` |

**Parser execution order:** PomParser runs first (synchronously) to ensure `MAVEN_ARTIFACT` nodes are available for runtime detection and dependency allowlist construction. All other parsers run in parallel after PomParser completes. CrossLinker runs after all content parsers finish, and PropertyBindingParser runs last to analyze the complete CONFIG_PROPERTY graph.

**Enhanced JavaGraphParser:** Detects dependency injection annotations (`@Inject`, `@Autowired`) and bean annotations (`@Component`, `@Service`, `@Repository`, `@Controller`, `@Named`, `@Singleton`, `@ApplicationScoped`, `@RequestScoped`) on fields, constructor parameters, and class declarations. Also detects `@Value` and `@ConfigProperty` for property references. Creates `USES_TYPE` edges for annotated fields/params, with POM-driven scope guard allowlist filtering framework types (Camel, Spring, Quarkus, Mule). Import-aware type resolution via `CompilationUnit.getImports()` matches short type names to fully-qualified class names.

**PropertyBindingParser:** Scans CONFIG_PROPERTY node values for Camel's PropertyBindingSupport syntax (`#class:`, `#bean:`, `#autowired`, `#type:`, `#property:`) and creates typed edges to referenced classes or beans. Convention-based detection identifies Spring Boot (`spring.datasource.*`) and Quarkus (`quarkus.datasource.*`, build-time properties) configuration patterns. Placeholder resolution (`{{key}}`) extracts property references from endpoint URIs.

**CrossLinker enhancements:** New `expandInterfaces()` pass creates `DEPENDS_ON_VIA_INTERFACE` shortcut edges across interface boundaries. This enables graph queries to traverse from interface consumers to all implementing classes without manual graph traversal. The `GraphQuery.expandWithInterfaces()` method performs BFS traversal that crosses interface boundaries, with direction-aware expansion (follows both `DEPENDS_ON` and `DEPENDS_ON_VIA_INTERFACE` edges). The interface-consumer expansion algorithm is inspired by the deterministic knowledge base (DKB) approach described in Chinthareddy, ["Reliable Graph-RAG for Codebases: AST-Derived Graphs vs LLM-Extracted Knowledge Graphs"](https://arxiv.org/pdf/2601.08773) (Jan 2026), which demonstrates that bidirectional AST-derived graph traversal with interface-boundary crossing achieves significantly higher correctness than vector-only RAG for multi-hop architectural queries on Java codebases.

**RuntimeDetector utility:** Shared utility that detects runtime environment (Spring Boot, Quarkus, Camel Main, Karaf) from `MAVEN_ARTIFACT` nodes. Graph construction uses it for runtime-specific property-binding analysis, and initialization persists the detected value to `.camel-kit/config.properties`. The migration-context command reads that persisted value instead of invoking `RuntimeDetector`.

**Auto-detection:** When the graph builder encounters an XML file, it checks for the `mulesoft.org/schema/mule` namespace. If present, the file is routed to `MuleXmlFlowParser` instead of `CamelRouteParser`. For BizTalk projects, the builder checks for the `schemas.microsoft.com/BizTalk` namespace and file extensions (`.odx`, `.btm`, `.btp`). If detected, files are routed to `BizTalkParser`. No explicit configuration is required -- if the project contains MuleSoft or BizTalk artifacts, they are parsed automatically.

**`--source-platform` flag:** For cases where auto-detection needs hinting (e.g., the project has non-standard file layouts), `camel-kit init --source-platform mulesoft` or `camel-kit init --source-platform biztalk` explicitly declares the source platform.

**ConfigParser expansion:** Now captures ALL application.properties keys, not just `camel.*` prefixes. This enables PropertyBindingParser to analyze datasource configurations, Quarkus build-time properties, and other framework-specific settings.

**DataWeave analysis:** The `DataWeaveParser` extracts version declarations, input/output content types, function definitions, and field access patterns from `.dwl` files. This helps the migration skill identify complex transformations that may need manual attention -- multi-function scripts, recursive field access, or format conversions that have no direct XSLT equivalent.

#### BizTalk Parser Architecture

The `BizTalkParser` is a hybrid parser that delegates to 4 internal StAX-based parsers (`BizTalkOdxParser`, `BizTalkBtmParser`, `BizTalkBtpParser`, `BizTalkBindingParser`) based on file extension and content. StAX was chosen over DOM to handle large orchestration files efficiently (streaming parse instead of full in-memory tree). The parser recognizes 38 orchestration shape element names (Receive, Send, Decide, Loop, Parallel, Call, Scope, etc.) and 45 functoid type mappings (string ops, math, looping, scripting, database lookup).

**Reference implementation:** The [BizTalkMigrationStarter](https://github.com/haroldcampos/BizTalkMigrationStarter) project (BizTalk → Azure Logic Apps) provided the reference parsing patterns. Its C# `BizTalkOrchestrationParser` (ODX text extraction + XPath), `BtmParser` (functoid/link resolution), `PipelineParser` (XmlSerializer deserialization), and `BindingSnapshot` (LINQ-to-XML) were translated to Java StAX equivalents for camel-kit.

### Graph Migration Context Command

The `graph migration-context` CLI command produces bounded structured JSON from the local property graph. It traverses in both directions from one route, including interface-boundary expansion, with depth 3 by default and a hard cap of 50 expanded nodes. It therefore supplies focused local context, not a complete project dependency map.

**Usage:** `camel-kit graph migration-context <routeId> [--depth N]`

**Output sections:**

| Section | Content | Source |
|---------|---------|--------|
| `route` | Requested route ID without the `route:` prefix | Command argument |
| `runtime` | `project.runtime`, or `unknown` if absent | `.camel-kit/config.properties` |
| `routes` | Reached route node IDs, source endpoints, and files | `CAMEL_ROUTE` nodes |
| `components` | Deduplicated endpoint schemes | Reached `CAMEL_ENDPOINT` nodes |
| `services` | Bean classes and bean names | Reached bean-marked `CLASS` nodes |
| `artifacts` | Group ID, artifact ID, and version | Reached `MAVEN_ARTIFACT` nodes |
| `properties` | Values and outgoing edge type/target pairs | Reached `CONFIG_PROPERTY` nodes |
| `warnings` | Nodes inferred rather than parsed | Reached nodes marked `synthetic=true` |

The command uses `GraphQuery.expandWithInterfaces()` to cross interface/implementation relationships within the traversal limits. It does not call the Knowledge MCP or derive documentation, CVE, compatibility, or migration-risk findings; those require separate lookups or analysis.

This graph-aware context complements file analysis with a bounded topology view of the reached graph nodes.

---

## 3. The Orchestration Model

### Pipeline

The end-to-end pipeline follows a strict phase progression:

```
brainstorm / migrate
       |
       v
   approved design package
       |
       v
     plan
       |
       v
 implementation plan
       |
       v
    execute
       |
       v
   artifacts + execution/verification reports
       |
       v
    validate
       |
       v
   validation report
```

The approved design is the only user approval gate in the chained pipeline; planning, execution, and validation then
continue automatically. For migration, that same gate covers the complete migration package, including its operational
runbook, without authorizing any of the runbook's operational actions.

The execute phase starts with an **environment probe** that validates the target environment before dispatching implementers. If architectural failures are found, a **re-plan loop** modifies affected flow design sections and re-executes (max 3 rounds).

Entry points diverge (`camel-brainstorm` for greenfield, `camel-migrate` for migration). Greenfield produces the active
design spec; migration also produces business requirements and an evidence-qualified `migration-analysis.md` before its
design spec, then registers `migration-runbook.md` as a direct child of that design. Migration strategy is classified
per independently switchable scope as `Incremental candidate`, `Single cutover required`, or
`Undetermined - evidence needed`, after every discovered ingress/root is reconciled into exactly one non-overlapping
scope. Both pipelines converge on the same approved design-spec contract, so `camel-plan` and `camel-execute` work
identically afterward; the plan does not consume the runbook.

### How camel-execute Dispatches Work

1. Read the ready implementation plan derived from the approved design, prefer the `yaml plan-metadata` task graph, and fall back to Markdown task
   parsing for older plans
2. **Catalog research** (Step 1.5): use a `catalog-researcher` sub-agent where supported to batch-verify all MCP catalog artifacts for the wave; inline targets perform the same checks in the active context.
3. For each task:
   - Dispatch an implementer sub-agent with full task context where supported; inline targets execute the same task in their gated session
   - **Adversarial Code Review** (Step 2b.5): use fresh Moderator and Critic Lane contexts where supported. Bob 1 runs the same critic lenses sequentially in its accumulated session and records the missing isolation. Hard cap: 3 cycles.
   - Run a **spec compliance review** -- does the output match the design spec? Use an isolated reviewer where supported.
   - If spec review passes, run a **code quality review** -- constitution compliance, security, anti-patterns. Use an isolated reviewer where supported.
   - If either reviewer finds critical issues, return to the implementer for fixes, then re-review
   - Mark task complete and immediately start the next task (no pause, no user confirmation)
4. After all tasks: run a **cross-cutting review** across all generated routes
5. Run the internal **verification phase** (`camel-verify`) within execute (build, Citrus tests, report), using a sub-agent where supported
6. Print the completion summary

After execute completes, the pipeline continues to **validation** (`camel-validate`) as Phase 4 — the final static quality gate. Validation reports findings without applying fixes. Pipeline-scoped runs write `docs/camel-kit/<PIPELINE_ID>/validation-report.md`; project-scoped standalone runs with no pipeline write `docs/validation-report-YYYY-MM-DD_HH-mm.md`.

For subagent-capable targets, reviews, verification, and catalog lookups run in isolated contexts and only structured reports flow back to the orchestrator. Bob 1 legacy keeps this work in one session and relies on mode gates instead.

### Agent-Specific Execution

The dispatch model varies by AI agent:

- **Claude Code** -- dispatches fresh sub-agents per task. Each sub-agent runs in isolated context with no cross-contamination between tasks.
- **IBM Bob 1 legacy** -- switches between custom modes and monolithic gate files with scoped tool permissions per mode.
- **IBM Bob 2** -- uses native `spawn_subagent` with factual-discovery `explore` plus generated `camel-worker` and read/MCP-only `camel-reviewer` presets, while retaining Bob custom modes for parent-task tool restrictions.
- **Gemini CLI, Qwen, OpenCode** -- use their native agent/delegation models with shared Camel-Kit skills and traits.

### The Design Spec Contract

Both `camel-brainstorm` (greenfield) and `camel-migrate` (migration) produce the active pipeline design spec under
`docs/camel-kit/<PIPELINE_ID>/design-spec.md`. Migration first materializes `business-requirements.md` and
`migration-analysis.md`; every unresolved behavioral-risk `MIG-###` and source-retirement `SRC-###` finding becomes a
scope constraint, validation obligation, or unresolved decision. The business requirements add `Migration Strategy`,
and the design adds `Migration Strategy Constraints`: incomplete or conflicting evidence remains
`Undetermined - evidence needed`; incremental or strangler guidance requires current confirmation of an existing
controllable traffic seam plus confirmed target design constraints and pre-cutover validation obligations. The
classification establishes design candidacy, not cutover readiness. `Single cutover required` needs a closed,
operator-confirmed ingress/control inventory plus complete confirmed evidence that every seam candidate inside named
validated source and operational-control boundaries is absent or unsafe. Anything unconfirmed or outside those
boundaries remains undetermined.

After the final design and Camel Main eligibility checks, `camel-migrate` generates `migration-runbook.md` from the
validated package, current operational evidence, and explicit operator decisions, then registers it as a direct child of
the design spec. The runbook preserves every strategy classification and referenced `MIG-###`/`SRC-###` finding without
promoting design candidacy to cutover readiness; an undetermined scope receives no concrete cutover procedure. It turns
only evidenced facts and confirmed design constraints into deployment, cutover, operational validation, rollback,
reconciliation, soak, and retirement decision steps. A missing operational fact is written exactly as
`Unknown — operator decision required: <missing fact>`; commands, endpoints, thresholds, durations, contacts, owners,
environment values, and credential values are never invented. Credential material is not copied into the artifact;
only validated secret references may be recorded.

One package approval covers the business requirements, analysis, design, and runbook for progression into planning. It
does not authorize source exclusions, infrastructure provisioning, deployment, cutover, operating a traffic seam,
traffic switching, rollback, message or data reconciliation, or source retirement. Retirement remains a separate,
named operator decision after operational validation, reconciliation, and soak have passed; every operational action
still requires the named operator's separate execution-time authorization. `camel-plan` continues to consume only the
design spec, so the implementation pipeline converges after approval.

---

## 4. DataMapper Pipeline

The DataMapper pipeline handles data transformation between formats (JSON, XML) within Camel routes. It supports two transformation engines with automatic selection.

### Engines

- **XSLT** (via `camel-xslt-saxon`) -- external `.xsl` files with Kaoto IDE visual editor support
- **Groovy** (inline in YAML) -- inline scripts in `transform:` steps, no external files

### Engine Selection Rules

Engine selection happens in the canonicalize stage (`skills/shared/datamapper-canonicalize.md`, Step 0). Rules are evaluated in order:

| # | Condition | Engine | Reason |
|---|-----------|--------|--------|
| 1 | Both source-schema AND target-schema are `"none"` | **Groovy** | No schemas to drive XSLT structure |
| 2 | Field count < 20 | **Groovy** | Small mapping -- inline script is simpler and more readable |
| 3 | Field count >= 20 AND at least one schema exists | **XSLT** | Large mapping with schema -- XSLT + Kaoto IDE visual editor |

### Canonicalize Stage

The canonicalize guide (`skills/shared/datamapper-canonicalize.md`) is shared between the design interview (`datamapper-interview.md`) and migration (`datamapper-migrate.md`). After deciding the engine, it enriches the semantic field mappings with engine-specific data:

- **XSLT path:** computes Source XPaths, Target Elements, selects pattern and approach
- **Groovy path:** produces a simplified semantic-only mapping table

### XSLT Path

4 patterns based on format pair:

| Source | Target | Pattern |
|--------|--------|---------|
| XML | XML | A -- XML to XML |
| JSON | JSON | B -- JSON to JSON |
| JSON | XML | C -- JSON to XML |
| XML | JSON | D -- XML to JSON |

Each pattern has 2 approaches:
- **Approach A** (`useJsonBody`) -- JSON body passed via Camel's JSON-to-XML auto-conversion
- **Approach B** (header param) -- JSON body passed as an XSLT parameter via message header

Pre-computed Source XPaths and Target Elements are stored in the design spec and used verbatim during implementation. The XSLT file is generated externally as `kaoto-datamapper-{id}.xsl` with a companion `.kaoto` metadata file for Kaoto IDE visual editing.

### Groovy Path

Inline scripts in YAML `transform:` steps. 4 format pairs (JSON to JSON, XML to JSON, JSON to XML, XML to XML). No external files, no `.kaoto` metadata.

### Validation Routing

The validation guide (`datamapper-validation.md`) reads the `Transformation Engine` field from the design spec and routes to the appropriate validation guide: `datamapper-groovy.md` for Groovy, or `datamapper-approach-a.md` / `datamapper-approach-b.md` for XSLT.

### Artifacts Comparison

| Aspect | XSLT Path | Groovy Path |
|--------|-----------|-------------|
| External file | `kaoto-datamapper-{id}.xsl` | None (inline in YAML) |
| YAML step | `xslt-saxon:` URI with parameters | `transform:` with `groovy:` expression |
| `.kaoto` metadata | Required (Kaoto IDE visual editor) | Skipped (no IDE support) |
| Maven dep (Spring Boot) | `camel-xslt-saxon-starter` | `camel-groovy-starter` |
| Maven dep (Quarkus) | `camel-quarkus-xslt-saxon` | `camel-quarkus-groovy` |
| Mapping columns | 8 (incl. Source XPath, Target Element) | 6 (semantic only) |
| Kaoto IDE editing | Visual DataMapper editor | Edit YAML directly |

---

## 5. Agent Templates

### Template Directory

`camel-kit-core/src/main/resources/templates/{agent}/`

### Agent Capability Registry

Built-in agent metadata lives in `camel-kit-core/src/main/resources/agents/registry/{agent}.yaml`.
`AgentRegistry` loads these descriptors at startup and maps them into the public `AgentConfig` model used by
commands and generators.

Each descriptor defines the agent id, display name, skills directory, whether it generates command stubs, optional
command directory and command syntax, MCP config path, template and format, MCP server container key, description,
generator strategy, dispatch template path, templates installed by the agent-specific generator, and whether the
agent supports sub-agents or traits. Missing required fields, duplicate ids, unsupported generator strategies, or
malformed YAML fail during registry loading with a descriptor-specific error.

### What `camel-kit init` Generates

| Agent | Template Dir | Instruction File | MCP Config | Skills Location |
|-------|-------------|-----------------|------------|-----------------|
| Claude Code | `templates/claude/` + shared `agents/` | `CLAUDE.md` + `.claude/camel-kit-personas/` | `.mcp.json` | `.claude/skills/` |
| IBM Bob 1 legacy | `templates/bob/` | `custom_modes.yaml` + rules + gates | `.bob/mcp.json` | `.bob/skills/` |
| IBM Bob 2 | `templates/bob2/` + shared `agents/` | modes + rules + scoped agents + role personas + shared skills | `.bob/mcp.json` | `.bob/skills/` |
| Gemini CLI | `templates/gemini/` + shared `agents/` | `GEMINI.md` + `@file.md` imports + policies + `.gemini/camel-kit-personas/` | `.gemini/settings.json` | `.gemini/skills/` |
| OpenAI Codex CLI | `templates/codex/` + shared `agents/` | `AGENTS.md` + `.codex/agents/*.toml` + `.agents/camel-kit-personas/` | `.codex/config.toml` | `.agents/skills/` |
| GitHub Copilot CLI | `templates/copilot/` + shared `agents/` | `.github/copilot-instructions.md` + `.github/agents/` + hooks + `.github/camel-kit-personas/` | `.github/mcp.json` | `.github/skills/` |
| Pi | `templates/pi/` + shared `agents/` | `AGENTS.md` + `.pi/prompts/` + guard extension + `.pi/camel-kit-personas/` | `.mcp.json` | `.pi/skills/` |
| Qwen | `templates/qwen/` + shared `agents/` | `QWEN.md` + bounded leaves + `.qwen/camel-kit-personas/` | `.qwen/settings.json` | `.qwen/skills/` |
| OpenCode | `templates/opencode/` + shared `agents/` | `AGENTS.md` + permission-based agents + `.opencode/camel-kit-personas/` | `opencode.json` (default) | `.opencode/skills/` |

### Resource Consistency Contract

Skills, generated instruction files, MCP config templates, and active docs are runtime contract surfaces. They are tested by `ResourceConsistencyTest` in `camel-kit-core` so stale contract tokens fail the build before they ship.

The active scan covers `camel-kit-core/src/main/resources/skills`, `camel-kit-core/src/main/resources/templates`, top-level Markdown files under `docs/`, `README.md`, and `CONTRIBUTING.md`. These files must reference current command names, current config files, current graph access patterns, current Knowledge MCP tool names, and current Iron Law counts.

Docs subdirectories are intentionally out of scope; this keeps ignored archives such as `docs/plans/` and `docs/superpowers/`, image assets, and generated planning material out of the contract check.

`ShippedAssetStructureTest` covers structural coherence for shipped assets. It verifies that workflow manifest skills have `skills/<name>/SKILL.md`, guide tables in `SKILL.md` point to existing bundled files, shared guide references in shipped skills and templates resolve, every supported agent descriptor has dispatch and MCP config templates, descriptor template sources exist, trait files target existing skills or guides, every shipped trait appears in generated output for its production agent generator, generated command files match manifest command names, generated command skill references resolve to copied skills, and generated MCP configs parse as their declared JSON or TOML format with the expected server containers.

Historical release notes, old planning material, and archived ADR-style documents are outside the active contract unless they are copied into generated projects or used as live instructions. Keep historical context in those files as history; do not exclude an active shipped instruction just because it is inconvenient to update.

### The Equalization Layer

Most supported agents receive the shared skills (markdown instruction files), with the template layer adapting them to each agent's conventions. Bob 1 legacy is the exception: because it cannot chain skill references, its registry installs seven self-contained monolithic gate variants plus shared rules. The equalization contract keeps behavior and outputs aligned even though Bob 1 does not read the same phase `SKILL.md` files.

**What equalization covers:**
- Workflow content (shared skills for most agents, corresponding monolithic gates for Bob 1)
- Iron Laws (embedded in every agent's instruction file)
- Constitution rules (enforced identically)
- MCP tool calls (same tools, same parameters)
- Output formats (same YAML routes, properties, test files)

**What equalization does NOT cover:**
- Dispatch mechanism (sub-agents vs. modes vs. inline)
- Tool restriction model (each agent's permission system is different)
- File reading patterns (context isolation varies)
- Parallelization strategy (parallel sub-agent behavior differs by agent)
- Configuration format (YAML modes, TOML policies, markdown frontmatter)

### Agent Traits

Traits are agent-specific instruction fragments that are appended to generated skill files during `camel-kit init`. They bridge the gap between the equalized workflow contract and the per-agent template layer (different dispatch models).

**Location:** `camel-kit-core/src/main/resources/templates/traits/{agent}/`

**How it works:** During `camel-kit init --ai {agent}`, `TraitApplicator` applies trait files for the selected agent to the corresponding generated skill files. SKILL.md-level traits are applied for skills in the workflow manifest. Guide-level traits are discovered from the actual `.append.md` files under `traits/{agent}/{skill-name}/`, so adding a new guide trait does not require registering the guide name in Java code. Idempotent HTML comment sentinels (`<!-- TRAIT:{agent} -->` / `<!-- /TRAIT:{agent} -->`) prevent duplicate application on re-init.

**Two granularity levels:**

| Level | Path Pattern | Appended To |
|-------|-------------|-------------|
| SKILL.md-level (strategy) | `traits/{agent}/{skill-name}.append.md` | `{skills-dir}/{skill-name}/SKILL.md` |
| Guide-level (tactics) | `traits/{agent}/{skill-name}/{guide-name}.append.md` | `{skills-dir}/{skill-name}/guides/{guide-name}.md` |

**Example:** `traits/claude/camel-execute.append.md` appends Claude-specific instructions to `camel-execute/SKILL.md` -- parallel sub-agent dispatch via the `Agent` tool, worktree isolation via `EnterWorktree`, build health monitoring via `CronCreate`. The same skill on Gemini gets different trait content: named agent delegation, TOML policy guidance, batch context loading via `read_many_files`.

**What traits contain:** Agent-specific tool usage, dispatch strategies, state persistence mechanisms, and execution optimizations. Each trait is written for the specific agent's capabilities -- Claude traits reference `Agent`, `ScheduleWakeup`, `TaskCreate`; Gemini traits reference `save_memory`, `read_many_files`; Bob traits reference `switch_mode`, `insert_content`.

**Bob ordering:** IBM Project Bob replaces several generated `SKILL.md` files with monolithic gate templates. Bob traits are applied after that replacement, so Bob-specific trait content is appended to the final gate-backed skill files rather than being overwritten. Bob guide-level traits still apply to the copied guide files.

### Iron Laws

The six shared Iron Laws from `skills/shared/iron-laws.md` are embedded in or referenced by each agent's instruction file:

1. **MCP Catalog Verification** -- every component, EIP, dataformat, and language must be verified via MCP before being written to any design spec or YAML file
2. **Constitution Compliance** -- every generated route must pass all 8 constitution rules (incorporates and enforces the constitution)
3. **No Code Without Design Approval and an Existing Plan** -- never generate implementation artifacts before the user has approved the design spec and a task-based implementation plan exists
4. **Spec Compliance Before Quality** -- always run spec compliance review before code quality review; wrong order wastes effort
5. **Adversarial Code Review** -- generated code must pass the adversarial review gate before spec compliance and quality review
6. **Surgical Changes** -- implementation tasks must touch only what they were asked to touch

### Sub-agent-Driven Execution

The `/camel-execute` pipeline relies on dispatching discrete units of work to isolated agents. The design principle: the agent that writes the code should never be the same agent that reviews it, and each task should start from a clean context with no residual assumptions from previous tasks.

Most supported agents use native **sub-agent dispatch** or custom-agent isolation:

- **Claude Code** -- uses the `Agent` tool to spawn fresh sub-agents per task. Each sub-agent receives the task text, relevant design spec section, guide file paths, and MCP parameters. Before implementation, a `catalog-researcher` sub-agent batch-verifies all MCP catalog artifacts (research isolation). After implementation, an Adversarial Code Review dispatches parallel Critic Lanes (Route Architecture, Security, Performance, Boundary Compliance, Behavioral Equivalence) via a Moderator sub-agent, then a spec-compliance reviewer sub-agent checks the design spec, then a code-quality reviewer sub-agent checks constitution compliance. Claude uniquely supports **parallel dispatch**: `camel-kit plan analyze` groups tasks into waves using structured plan metadata (`dependsOn`, file overlap, and logical `provides`/`consumes` resources such as endpoints, routes, properties, schemas, test data, beans, external services, and route contracts), then independent tasks are dispatched simultaneously to multiple sub-agents.

- **Gemini CLI** -- dispatches via a unified `invoke_subagent` tool to 6 specialized sub-agents. The scheduler natively supports **parallel tool execution** via `Promise.all()` (default-parallel). However, sub-agents cannot invoke other sub-agents (hardcoded `Kind.Agent` filter), so `/camel-execute` runs in the **main agent context** where it can dispatch to all sub-agents. Within-wave parallelism is achieved through the scheduler batching multiple `invoke_subagent` calls.

- **Qwen** -- keeps brainstorm, plan, migrate, execute, validate, and start orchestration in the primary session so
  questions, approval, arguments, and handoffs remain available. It dispatches four bounded leaves with explicit
  `subagent_type` values for implementation, read-only research/review, testing, and validation. Gating calls set
  `run_in_background: false`; optional top-level factual forks use `subagent_type: "fork"`. Generated leaves cannot
  redispatch, use explicit `approvalMode: default`, and receive complete roles from `.qwen/camel-kit-personas/`.

- **OpenCode** -- 9 generated agents with granular permissions. The `/camel-execute` command selects a primary executor in the current session; it can dispatch an explicit allowlist of implementer, migrator, bounded planner, researcher, reviewer, and tester leaves. Every leaf denies further delegation, making the generated topology one level deep, and read-only research/review roles keep implementation separate from its gates. Other interactive phase commands run in the primary session. Agent-owned `steps` limits include implementer 50, reviewer 50, and executor 100. The generated config uses supported top-level namespace patterns to prompt before Camel, Knowledge, or Citrus MCP tool calls.

- **GitHub Copilot CLI** -- project skills live under `.github/skills/` and custom agents live under `.github/agents/`. Camel-Kit generates planner, implementer, tester, validator, migrator, catalog researcher, and security reviewer agents with Copilot tool aliases and MCP server prefixes. Internal guide skills copied for custom-agent use are marked `user-invocable: false` and `disable-model-invocation: true` using Copilot-readable metadata. MCP servers are committed in `.github/mcp.json` using Copilot's `tools` schema. Repository hooks under `.github/hooks/` provide a lightweight safety harness for destructive shell commands while keeping Copilot's normal permission prompts active.

- **OpenAI Codex CLI** -- native project skills live under `.agents/skills/`, project instructions live in `AGENTS.md`, and seven custom roles live under `.codex/agents/`. Codex receives no command-stub directory; `$camel-start` and `/skills` are the entry points. The generated `.codex/config.toml` preserves unrelated valid project settings while adding three version-pinned MCP servers with exact `enabled_tools` lists and prompt approval. Repository trust controls whether project config loads, while the user's sandbox and approval settings remain authoritative. The parent Codex agent owns orchestration, dispatches independent wave work in parallel when supported, and falls back to inline execution when delegation is unavailable.

- **Pi** -- project skills live under `.pi/skills/` and command stubs are generated as `.pi/prompts/` prompt templates. Pi reads `AGENTS.md` natively. MCP servers are committed in `.mcp.json` for `pi-mcp-adapter` using the adapter's `directTools` allowlist schema. Internal guide skills are marked `user-invocable: false` and `disable-model-invocation: true`; Pi currently honors only the model-invocation flag. A static `.pi/extensions/camel-kit-guard.ts` extension interprets `.pi/camel-kit-guard-policy.json` to block destructive or secret-sensitive tool calls. Pi has no native subagents, so custom-agent parity is deferred.

**IBM Bob 2** uses Bob's native `spawn_subagent` tool. Camel-Kit exposes this as `--ai bob2`, but generated project files still live under `.bob/`: commands, skills, scoped agent presets, role personas, modes, rules, and MCP configuration.

- `explore` is reserved for factual source search, inventory, and discovery. Its built-in raw prompt is not used for
  recommendations or verdicts.
- Generated `.bob/agents/camel-worker.md` handles implementation, test generation, fixes, and verification from broad
  execute/debug orchestration modes with read, edit, execute, MCP, and skill groups. Standalone restricted implement
  and test modes keep mutations inline instead of dispatching that broader preset; test retains its path-scoped edit
  restriction.
- Generated `.bob/agents/camel-reviewer.md` handles the Catalog Researcher, Knowledge Researcher, ACR phases, spec/quality review, and
  validation reasoning with only read and MCP groups, enforcing non-mutation at the tool layer.
- `.bob/personas/*.md` contains the complete catalog, worker, Moderator, critic, spec, quality, and supporting role
  contracts. The parent includes the selected full text in each scoped-agent prompt; keeping these files outside
  `.bob/agents/` prevents Bob from registering them as separate presets with unintended capabilities.
- Multiple `spawn_subagent` calls in one parent turn run in parallel, so `/camel-execute` dispatches all independent tasks in the current `camel-kit plan analyze` wave together.
- The parent Bob task remains the orchestrator. Subagents return summaries and must not spawn subagents.
- Bob 2 skills are the shared Camel-Kit `SKILL.md` files with Bob 2 traits appended; Bob 2 does not replace them with monolithic gates.

**IBM Bob 1 legacy (`--ai bob`)** uses a fundamentally different architecture -- the **B+A (Behavior + Advanced) hybrid with mode switching**:

1. Each gate-backed skill starts in **Advanced mode** (unrestricted), allowing the agent to read all skill files and project context
2. The first instruction in the gate file switches to a **restricted custom mode** (e.g., `camel-brainstorm-mode`, `camel-implement-mode`) with scoped tool permissions
3. The mode's tool group constrains what the AI can do for the remainder of that skill invocation

This means Bob 1 cannot isolate tasks into separate context windows or use independent reviewer agents. Its edit tool
is platform-scoped by mode, while its broad command group is constrained by the generated instructions. During design,
`camel-brainstorm-mode` grants `read`, `mcp`, `browser`, scoped edits for design Markdown plus
`.camel-kit/config.properties`, `.camel-kit/pipeline.json`, and `.camel-kit/project-snapshot.md`; commands are limited by
instructions to pipeline metadata and read-only graph operations, and must not mutate application code.

Bob 1 also requires **seven monolithic gate files** (one for each replaced skill) that inline complete orchestration logic, because it cannot chain skill references across mode switches the way sub-agent-based agents load skills into fresh contexts. Its execute gate runs adversarial critic lenses sequentially in the accumulated session and explicitly lacks fresh-context or parallel critic isolation.

The trade-off table:

| Design Dimension | Sub-agent Dispatch | Mode Switching (Bob 1 Legacy) |
|-----------------|-------------------|------------------------------|
| Context isolation | Per-task (fresh sub-agent) | Per-session (accumulated) |
| Reviewer independence | Separate sub-agent | Same session self-reviews |
| Tool restriction mechanism | Instruction-based / tool whitelists / policies | Platform-enforced mode tool groups |
| Parallel execution | Claude (graph topology), Bob 2 (`spawn_subagent` in one turn), Gemini (scheduler `Promise.all()`), Codex (independent waves), Qwen (primary-session same-turn leaves and detached forks), OpenCode (LLM-level) | Not possible |
| Skill loading | Loaded into sub-agent context on dispatch | Inlined in monolithic gate files |
| Template complexity | 3-12 files per agent | 17+ files (gates + rules + modes) |
| Failure isolation | Sub-agent failure doesn't affect other tasks | Phase failure affects entire session |

### Per-Agent Summary

| Agent | Dispatch Model | Key Differentiator |
|-------|---------------|-------------------|
| Claude Code | Parallel sub-agent dispatch | Route graph topology, research isolation, parallel fan-out, adversarial code review |
| IBM Bob 1 legacy | B+A hybrid with custom modes | Monolithic gate files, 3 checkpoint types |
| IBM Bob 2 | Native `spawn_subagent` plus custom modes | Capability-scoped `explore`/`camel-worker`/`camel-reviewer` dispatch, parallel same-turn calls, shared skills |
| Gemini CLI | `invoke_subagent` + parallel scheduler | Default-parallel `Promise.all()`, TOML policy, MCP wildcards, A2A remote agents |
| OpenAI Codex CLI | Native custom-agent dispatch | `.agents/skills`, `.codex/agents`, prompt-gated MCP tools, inherited sandbox and approvals |
| GitHub Copilot CLI | Project skills + custom agents + hooks | `.github/skills`, `.github/agents`, `.github/mcp.json`, safety hooks |
| Pi | Project skills + prompt templates + guard extension | `.pi/skills`, `.pi/prompts`, `.mcp.json`, `pi-mcp-adapter`, trust-gated resources |
| Qwen | Primary workflow + explicit bounded leaves/forks | Four scoped leaves, preserved interaction/handoffs, detached context-sharing forks |
| OpenCode | Primary executor + `task` leaf sessions | Glob-pattern permissions and explicit leaf allowlist |

For full per-agent deep dives (template files, tool restriction models, configuration examples, unique capabilities), see **[Agent Architectures](agent-architectures.md)**.

### Adding a New Agent

To add support for a new AI coding assistant:

1. Add `agents/registry/{agent-name}.yaml` with the skills path, optional command-stub contract, MCP config path and
   format, generator strategy, dispatch template, and capabilities.
2. Create a template directory: `templates/{agent-name}/`
3. Implement `{Agent}Generator extends DefaultGenerator` in `io.github.luigidemasi.camelkit.generator`
4. Register the generator strategy in `AgentGeneratorStrategy` and `AgentGeneratorFactory`
5. Generate the agent's instruction file with embedded iron laws and skill references
6. Map pipeline phases to the agent's native dispatch mechanism (modes, sub-agents, permissions, etc.)
7. Add MCP configuration for the agent's MCP config format
8. Verify `camel-kit doctor` can validate a generated workspace using the descriptor MCP path.
9. Update ADRs and user documentation when the new agent changes architecture or user-visible behavior.
10. Write tests following existing patterns (registry, factory, generated structure, doctor, and key content markers)

The `DefaultGenerator` orchestrates shared generation services such as `CommandStubGenerator`, `SkillResourceInstaller`, `TraitApplicator`, and `McpConfigGenerator`. Each agent-specific generator adds or overrides template generation to produce the agent's native format.

---

## 6. MCP Integration

### Camel JBang MCP

The configured Camel MCP server (`camel-jbang-mcp`, via `camel.mcp.version`) exposes the Camel catalog, route validation,
diagnostics, migration, and scaffolding tools. Camel-Kit auto-allows the read-only and generation helpers used by
the skills, and leaves runtime mutation/control tools out of the generated allowlist.

#### 1. Catalog Exploration

| Tool Name | Purpose |
|-----------|---------|
| `camel_catalog_components` | List Camel components with filtering by name, label, runtime |
| `camel_catalog_component_doc` | Get comprehensive component documentation |
| `camel_catalog_component_maven` | Get Maven coordinates for a component |
| `camel_catalog_dataformats` | List data formats (JSON, XML, CSV, etc.) |
| `camel_catalog_dataformat_doc` | Get data format configuration options |
| `camel_catalog_languages` | List expression languages (Simple, JsonPath, XPath, JQ) |
| `camel_catalog_language_doc` | Get expression language documentation |
| `camel_catalog_eips` | List Enterprise Integration Patterns |
| `camel_catalog_eip_doc` | Get EIP documentation and configuration |
| `camel_catalog_kamelets` | List available Kamelets with filtering |
| `camel_catalog_kamelet_doc` | Get Kamelet documentation and dependencies |
| `camel_catalog_examples` | List Camel examples |
| `camel_catalog_example_file` | Read a file from a Camel example |

#### 2. Route Validation, Security, And Tests

| Tool Name | Purpose |
|-----------|---------|
| `camel_validate_route` | Validate one explicit endpoint URI against the bound catalog; route-content extraction is YAML-only and best-effort, so shipped guides call it once per statically extracted endpoint |
| `camel_validate_yaml_dsl` | Validate Camel YAML DSL syntax |
| `camel_transform_route` | Convert routes between YAML and XML formats |
| `camel_route_context` | Extract components and EIPs from route (YAML/XML/Java) |
| `camel_route_harden_context` | Provide supplemental candidate evidence for route-hardening concerns |
| `camel_route_test_scaffold` | Generate a JUnit 5 Camel route test scaffold |

#### 3. Diagnostics And Configuration

| Tool Name | Purpose |
|-----------|---------|
| `camel_component_properties` | Inspect component property metadata |
| `camel_configuration_validate` | Validate Camel configuration properties |
| `camel_dependency_check` | Check dependencies for common Camel issues |
| `camel_error_diagnose` | Diagnose Camel errors |
| `camel_properties_translate` | Translate properties between Camel runtimes |
| `camel_version_list` | List Camel versions with LTS status and JDK requirements |

#### 4. Migration And OpenAPI

| Tool Name | Purpose |
|-----------|---------|
| `camel_migration_analyze` | Analyze a project for migration concerns |
| `camel_migration_compatibility` | Check migration compatibility |
| `camel_migration_recipes` | Find migration recipes |
| `camel_migration_guide_search` | Search Camel migration guidance |
| `camel_migration_wildfly_karaf` | Analyze WildFly/Karaf migration concerns |
| `camel_openapi_validate` | Validate OpenAPI input for Camel use |
| `camel_openapi_scaffold` | Generate Camel OpenAPI route scaffolding |
| `camel_openapi_mock_guidance` | Provide OpenAPI mock guidance |

### Knowledge Layer MCP

Separate from Camel JBang MCP, the knowledge layer runs from the `camel-kit-knowledge` repository and exposes
`camel_docs_*` documentation search tools.

| Tool Name | Purpose |
|-----------|---------|
| `camel_docs_search` | General hybrid search across Apache Camel documentation |
| `camel_docs_component_info` | Component documentation lookup with usage, options, and related CVEs |
| `camel_docs_cve_search` | CVE search by ID, component, severity, or version |
| `camel_docs_release_info` | Release notes by version |
| `camel_docs_jira_lookup` | CAMEL-* JIRA issue details and fix version |

### Tool Usage by Skill

| Skill | Tools Used | Count |
|-------|------------|-------|
| `camel-brainstorm` | Catalog discovery/detail tools, `camel_version_list`, `camel_docs_search` | varies |
| `camel-migrate` | Catalog, migration, and knowledge tools | varies |
| `camel-implement` | Catalog detail tools, route context, validation, transformation, test scaffold | varies |
| `camel-validate` | Static route quality, security, dependency/config checks, and reporting | varies |
| `camel-test` | Route validation, route context, hardening analysis, component metadata, Citrus catalog/schema/docs | varies |
| `camel-knowledge` | `camel_docs_search`, `camel_docs_component_info`, `camel_docs_cve_search`, `camel_docs_release_info`, `camel_docs_jira_lookup` | 5 |

### Token Savings

| Approach | Token Cost |
|----------|------------|
| Without skills or MCP | ~500k tokens (all component catalogs) |
| With skills only | ~10k tokens (components loaded on demand) |
| With skills + MCP | ~6k tokens (skills for impl, MCP for queries) |
| Overall savings | 99% reduction |

### Graceful Degradation

If the MCP server is not available, skills fall back to local component data and manual analysis. The pipeline continues with degraded functionality rather than failing outright.

---

## 7. Verification Pipeline

The verification pipeline (`camel-verify`) is a 3-phase feedback loop that builds and tests the generated application using Citrus integration tests. It runs internally within `camel-execute`—in an isolated subagent when supported, or inline for single-conversation targets—not as a standalone pipeline stage. After verification completes inside execute, `camel-validate` runs as the final pipeline stage.

### Phases

1. **Build Verification** -- compile Spring Boot or Quarkus projects with Maven and classify build errors; Camel Main runs its startup smoke test instead
2. **Test Verification** -- run Citrus YAML integration tests via `camel test run`, classify and fix test failures. Citrus tests are self-contained: Testcontainers start external services, `camel:jbang:run` starts the Camel integration, send/receive actions validate behavior.
3. **Report** -- structured summary of all phases, fixes applied, and issues found

Each phase has an independent iteration budget of **max 15 attempts**. On each iteration, errors are classified and routed to the appropriate fix strategy.

### Environment Probe

Before the verify loop runs, `camel-execute` performs an **environment probe** as its first step. The probe generates a
runtime-appropriate throwaway skeleton and checks dependency resolution for Maven-based runtimes, Docker when available
for full test verification, and required runtime startup. Non-applicable or unavailable checks are recorded as skipped.
Failures in applicable checks are classified as **mechanical** (auto-fix and re-probe) or **architectural** (trigger
re-plan loop). Mechanical failures route to the automated self-repair path (fix and re-probe without entering the
re-plan loop). Architectural failures trigger the re-plan loop, which modifies affected flow design sections and
re-executes.

### Error Taxonomy

Error patterns organized by fix target:

| Category | Examples | Fix Target |
|----------|----------|------------|
| Missing dependency | `ClassNotFoundException` | Self-repair (pom.xml) |
| Third-party dependency | `cannot find symbol` | Self-repair |
| Version incompatibility | `NoSuchMethodError` | Self-repair (align BOM) |
| Build tool | `Unknown lifecycle phase` | Escalate to user |
| Route creation | `FailedToCreateRouteException` | `camel-implement` |
| Unknown component | `NoSuchEndpointException` | `camel-implement` |
| Wrong endpoint options | `ResolveEndpointFailedException` | `camel-validate` static diagnosis/report, then implementation correction |
| Missing bean | `NoSuchBeanException` | `camel-implement` |
| Injection failure | `UnsatisfiedDependencyException` | `camel-implement` |
| External service | `Connection refused` | Self-repair (Docker) |
| Quarkus augmentation | `io.quarkus.builder.BuildException` | Escalate to user |
| Expression failure | `ExpressionEvaluationException` | `camel-implement` |
| Type conversion | `TypeConversionException` | `camel-implement` |
| XSLT transformation | `XPathException`, `TransformerException` | `camel-implement` |
| Citrus assertion mismatch | `CitrusRuntimeException`, validation failure | `camel-implement` |
| Citrus test timeout | `ActionTimeoutException` | Self-repair or `camel-implement` |
| Testcontainer failure | `ContainerLaunchException` | Self-repair |
| Test YAML error | Parse/schema error in test file | `camel-test` |
| Test logic error | Incorrect test assertion | `camel-test` |

### Fix Routing

Errors route to one of six handling destinations:

1. **Self-repair** -- fix pom.xml, application.properties, or test configuration directly
2. **camel-validate** -- run static endpoint diagnosis and report the finding; return corrections to implementation handling
3. **camel-implement** -- route to implementation skill for route logic fixes
4. **camel-test** -- route to test skill for test re-generation (when the test is wrong, not the code)
5. **re-plan** -- trigger the re-plan loop for architectural failures (modifies affected flow design sections, max 3 rounds)
6. **Escalate to user** -- when the error is outside the pipeline's control

`re-plan` is not a separate error category -- it is a promotion destination. When the same error class persists after failed fix attempts, the error promotes to re-plan via the two-tier promotion model (Tier 1: MCP confirms structural after 1 attempt; Tier 2: 3 failed attempts on same error class).

### Re-Plan Promotion

When fix attempts fail persistently, errors promote to re-planning via a two-tier model:

- **Tier 1 (immediate):** After 1 failed fix, MCP catalog confirms the failure is structural (component doesn't exist for this runtime). Triggers re-plan immediately.
- **Tier 2 (progressive):** After 3 failed fix attempts on the same error class. Triggers re-plan.

The re-plan loop modifies affected flow design sections only (never the business requirements), max 3 rounds, with short-circuit on same failure class.

---

## 8. Pipeline Infrastructure

The pipeline uses file-based handoff for session resilience and CI/CD integration. Each pipeline run creates a directory under `docs/camel-kit/` with a sequential ID.

### Pipeline ID

Format: `NNN-slug` (e.g., `001-order-processing`). Generated by the `nextId` CLI command:

```bash
camel-kit nextId <slug>
```

The Camel JBang plugin exposes the same command as `camel kit nextId <slug>`.

This scans `docs/camel-kit/` for existing directories, finds the max ID, and creates `docs/camel-kit/<NNN+1>-<slug>/`.

### Directory Structure

```text
docs/camel-kit/<PIPELINE_ID>/
  business-requirements.md <- migrate output only
  migration-analysis.md    <- migrate evidence, risk register, retirement audit, and safe-seam strategy analysis only
  design-spec.md           <- brainstorm or migrate output
  migration-runbook.md     <- migrate operational runbook only
  implementation-plan.md   <- plan output
  execution-report.md      <- execute output
  validation-report.md     <- validate output
```

Migration provenance branches after design:

```text
business-requirements.md -> migration-analysis.md -> design-spec.md
                                                       |-> migration-runbook.md
                                                       `-> implementation-plan.md -> execution-report.md -> validation-report.md
```

The runbook and implementation plan are independent direct children of the design. `camel-migrate` owns the runbook;
no later pipeline stage consumes it.

### Pipeline State

`.camel-kit/pipeline.json` tracks the active manual pipeline. Skills resolve `activePipeline` to find the working directory, and stage is detected by artifact presence (spec-kit pattern). It is not Ship run state.

Ship harness entry points are thin delegates to the configured `camel-kit ship` or `camel kit ship` command. The local controller is the sole writer of Ship state and transitions. Its run records and retained evidence live under `CAMEL_KIT_SHIP_STATE_HOME` when configured, otherwise under `$XDG_STATE_HOME/camel-kit/ship` or `~/.local/state/camel-kit/ship`. This keeps controller state and validation evidence outside the live project.

### Stage Detection

For manual pipelines, the stage is determined by which artifacts exist in the pipeline directory — no explicit stage tracking. This follows the spec-kit pattern where artifact presence IS the state.

### Dual-Mode Invocation

Every pipeline skill (brainstorm, plan, execute, validate) supports two invocation modes:

- **Chained mode** — the skill is auto-invoked by the previous stage within the same conversation. HARD-RULE auto-transitions are enforced (brainstorm → plan → execute → validate).
- **Standalone mode** — the skill is invoked independently (new session, CI/CD, or manual re-entry). It reads its input from pipeline artifacts on disk and writes its output to the same directory. Auto-transitions are suppressed — the caller manages stage progression.

Detection is automatic: if the skill was auto-invoked in conversation context, it runs in chained mode. If invoked independently with pipeline artifacts available, it runs in standalone mode.

### Re-iteration and Staleness

When `/camel-brainstorm <PIPELINE_ID>` is invoked on a pipeline that already has a design spec, the skill enters **amend
mode**: it loads the existing spec, lets the user modify it, and writes the updated version back. Because `doc stale`
marks its target as well as its descendants, the freshly regenerated design is not targeted. Instead, each existing
direct child is marked stale separately:

```bash
camel-kit doc stale --reason "design spec was amended" --cascade docs/camel-kit/001-order-processing/migration-runbook.md
camel-kit doc stale --reason "design spec was amended" --cascade docs/camel-kit/001-order-processing/implementation-plan.md
```

The runbook command applies only when that migration artifact exists; greenfield pipelines have only the plan branch.
Changes to business requirements or migration analysis cascade through the design to both branches. Re-planning
regenerates and clears staleness only for `implementation-plan.md`; an existing runbook stays stale until
`camel-migrate` regenerates it for operational use.

Staleness is tracked in structured YAML frontmatter within each artifact (see
[camel-kit doc](commands.md#camel-kit-doc) for the full schema and CLI reference). Skills detect staleness by running
`camel-kit doc check <file>` and inspecting the JSON output.

For controller-owned runs, `camel-kit ship --resume <run-id>` re-reads recorded inputs and artifacts, compares their digests, and restarts the earliest stale or incomplete controller stage. It does not use manual staleness frontmatter or `.camel-kit/pipeline.json` as its run-state authority.

### Verify Iteration Log

`.camel-kit/verify-log.md` is an append-only audit trail of verify cycles, recording findings, severity, and actions taken per iteration.

---

## 9. Key Design Decisions

### DataMapper Consistency Fix (Feb 2026)

Before this fix, XSLT generation varied between runs because the LLM would re-derive XPaths differently each time from
the same schema. The solution: pre-compute Source XPaths and Target Elements once during the canonicalize stage (design
time), store them in the design spec, and use them verbatim during implementation. The key insight is that for LLM code
generation, providing the exact template per case produces consistent output -- never a single template with conditional
rules.

### Version Alignment

Camel-Kit ships version defaults in `distribution.properties`, then persists the selected runtime/version into `.camel-kit/config.properties` during init. Users can override properties via `-p key=value` CLI flags or a custom config file (`-c path`). Component availability is verified via the MCP catalog layer using the workspace-selected values.

### Multi-Agent Parity

Most targets use the shared markdown skills, with agent-specific differences (sub-agent dispatch vs. custom modes vs. inline execution) handled by traits and templates. Bob 1 legacy instead uses generated monolithic phase gates, so shared-skill changes must also be reflected in those gate variants to preserve parity.

### Constitution vs Iron Laws

The **Constitution** defines 8 route quality rules (what makes a good route): route structure, single responsibility, separation of concerns, naming conventions, observability, external configuration, component support verification, infrastructure via Forage.

The **Iron Laws** define six pipeline process rules (how the pipeline operates): MCP verification, constitution compliance, no code without an approved design and task plan, spec compliance before quality, adversarial review before staged review, and surgical changes.

Iron Law 2 explicitly incorporates and enforces the 8 constitution rules. They are complementary, not overlapping -- the constitution says what to check, the iron laws say when and how to enforce it.

---

## 10. How to Add a Skill

### Steps

1. **Create directory:** `camel-kit-core/src/main/resources/skills/camel-{name}/`

2. **Write `SKILL.md`** with YAML frontmatter and guides table:

```markdown
---
name: camel-{name}
description: Brief description with trigger keywords
user_invocable: false
---

# /camel-{name}

> One-line purpose

## Guides

| Guide | When Loaded | Purpose |
|-------|-------------|---------|
| `guides/main-guide.md` | Always | Primary instruction guide |
```

**Note:** Only `camel-start` should have `user_invocable: true`. All other skills have `user_invocable: false`. Generated command stubs and Codex/Copilot project skills still work independently of this metadata; Qwen generation also emits its equivalent hyphenated `user-invocable` field.

3. **Write guide files** in `guides/`. Each guide is a self-contained markdown instruction file loaded by the agent when the skill is active.

4. **Update the workflow manifest first:** add or modify the entry in `camel-kit-core/src/main/resources/workflow/camel-kit-workflow.yaml`. Set `generated_stub: true` only for commands that should be emitted into each agent's commands directory. Add or update the corresponding skill entry, stage/artifact metadata, transitions, and MCP tool allowlists if the workflow contract changes.

5. **If registering generated command or skill entry points:** update agent-specific guidance only where the command needs custom behavior beyond the generated stub or project skill. The default generator creates command stubs from the manifest. Agent templates still need updates when they contain human-readable command tables, custom modes, policies, or sub-agent dispatch:
   - Claude Code: update `templates/claude/claude-md.md`
   - IBM Bob 1 legacy: update `templates/bob/custom_modes.yaml`, gate files, and rules directories
   - IBM Bob 2: update `agents/registry/bob2.yaml`, `Bob2Generator`, `templates/dispatch/bob2.md`, `templates/bob2/` modes/agents/rules, `templates/traits/bob2/`, and any shared `agents/*.md` installed under `.bob/personas/`
   - Gemini CLI: update `templates/gemini/gemini-md.md`
   - OpenAI Codex CLI: update `templates/codex/`, `templates/dispatch/codex.md`, and custom-agent TOML templates
   - GitHub Copilot CLI: update `templates/copilot/copilot-instructions.md`, `templates/copilot/agents-md.md`, and any affected `.github/agents` templates
   - Pi: update `templates/pi/agents-md.md`, `templates/dispatch/pi.md`, and guard policy templates when relevant
   - Qwen: update `agents/registry/qwen.yaml`, `QwenGenerator`, `templates/qwen/agents/`, `templates/qwen/qwen-md.md`, `templates/dispatch/qwen.md`, `templates/traits/qwen/`, and the Qwen MCP config template
   - OpenCode: update `templates/opencode/`, `templates/traits/opencode/`, the OpenCode MCP config template, and `OpenCodeGenerator`

6. **If changing an agent capability:** update `agents/registry/{agent}.yaml` when skills or command directories,
   command-generation behavior, file formats, MCP config paths or formats, generator strategy, dispatch templates,
   installed templates, sub-agent support, trait support, or capability labels change.

7. **If internal:** update the loading skill's `SKILL.md` to reference the new guides (e.g., add a guide reference to `camel-execute`'s guide manifest).

8. **Update `docs/commands.md`** if the skill is user-facing.

9. **Run manifest consistency tests** in `camel-kit-core` to verify generated stubs, skill metadata, and runtime-supported MCP filters still match the manifest.
