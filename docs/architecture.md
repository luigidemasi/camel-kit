# Camel-Kit Architecture Guide

This document describes Camel-Kit's internal architecture for contributors and extenders. For user-facing documentation, see the [User Guide](user-guide.md) and [Command Reference](commands.md).

---

## 1. Overview

Camel-Kit combines two mechanisms to give AI agents accurate, efficient access to the Apache Camel ecosystem:

- **Skills** -- markdown instruction files that guide LLM agents through structured workflows (design, implementation, validation, testing, verification)
- **MCP Servers** -- real-time queries against live Camel, documentation, and Citrus catalogs for component verification, validation, security analysis, and test generation

Together they enable AI-powered integration development targeting Apache Camel. Skills carry the process knowledge (how to design a flow, how to generate YAML, how to validate a route), while MCP provides the data knowledge (which components exist, what options they accept, whether an endpoint URI or Citrus test action is valid).

The authoritative workflow contract lives in `camel-kit-core/src/main/resources/workflow/camel-kit-workflow.yaml`. It defines command names and aliases, generated command stubs, skill visibility, pipeline stages, artifacts, transitions, MCP servers, allowed tools, and documentation references. Generator code reads this manifest for command stub generation and MCP allowlists, and tests validate skill frontmatter against it. When changing workflow behavior, update the manifest first and then update the Markdown skill bodies and docs to match.

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

**Note:** Only `camel-start` sets `user_invocable: true` — it is the single auto-discovered entry point (meta-router). Pipeline and standalone skills (Tier 1/2) are invoked through generated command stubs on slash-command agents and through project skill selection on GitHub Copilot CLI. Internal skills are dispatched only by pipeline skills.

The frontmatter fields:
- `name` -- skill identifier, used in cross-references
- `description` -- trigger keywords that help agents match user intent to the correct skill
- `user_invocable` -- `true` for `camel-start` (meta-router) only. Pipeline and standalone skills (Tier 1/2) still have generated entry points despite `user_invocable: false`: slash-command stubs for most agents and project skills for GitHub Copilot CLI. Internal skills (`camel-verify`, `camel-design`, `camel-implement`, `camel-test`) are dispatched only by pipeline skills

### All Skills

| Skill | User-Invocable | Loaded By | Purpose |
|-------|---------------|-----------|---------|
| `camel-start` | Yes | -- | Meta-router and primary entry point: detects intent, loads appropriate pipeline |
| `camel-brainstorm` | No | `camel-start` (greenfield) | Orchestrate design phase: interview user, produce the pipeline design spec |
| `camel-plan` | No | `camel-brainstorm` (after design approval) | Produce detailed implementation plan from approved design spec |
| `camel-execute` | No | `camel-plan` (auto-invoked after planning) | Environment probe, dispatch sub-agents per task with two-stage review |
| `camel-migrate` | No | `camel-start` (migration) | Migration entry point: shortcut into `camel-brainstorm` with project type pre-set |
| `camel-verify` | No | `camel-execute` (internal sub-agent) | 3-phase runtime verification loop (build, Citrus tests, report) — runs inside execute, not as a standalone pipeline stage |
| `camel-ship` | No | -- (standalone orchestrator) | Autonomous pipeline — chains brainstorm → plan → execute → validate with configurable oversight |
| `camel-design` | No | `camel-brainstorm` | Guides for component selection, EIP catalog, and flow design assembly |
| `camel-implement` | No | `camel-execute` | Guides for YAML generation, properties, Docker Compose, DataMapper |
| `camel-validate` | No | `camel-ship` (Stage 3) | Tier 1 quality gate: schema validation, endpoint verification, security analysis |
| `camel-test` | No | `camel-execute` | Guides for route analysis and test generation with Citrus + Testcontainers |
| `camel-knowledge` | No | `camel-brainstorm`, `camel-execute` | Routes questions to knowledge MCP tools |
| `camel-debug` | No | `camel-start` (ad-hoc troubleshooting) | Standalone debugging: STOP → PRESERVE → DIAGNOSE → FIX → GUARD workflow |

**Note:** Only `camel-start` has `user_invocable: true` in its skill metadata. Pipeline and standalone skills still have generated entry points despite `user_invocable: false`: slash-command stubs for most agents and project skills for GitHub Copilot CLI. Internal skills (`camel-verify`, `camel-design`, `camel-implement`, `camel-test`) are dispatched only by pipeline skills.

### Shared Guides

Shared guides live at `camel-kit-core/src/main/resources/skills/shared/` and are loaded by multiple skills:

| Guide | Purpose |
|-------|---------|
| `iron-laws.md` | Six non-negotiable pipeline process enforcement rules |
| `datamapper-canonicalize.md` | Engine selection and field mapping enrichment for DataMapper |
| `flow-test-data.md` | Test data generation patterns for flow design |
| `mcp-setup.md` | MCP version mapping and connection parameters |
| `graph-availability.md` | Graph MCP server availability detection |
| `mulesoft-graph.md` | MuleSoft graph node types and auto-detection |
| `biztalk-phase1.md`, `biztalk-phase2.md`, `biztalk-component-mapping.md`, `biztalk-map-conversion.md`, `biztalk-expression-mapping.md`, `biztalk-pipeline-mapping.md` | BizTalk migration guides (adapter mappings, orchestration shape to EIP, functoid to Camel patterns, pipeline component mapping) |
| `yaml-structure.md` | YAML DSL structure rules and Kaoto compatibility |
| `yaml-components.md` | Component URI syntax and parameter rules |
| `yaml-examples.md` | Reference YAML patterns for common integrations |
| `patterns-foundational.md` | Foundational EIP patterns (content-based routing, splitter, aggregator) |
| `patterns-error-handling.md` | Error handling patterns (dead letter channel, retry, circuit breaker) |
| `patterns-deployment.md` | Deployment patterns (health checks, graceful shutdown, scaling) |

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

**RuntimeDetector utility:** Shared utility that detects runtime environment (Spring Boot, Quarkus, Camel Main, Karaf) from `MAVEN_ARTIFACT` nodes. Used by JavaGraphParser to build the framework type allowlist and by the migration-context command to classify the project runtime.

**Auto-detection:** When the graph builder encounters an XML file, it checks for the `mulesoft.org/schema/mule` namespace. If present, the file is routed to `MuleXmlFlowParser` instead of `CamelRouteParser`. For BizTalk projects, the builder checks for the `schemas.microsoft.com/BizTalk` namespace and file extensions (`.odx`, `.btm`, `.btp`). If detected, files are routed to `BizTalkParser`. No explicit configuration is required -- if the project contains MuleSoft or BizTalk artifacts, they are parsed automatically.

**`--source-platform` flag:** For cases where auto-detection needs hinting (e.g., the project has non-standard file layouts), `camel-kit init --source-platform mulesoft` or `camel-kit init --source-platform biztalk` explicitly declares the source platform.

**ConfigParser expansion:** Now captures ALL application.properties keys, not just `camel.*` prefixes. This enables PropertyBindingParser to analyze datasource configurations, Quarkus build-time properties, and other framework-specific settings.

**DataWeave analysis:** The `DataWeaveParser` extracts version declarations, input/output content types, function definitions, and field access patterns from `.dwl` files. This helps the migration skill identify complex transformations that may need manual attention -- multi-function scripts, recursive field access, or format conversions that have no direct XSLT equivalent.

**BizTalk parser architecture:** The `BizTalkParser` is a hybrid parser that delegates to 4 internal StAX-based parsers (`BizTalkOdxParser`, `BizTalkBtmParser`, `BizTalkBtpParser`, `BizTalkBindingParser`) based on file extension and content. StAX was chosen over DOM to handle large orchestration files efficiently (streaming parse instead of full in-memory tree). The parser recognizes 37 orchestration shape types (Receive, Send, Decide, Loop, Parallel, Call, Scope, etc.) and 45 functoid type mappings (string ops, math, looping, scripting, database lookup).

**Reference implementation:** The [BizTalkMigrationStarter](https://github.com/haroldcampos/BizTalkMigrationStarter) project (BizTalk → Azure Logic Apps) provided the reference parsing patterns. Its C# `BizTalkOrchestrationParser` (ODX text extraction + XPath), `BtmParser` (functoid/link resolution), `PipelineParser` (XmlSerializer deserialization), and `BindingSnapshot` (LINQ-to-XML) were translated to Java StAX equivalents for camel-kit.

### Graph Migration Context Command

The `graph migration-context` CLI command produces a comprehensive structured JSON analysis of a project's integration landscape by traversing the property graph. This context powers the migration skills by providing a complete dependency map before any transformation work begins.

**Usage:** `camel-kit graph migration-context <routeId> [--depth N]`

**Output sections:**

| Section | Content | Source |
|---------|---------|--------|
| `routes` | List of all Camel routes with IDs, endpoints, and processors | `CamelRouteParser` nodes |
| `components` | Used Camel components with URIs and properties | Endpoint URI analysis |
| `services` | Business services and their dependencies (including interface-based) | `JavaGraphParser` + `CrossLinker` |
| `artifacts` | Maven dependencies, plugins, and runtime detection | `PomParser` + `RuntimeDetector` |
| `properties` | Configuration properties with bean/type/property references | `ConfigParser` + `PropertyBindingParser` |
| `warnings` | Detected migration risks (unsupported patterns, complex DI, etc.) | Cross-parser heuristics |

The command uses `GraphQuery.expandWithInterfaces()` to traverse service dependencies across interface boundaries, ensuring all implementation classes are discovered even when references only declare interfaces. Runtime detection via `RuntimeDetector` classifies the project as Spring Boot, Quarkus, Camel Main, or Karaf, informing component availability checks and migration strategy selection.

This graph-aware context replaces file-by-file static analysis with topology-driven dependency resolution, surfacing transitive dependencies and cross-cutting concerns that would otherwise require manual discovery.

---

## 3. The Orchestration Model

### Pipeline

The end-to-end pipeline follows a strict phase progression:

```
brainstorm / migrate
       |
       v
   business requirements + design spec
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
   artifacts + verification report
```

The execute phase starts with an **environment probe** that validates the target environment before dispatching implementers. If architectural failures are found, a **re-plan loop** modifies affected flow design sections and re-executes (max 3 rounds).

Entry points diverge (`camel-brainstorm` for greenfield, `camel-migrate` for migration) but both produce the same
artifact format -- business requirements plus an active design spec under `docs/camel-kit/<PIPELINE_ID>/`. This means
`camel-plan` and `camel-execute` work identically regardless of whether the project is greenfield or migrated.

### How camel-execute Dispatches Work

1. Read the approved implementation plan, prefer the `yaml plan-metadata` task graph, and fall back to Markdown task
   parsing for older plans
2. **Catalog research** (Step 1.5): dispatch a `catalog-researcher` sub-agent to batch-verify all MCP catalog artifacts for the wave. Only the structured summary flows back -- MCP response traces stay in the research sub-agent's context.
3. For each task:
   - Dispatch an implementer sub-agent with full task text, design spec section, pre-verified catalog summary, and MCP parameters
   - **Adversarial Code Review** (Step 2b.5): dispatch parallel Critic Lanes via a Moderator sub-agent to adversarially review the implementation against the design spec. Hard cap: 3 cycles.
   - Dispatch a **spec compliance reviewer** (sub-agent) -- does the output match the design spec?
   - If spec review passes, dispatch a **code quality reviewer** (sub-agent) -- constitution compliance, security, anti-patterns
   - If either reviewer finds critical issues, return to the implementer for fixes, then re-review
   - Mark task complete and immediately start the next task (no pause, no user confirmation)
4. After all tasks: dispatch a **cross-cutting review** as a sub-agent across all generated routes
5. Dispatch the **verification phase** (`camel-verify`) as an internal sub-agent within execute (build, Citrus tests, report)
6. Print the completion summary

After execute completes, the pipeline continues to **validation** (`camel-validate`) as Stage 3 — the final quality gate.

For subagent-capable targets, reviews, verification, and catalog lookups run in isolated contexts and only structured reports flow back to the orchestrator. Bob 1 legacy keeps this work in one session and relies on mode gates instead.

### Agent-Specific Execution

The dispatch model varies by AI agent:

- **Claude Code** -- dispatches fresh sub-agents per task. Each sub-agent runs in isolated context with no cross-contamination between tasks.
- **IBM Bob 1 legacy** -- switches between custom modes and monolithic gate files with scoped tool permissions per mode.
- **IBM Bob 2** -- uses native `spawn_subagent` (`explore` and `general`) while retaining Bob custom modes for tool restrictions.
- **Gemini CLI, Qwen, OpenCode** -- use their native agent/delegation models with shared Camel-Kit skills and traits.

### The Design Spec Contract

Both `camel-brainstorm` (greenfield) and `camel-migrate` (migration) produce the same output format: the active
pipeline design spec under `docs/camel-kit/<PIPELINE_ID>/design-spec.md`. This is the contract between design and
implementation -- `camel-plan` consumes this format, and `camel-execute` implements from it. The design phase diverges
(interview vs. source analysis), but the output converges.

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

Each descriptor defines the agent id, display name, command directory, command file format, argument placeholder,
MCP config path, MCP config template path, MCP server container key, description, generator strategy, dispatch
template path, templates installed by the agent-specific generator, and whether the agent supports sub-agents or
traits. Missing required fields, duplicate ids, unsupported generator strategies, or malformed YAML fail during
registry loading with a descriptor-specific error.

### What `camel-kit init` Generates

| Agent | Template Dir | Instruction File | MCP Config | Skills Location |
|-------|-------------|-----------------|------------|-----------------|
| Claude Code | `templates/claude/` | `CLAUDE.md` | `.mcp.json` | `.claude/skills/` |
| IBM Bob 1 legacy | `templates/bob/` | `custom_modes.yaml` + rules + gates | `.bob/mcp.json` | `.bob/skills/` |
| IBM Bob 2 | `templates/bob2/` | `custom_modes.yaml` + rules + shared skills | `.bob/mcp.json` | `.bob/skills/` |
| Gemini CLI | `templates/gemini/` | `GEMINI.md` + `@file.md` imports + policies | `.gemini/settings.json` | `.gemini/skills/` |
| GitHub Copilot CLI | `templates/copilot/` | `.github/copilot-instructions.md` + `.github/agents/` + hooks | `.github/mcp.json` | `.github/skills/` |
| Qwen | `templates/qwen/` | `QWEN.md` + sub-agent definitions | `.qwen/settings.json` | `.qwen/skills/` |
| OpenCode | `templates/opencode/` | `AGENTS.md` + permission-based agents | `opencode.json` | `.opencode/skills/` |

### Resource Consistency Contract

Skills, generated instruction files, MCP config templates, and active docs are runtime contract surfaces. They are tested by `ResourceConsistencyTest` in `camel-kit-core` so stale contract tokens fail the build before they ship.

The active scan covers `camel-kit-core/src/main/resources/skills`, `camel-kit-core/src/main/resources/templates`, top-level Markdown files under `docs/`, `README.md`, and `CONTRIBUTING.md`. These files must reference current command names, current config files, current graph access patterns, current Knowledge MCP tool names, and current Iron Law counts.

Docs subdirectories are intentionally out of scope; this keeps ignored archives such as `docs/plans/` and `docs/superpowers/`, image assets, and generated planning material out of the contract check.

`ShippedAssetStructureTest` covers structural coherence for shipped assets. It verifies that workflow manifest skills have `skills/<name>/SKILL.md`, guide tables in `SKILL.md` point to existing bundled files, shared guide references in shipped skills and templates resolve, every supported agent descriptor has dispatch and MCP config templates, descriptor template sources exist, trait files target existing skills or guides, every shipped trait appears in generated output for its production agent generator, generated command files match manifest command names, generated command skill references resolve to copied skills, and generated MCP configs parse as JSON with the expected server containers.

Historical release notes, old planning material, and archived ADR-style documents are outside the active contract unless they are copied into generated projects or used as live instructions. Keep historical context in those files as history; do not exclude an active shipped instruction just because it is inconvenient to update.

### The Equalization Layer

All supported agents receive the same skills (markdown instruction files). The template layer adapts the instruction format to each agent's conventions (system prompt vs. custom modes vs. agent files), but the underlying skill content is identical. This means a fix to a skill guide benefits all agents simultaneously.

**What equalization covers:**
- Skill content (all agents read the same `SKILL.md` and guide files)
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

Traits are agent-specific instruction fragments that are appended to shared skill files during `camel-kit init`. They bridge the gap between the equalization layer (identical skills) and the per-agent template layer (different dispatch models).

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
2. **Constitution Compliance** -- every generated route must pass all 7 constitution rules (incorporates and enforces the constitution)
3. **No Code Without Plan & Design Approval** -- never generate implementation artifacts before the user has approved the design spec and a task-based implementation plan exists
4. **Spec Compliance Before Quality** -- always run spec compliance review before code quality review; wrong order wastes effort
5. **Adversarial Code Review** -- generated code must pass the adversarial review gate before spec compliance and quality review
6. **Surgical Changes** -- implementation tasks must touch only what they were asked to touch

### Sub-agent-Driven Execution

The `/camel-execute` pipeline relies on dispatching discrete units of work to isolated agents. The design principle: the agent that writes the code should never be the same agent that reviews it, and each task should start from a clean context with no residual assumptions from previous tasks.

Most supported agents use native **sub-agent dispatch** or custom-agent isolation:

- **Claude Code** -- uses the `Agent` tool to spawn fresh sub-agents per task. Each sub-agent receives the task text, relevant design spec section, guide file paths, and MCP parameters. Before implementation, a `catalog-researcher` sub-agent batch-verifies all MCP catalog artifacts (research isolation). After implementation, an Adversarial Code Review dispatches parallel Critic Lanes (Route Architecture, Security, Performance, Boundary Compliance, Behavioral Equivalence) via a Moderator sub-agent, then a spec-compliance reviewer sub-agent checks the design spec, then a code-quality reviewer sub-agent checks constitution compliance. At the Stamp Gate, three reviewers run in parallel (spec, quality, security). Claude uniquely supports **parallel dispatch**: `camel-kit plan analyze` groups tasks into waves using structured plan metadata (`dependsOn`, file overlap, and logical `provides`/`consumes` resources such as endpoints, routes, properties, schemas, test data, beans, external services, and route contracts), then independent tasks are dispatched simultaneously to multiple sub-agents.

- **Gemini CLI** -- dispatches via a unified `invoke_subagent` tool to 6 specialized sub-agents. The scheduler natively supports **parallel tool execution** via `Promise.all()` (default-parallel). However, sub-agents cannot invoke other sub-agents (hardcoded `Kind.Agent` filter), so `/camel-execute` runs in the **main agent context** where it can dispatch to all sub-agents. Within-wave parallelism is achieved through the scheduler batching multiple `invoke_subagent` calls.

- **Qwen** -- dual dispatch model: **named sub-agents** (clean context, parent blocks) and **forks** (inherit parent context, run in background). The fork model enables parallel review and research tasks. Read-only tools (Read, Search, Fetch) are concurrent with a configurable cap (max 10). The `"MUST BE USED for..."` phrasing in description fields forces automatic delegation.

- **OpenCode** -- 7 agents with granular, per-type glob permissions. The executor agent has `task: {"*": allow}` permission. Sub-agent-to-sub-agent delegation is now opt-in (PR #7756) with configurable depth limits and call budgets. LLM-level parallel tool calls are supported. Each agent has a `steps` limit (implementer: 50, executor: 100) that triggers graceful summarization rather than hard failure.

- **GitHub Copilot CLI** -- project skills live under `.github/skills/` and custom agents live under `.github/agents/`. Camel-Kit generates planner, implementer, tester, validator, migrator, catalog researcher, and security reviewer agents with Copilot tool aliases and MCP server prefixes. Internal guide skills copied for custom-agent use are marked `user-invocable: false` and `disable-model-invocation: true` using Copilot-readable metadata. MCP servers are committed in `.github/mcp.json` using Copilot's `tools` schema. Repository hooks under `.github/hooks/` provide a lightweight safety harness for destructive shell commands while keeping Copilot's normal permission prompts active.

**IBM Bob 2** uses Bob's native `spawn_subagent` tool. Camel-Kit exposes this as `--ai bob2`, but generated project files still live under `.bob/` because Bob reads `.bob/commands`, `.bob/skills`, `.bob/custom_modes.yaml`, and `.bob/mcp.json`.

- `explore` is used for read-only research, route inspection, spec review, quality review, and MCP verification summaries.
- `general` is used for implementation, test generation, and fix tasks that need edit or execute access.
- Multiple `spawn_subagent` calls in one parent turn run in parallel, so `/camel-execute` dispatches all independent tasks in the current `camel-kit plan analyze` wave together.
- The parent Bob task remains the orchestrator. Subagents return summaries and must not spawn subagents.
- Bob 2 skills are the shared Camel-Kit `SKILL.md` files with Bob 2 traits appended; Bob 2 does not replace them with monolithic gates.

**IBM Bob 1 legacy (`--ai bob`)** uses a fundamentally different architecture -- the **B+A (Behavior + Advanced) hybrid with mode switching**:

1. Each pipeline phase starts in **Advanced mode** (unrestricted), allowing the agent to read all skill files and project context
2. The first instruction in the gate file switches to a **restricted custom mode** (e.g., `camel-brainstorm`, `camel-implement`) with scoped tool permissions
3. The mode's tool group constrains what the AI can do for the remainder of that phase

This means Bob 1 cannot isolate tasks into separate context windows or use independent reviewer agents. The compensation is that Bob's tool restrictions are **platform-enforced**, not instruction-based. During design, Bob's `camel-brainstorm` mode grants only `read`, `edit` (`.md` files only via `fileRegex`), `mcp`, and `browser` -- the AI physically cannot edit code files because the mode excludes the edit tool for non-markdown files. This is stricter than any instruction-based constraint, which the AI could rationalize away.

Bob 1 also requires **monolithic gate files** (one per pipeline phase, 6-10 KB each) that inline complete orchestration logic, because it cannot chain skill references across mode switches the way sub-agent-based agents load skills into fresh contexts.

The trade-off table:

| Design Dimension | Sub-agent Dispatch | Mode Switching (Bob 1 Legacy) |
|-----------------|-------------------|------------------------------|
| Context isolation | Per-task (fresh sub-agent) | Per-session (accumulated) |
| Reviewer independence | Separate sub-agent | Same session self-reviews |
| Tool restriction mechanism | Instruction-based / tool whitelists / policies | Platform-enforced mode tool groups |
| Parallel execution | Claude (graph topology), Bob 2 (`spawn_subagent` in one turn), Gemini (scheduler `Promise.all()`), Qwen (fork), OpenCode (LLM-level) | Not possible |
| Skill loading | Loaded into sub-agent context on dispatch | Inlined in monolithic gate files |
| Template complexity | 3-12 files per agent | 17+ files (gates + rules + modes) |
| Failure isolation | Sub-agent failure doesn't affect other tasks | Phase failure affects entire session |

### Per-Agent Summary

| Agent | Dispatch Model | Key Differentiator |
|-------|---------------|-------------------|
| Claude Code | Parallel sub-agent dispatch | Route graph topology, research isolation, parallel fan-out, adversarial code review |
| IBM Bob 1 legacy | B+A hybrid with custom modes | Monolithic gate files, 3 checkpoint types |
| IBM Bob 2 | Native `spawn_subagent` plus custom modes | `explore`/`general` subagents, parallel same-turn dispatch, shared skills |
| Gemini CLI | `invoke_subagent` + parallel scheduler | Default-parallel `Promise.all()`, TOML policy, MCP wildcards, A2A remote agents |
| GitHub Copilot CLI | Project skills + custom agents + hooks | `.github/skills`, `.github/agents`, `.github/mcp.json`, safety hooks |
| Qwen | Dual dispatch (named + fork) | Fork background tasks, DashScope cache sharing, auto-delegation |
| OpenCode | `task` child sessions + opt-in delegation | 14 permission types, glob patterns, configurable depth limits |

For full per-agent deep dives (template files, tool restriction models, configuration examples, unique capabilities), see **[Agent Architectures](agent-architectures.md)**.

### Adding a New Agent

To add support for a new AI coding assistant:

1. Add `agents/registry/{agent-name}.yaml` with command paths, MCP config path, generator strategy,
   dispatch template, and capabilities.
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
| `camel_validate_route` | Validate endpoint URIs and route definitions against catalog schema |
| `camel_validate_yaml_dsl` | Validate Camel YAML DSL syntax |
| `camel_transform_route` | Convert routes between YAML and XML formats |
| `camel_route_context` | Extract components and EIPs from route (YAML/XML/Java) |
| `camel_route_harden_context` | Analyze routes for security concerns (47 checks) |
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
| `camel-validate` | Route validation, hardening, diagnostics, dependency/config checks | varies |
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

The verification pipeline (`camel-verify`) is a 3-phase feedback loop that builds and tests the generated application using Citrus integration tests. It runs as an internal sub-agent within `camel-execute`, not as a standalone pipeline stage. After verification completes inside execute, `camel-validate` runs as the final pipeline stage.

### Phases

1. **Build Verification** -- compile the project with `./mvnw`, classify and fix build errors (skipped for JBang runtime)
2. **Test Verification** -- run Citrus YAML integration tests via `camel test run`, classify and fix test failures. Citrus tests are self-contained: Testcontainers start external services, `camel:jbang:run` starts the Camel integration, send/receive actions validate behavior.
3. **Report** -- structured summary of all phases, fixes applied, and issues found

Each phase has an independent iteration budget of **max 15 attempts**. On each iteration, errors are classified and routed to the appropriate fix strategy.

### Environment Probe

Before the verify loop runs, `camel-execute` performs an **environment probe** as its first step. The probe generates a
throwaway skeleton (pom.xml, docker-compose, empty route) and checks dependency resolution, Docker service availability,
and runtime startup. Failures are classified as **mechanical** (auto-fix and re-probe) or **architectural** (trigger
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
| Wrong endpoint options | `ResolveEndpointFailedException` | `camel-validate` |
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

Errors route to one of six destinations:

1. **Self-repair** -- fix pom.xml, application.properties, or test configuration directly
2. **camel-validate** -- route to validation skill for endpoint URI fixes
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
{COMMAND_PREFIX} nextId <slug>
```

This scans `docs/camel-kit/` for existing directories, finds the max ID, and creates `docs/camel-kit/<NNN+1>-<slug>/`.

### Directory Structure

```text
docs/camel-kit/<PIPELINE_ID>/
  design-spec.md           <- brainstorm output
  implementation-plan.md   <- plan output
  execution-report.md      <- execute output
  validation-report.md     <- validate output
  stamp-report.md          <- ship stamp gate output
```

### Pipeline State

`.camel-kit/pipeline.json` tracks the active pipeline:

- **Manual mode** (`mode: "manual"`): Skills resolve `activePipeline` to find the working directory. Stage is detected by artifact presence (spec-kit pattern).
- **Ship mode** (`mode: "ship"`): Full lifecycle tracking with `currentStage`, `stageResults`, oversight level, and fix attempts.

### Stage Detection

For manual pipelines, the stage is determined by which artifacts exist in the pipeline directory — no explicit stage tracking. This follows the spec-kit pattern where artifact presence IS the state.

### Dual-Mode Invocation

Every pipeline skill (brainstorm, plan, execute, validate) supports two invocation modes:

- **Chained mode** — the skill is auto-invoked by the previous stage within the same conversation. HARD-RULE auto-transitions are enforced (brainstorm → plan → execute → validate).
- **Standalone mode** — the skill is invoked independently (new session, CI/CD, or manual re-entry). It reads its input from pipeline artifacts on disk and writes its output to the same directory. Auto-transitions are suppressed — the caller manages stage progression.

Detection is automatic: if the skill was auto-invoked in conversation context, it runs in chained mode. If invoked independently with pipeline artifacts available, it runs in standalone mode.

### Re-iteration and Staleness

When `/camel-brainstorm <PIPELINE_ID>` is invoked on a pipeline that already has a design spec, the skill enters **amend mode**: it loads the existing spec, lets the user modify it, and writes the updated version back. All downstream artifacts are marked stale via:

```bash
camel-kit doc stale --reason "design spec was amended" --cascade design-spec.md
```

Staleness is tracked in structured YAML frontmatter within each artifact (see [camel-kit doc](commands.md#camel-kit-doc) for the full schema and CLI reference). Skills detect staleness by running `camel-kit doc check <file>` and inspecting the JSON output.

When `camel-ship --resume` detects stale artifacts, it automatically re-runs from the earliest stale stage instead of the stored `currentStage`. This ensures the pipeline produces consistent output after upstream amendments.

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

Skills are markdown instruction files -- the same skill works across all supported agents. Agent-specific differences (sub-agent dispatch vs. custom modes vs. inline execution) are handled by the template layer, not the skill layer. A bug fix or improvement to a guide file benefits every agent.

### Constitution vs Iron Laws

The **Constitution** defines 7 route quality rules (what makes a good route): route structure, single responsibility, separation of concerns, naming conventions, observability, external configuration, component support verification.

The **Iron Laws** define pipeline process enforcement rules (how the pipeline operates): MCP verification, constitution compliance, no code without design approval, spec compliance before quality.

Iron Law 2 explicitly incorporates and enforces the 7 constitution rules. They are complementary, not overlapping -- the constitution says what to check, the iron laws say when and how to enforce it.

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

**Note:** Only `camel-start` should have `user_invocable: true`. All other skills have `user_invocable: false`. Generated command stubs and Copilot project skills still work independently of this metadata.

3. **Write guide files** in `guides/`. Each guide is a self-contained markdown instruction file loaded by the agent when the skill is active.

4. **Update the workflow manifest first:** add or modify the entry in `camel-kit-core/src/main/resources/workflow/camel-kit-workflow.yaml`. Set `generated_stub: true` only for commands that should be emitted into each agent's commands directory. Add or update the corresponding skill entry, stage/artifact metadata, transitions, and MCP tool allowlists if the workflow contract changes.

5. **If registering generated command or skill entry points:** update agent-specific guidance only where the command needs custom behavior beyond the generated stub or project skill. The default generator creates command stubs from the manifest. Agent templates still need updates when they contain human-readable command tables, custom modes, policies, or sub-agent dispatch:
   - Claude Code: update `templates/claude/claude-md.md`
   - IBM Bob 1 legacy: update `templates/bob/custom_modes.yaml`, gate files, and rules directories
   - IBM Bob 2: update `templates/bob2/custom_modes.yaml`, `templates/traits/bob2/`, and rules directories
   - Gemini CLI: update `templates/gemini/gemini-md.md`
   - GitHub Copilot CLI: update `templates/copilot/copilot-instructions.md`, `templates/copilot/agents-md.md`, and any affected `.github/agents` templates
   - Qwen: update `templates/qwen/qwen-md.md`
   - OpenCode: update `templates/opencode/agents-md.md`

6. **If changing an agent capability:** update `agents/registry/{agent}.yaml` when command directories,
   file formats, MCP config paths, generator strategy, dispatch templates, installed templates, sub-agent support,
   trait support, or capability labels change.

7. **If internal:** update the loading skill's `SKILL.md` to reference the new guides (e.g., add a guide reference to `camel-execute`'s guide manifest).

8. **Update `docs/commands.md`** if the skill is user-facing.

9. **Run manifest consistency tests** in `camel-kit-core` to verify generated stubs, skill metadata, and MCP allowlists still match the manifest.
