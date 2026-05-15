# Camel-Kit Architecture Guide

This document describes Camel-Kit's internal architecture for contributors and extenders. For user-facing documentation, see the [User Guide](user-guide.md) and [Command Reference](commands.md).

---

## 1. Overview

Camel-Kit combines two mechanisms to give AI agents accurate, efficient access to the Apache Camel ecosystem:

- **Skills** -- markdown instruction files that guide LLM agents through structured workflows (design, implementation, validation, testing, verification)
- **MCP Server** -- real-time queries against the live Camel catalog for component verification, validation, and security analysis

Together they enable AI-powered integration development targeting Apache Camel. Skills carry the process knowledge (how to design a flow, how to generate YAML, how to validate a route), while MCP provides the data knowledge (which components exist, what options they accept, whether an endpoint URI is valid).

---

## 2. Skills Architecture

### What a Skill Is

A skill is a directory containing a manifest file (`SKILL.md`) and an optional `guides/` subdirectory with instruction files loaded on demand. The manifest uses YAML frontmatter to declare metadata and a table listing which guides exist and when to load them.

**Skill location:** `camel-kit-core/src/main/resources/skills/{skill-name}/`

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

**Note:** Only `camel-start` sets `user_invocable: true` — it is the single auto-discovered entry point (meta-router). Pipeline and standalone skills (Tier 1/2) are invoked via explicit slash commands. Internal skills are dispatched only by pipeline skills.

The frontmatter fields:
- `name` -- skill identifier, used in cross-references
- `description` -- trigger keywords that help agents match user intent to the correct skill
- `user_invocable` -- `true` for `camel-start` (meta-router) only. Pipeline and standalone skills (Tier 1/2) have explicit slash commands despite `user_invocable: false`. Internal skills (`camel-verify`, `camel-design`, `camel-implement`, `camel-test`) are dispatched only by pipeline skills

### All Skills

| Skill | User-Invocable | Loaded By | Purpose |
|-------|---------------|-----------|---------|
| `camel-start` | Yes | -- | Meta-router and primary entry point: detects intent, loads appropriate pipeline |
| `camel-brainstorm` | No | `camel-start` (greenfield) | Orchestrate design phase: interview user, produce BRD + TDDs |
| `camel-plan` | No | `camel-brainstorm` (after design approval) | Produce detailed implementation plan from approved design spec |
| `camel-execute` | No | `camel-plan` (auto-invoked after planning) | Environment probe, dispatch subagents per task with two-stage review |
| `camel-migrate` | No | `camel-start` (migration) | Migration entry point: shortcut into `camel-brainstorm` with project type pre-set |
| `camel-verify` | No | `camel-execute` (internal subagent) | 3-phase runtime verification loop (build, Citrus tests, report) — runs inside execute, not as a standalone pipeline stage |
| `camel-ship` | No | -- (standalone orchestrator) | Autonomous pipeline — chains brainstorm → plan → execute → validate with configurable oversight |
| `camel-design` | No | `camel-brainstorm` | Guides for component selection, EIP catalog, TDD assembly |
| `camel-implement` | No | `camel-execute` | Guides for YAML generation, properties, Docker Compose, DataMapper |
| `camel-validate` | No | `camel-ship` (Stage 3) | Tier 1 quality gate: schema validation, endpoint verification, security analysis |
| `camel-test` | No | `camel-execute` | Guides for route analysis and test generation with Citrus + Testcontainers |
| `camel-knowledge` | No | `camel-brainstorm`, `camel-execute` | Routes questions to knowledge MCP tools |
| `camel-debug` | No | `camel-start` (ad-hoc troubleshooting) | Standalone debugging: STOP → PRESERVE → DIAGNOSE → FIX → GUARD workflow |

**Note:** Only `camel-start` has `user_invocable: true` in its skill metadata. Pipeline and standalone skills have explicit slash commands despite `user_invocable: false`. Internal skills (`camel-verify`, `camel-design`, `camel-implement`, `camel-test`) are dispatched only by pipeline skills.

### Shared Guides

Shared guides live at `camel-kit-core/src/main/resources/skills/shared/` and are loaded by multiple skills:

| Guide | Purpose |
|-------|---------|
| `iron-laws.md` | Four non-negotiable pipeline process enforcement rules |
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

The `camel-kit-graph` module builds a property graph of the project structure by running a set of parsers over the project's source files. Each parser produces typed nodes and edges that the graph consumers (validation, implementation, testing, migration) can query.

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
   BRD + TDDs  (design spec)
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

The execute phase starts with an **environment probe** that validates the target environment before dispatching implementers. If architectural failures are found, a **re-plan loop** modifies affected TDDs and re-executes (max 3 rounds).

Entry points diverge (`camel-brainstorm` for greenfield, `camel-migrate` for migration) but both produce the same artifact format -- a BRD (Business Requirements Document) with TDDs (Technical Design Documents). This means `camel-plan` and `camel-execute` work identically regardless of whether the project is greenfield or migrated.

### How camel-execute Dispatches Work

1. Read the approved implementation plan and extract all tasks
2. **Catalog research** (Step 1.5): dispatch a `catalog-researcher` subagent to batch-verify all MCP catalog artifacts for the wave. Only the structured summary flows back -- MCP response traces stay in the research subagent's context.
3. For each task:
   - Dispatch an implementer subagent with full task text, design spec section, pre-verified catalog summary, and MCP parameters
   - **Adversarial Code Review** (Step 2b.5): dispatch parallel Critic Lanes via a Moderator subagent to adversarially review the implementation against the TDD. Hard cap: 3 cycles.
   - Dispatch a **spec compliance reviewer** (subagent) -- does the output match the design spec?
   - If spec review passes, dispatch a **code quality reviewer** (subagent) -- constitution compliance, security, anti-patterns
   - If either reviewer finds critical issues, return to the implementer for fixes, then re-review
   - Mark task complete and immediately start the next task (no pause, no user confirmation)
4. After all tasks: dispatch a **cross-cutting review** as a subagent across all generated routes
5. Dispatch the **verification phase** (`camel-verify`) as an internal subagent within execute (build, Citrus tests, report)
6. Print the completion summary

After execute completes, the pipeline continues to **validation** (`camel-validate`) as Stage 3 — the final quality gate.

All reviews, verification, and catalog lookups run as subagents with isolated context windows. Only structured reports flow back to the orchestrator -- preventing ~60-70% of pipeline tokens from accumulating in the main conversation.

### Agent-Specific Execution

The dispatch model varies by AI agent:

- **Claude Code** -- dispatches fresh subagents per task. Each subagent runs in isolated context with no cross-contamination between tasks.
- **IBM Project Bob** -- switches between custom modes (`brainstorm`, `plan`, `implement`, `validate`, `test`) with scoped tool permissions per mode.
- **Gemini CLI, Qwen, OpenCode** -- inline execution within the same session. Skills are loaded as instruction context rather than dispatched as separate agents.

### The BRD+TDD Contract

Both `camel-brainstorm` (greenfield) and `camel-migrate` (migration) produce the same output format: a BRD with per-flow TDDs. This is the contract between design and implementation -- `camel-plan` consumes this format, and `camel-execute` implements from it. The design phase diverges (interview vs. source analysis), but the output converges.

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

Pre-computed Source XPaths and Target Elements are stored in the TDD and used verbatim during implementation. The XSLT file is generated externally as `kaoto-datamapper-{id}.xsl` with a companion `.kaoto` metadata file for Kaoto IDE visual editing.

### Groovy Path

Inline scripts in YAML `transform:` steps. 4 format pairs (JSON to JSON, XML to JSON, JSON to XML, XML to XML). No external files, no `.kaoto` metadata.

### Validation Routing

The validation guide (`datamapper-validation.md`) reads the `Transformation Engine` field from the TDD and routes to the appropriate validation guide: `datamapper-groovy.md` for Groovy, or `datamapper-approach-a.md` / `datamapper-approach-b.md` for XSLT.

### Artifacts Comparison

| Aspect | XSLT Path | Groovy Path |
|--------|-----------|-------------|
| External file | `kaoto-datamapper-{id}.xsl` | None (inline in YAML) |
| YAML step | `xslt-saxon:` URI with parameters | `transform:` with `groovy:` expression |
| `.kaoto` metadata | Required (Kaoto IDE visual editor) | Skipped (no IDE support) |
| Maven dep (Spring Boot) | `camel-xslt-saxon-starter` | `camel-groovy-starter` |
| Maven dep (Quarkus) | `camel-quarkus-xslt-saxon` | `camel-quarkus-groovy` |
| TDD columns | 8 (incl. Source XPath, Target Element) | 6 (semantic only) |
| Kaoto IDE editing | Visual DataMapper editor | Edit YAML directly |

---

## 5. Agent Templates

### Template Directory

`camel-kit-core/src/main/resources/templates/{agent}/`

### What `camel-kit init` Generates

| Agent | Template Dir | Instruction File | MCP Config | Skills Location |
|-------|-------------|-----------------|------------|-----------------|
| Claude Code | `templates/claude/` | `CLAUDE.md` | `.mcp.json` | `.claude/skills/` |
| IBM Project Bob | `templates/bob/` | `custom_modes.yaml` + rules + gates | `.bob/mcp.json` | `.bob/skills/` |
| Gemini CLI | `templates/gemini/` | `GEMINI.md` + `@file.md` imports + policies | `.gemini/mcp.json` | `.gemini/skills/` |
| Qwen | `templates/qwen/` | `QWEN.md` + sub-agent definitions | `.qwen/mcp.json` | `.qwen/skills/` |
| OpenCode | `templates/opencode/` | `AGENTS.md` + permission-based agents | `.opencode/mcp.json` | `.opencode/skills/` |

### The Equalization Layer

All five agents receive the same skills (markdown instruction files). The template layer adapts the instruction format to each agent's conventions (system prompt vs. custom modes vs. agent files), but the underlying skill content is identical. This means a fix to a skill guide benefits all agents simultaneously.

**What equalization covers:**
- Skill content (all agents read the same `SKILL.md` and guide files)
- Iron Laws (embedded in every agent's instruction file)
- Constitution rules (enforced identically)
- MCP tool calls (same tools, same parameters)
- Output formats (same YAML routes, properties, test files)

**What equalization does NOT cover:**
- Dispatch mechanism (subagents vs. modes vs. inline)
- Tool restriction model (each agent's permission system is different)
- File reading patterns (context isolation varies)
- Parallelization strategy (only Claude supports parallel subagent dispatch)
- Configuration format (YAML modes, TOML policies, markdown frontmatter)

### Agent Traits

Traits are agent-specific instruction fragments that are appended to shared skill files during `camel-kit init`. They bridge the gap between the equalization layer (identical skills) and the per-agent template layer (different dispatch models).

**Location:** `camel-kit-core/src/main/resources/templates/traits/{agent}/`

**How it works:** During `camel-kit init --ai {agent}`, `DefaultGenerator.applyTraits()` scans the traits directory for the selected agent and appends each `.append.md` file to the corresponding skill file. Idempotent HTML comment sentinels (`<!-- TRAIT:{agent} -->` / `<!-- /TRAIT:{agent} -->`) prevent duplicate application on re-init.

**Two granularity levels:**

| Level | Path Pattern | Appended To |
|-------|-------------|-------------|
| SKILL.md-level (strategy) | `traits/{agent}/{skill-name}.append.md` | `{skills-dir}/{skill-name}/SKILL.md` |
| Guide-level (tactics) | `traits/{agent}/{skill-name}/{guide-name}.append.md` | `{skills-dir}/{skill-name}/guides/{guide-name}.md` |

**Example:** `traits/claude/camel-execute.append.md` appends Claude-specific instructions to `camel-execute/SKILL.md` -- parallel subagent dispatch via the `Agent` tool, worktree isolation via `EnterWorktree`, build health monitoring via `CronCreate`. The same skill on Gemini gets different trait content: named agent delegation, TOML policy guidance, batch context loading via `read_many_files`.

**What traits contain:** Agent-specific tool usage, dispatch strategies, state persistence mechanisms, and execution optimizations. Each trait is written for the specific agent's capabilities -- Claude traits reference `Agent`, `ScheduleWakeup`, `TaskCreate`; Gemini traits reference `save_memory`, `read_many_files`; Bob traits reference `switch_mode`, `insert_content`.

### Iron Laws

The four Iron Laws from `skills/shared/iron-laws.md` are embedded in each agent's instruction file:

1. **MCP Catalog Verification** -- every component, EIP, dataformat, and language must be verified via MCP before being written to any spec, TDD, or YAML file
2. **Constitution Compliance** -- every generated route must pass all 7 constitution rules (incorporates and enforces the constitution)
3. **No Code Without Design Approval** -- never generate implementation artifacts before the user has approved the design spec. Planning flows directly into execution (no separate plan approval gate).
4. **Spec Compliance Before Quality** -- always run spec compliance review before code quality review; wrong order wastes effort

### Subagent-Driven Execution

The `/camel-execute` pipeline relies on dispatching discrete units of work to isolated agents. The design principle: the agent that writes the code should never be the same agent that reviews it, and each task should start from a clean context with no residual assumptions from previous tasks.

Four of the five agents support this natively through **subagent dispatch**:

- **Claude Code** -- uses the `Agent` tool to spawn fresh subagents per task. Each subagent receives the task text, relevant TDD section, guide file paths, and MCP parameters. Before implementation, a `catalog-researcher` subagent batch-verifies all MCP catalog artifacts (research isolation). After implementation, an Adversarial Code Review dispatches parallel Critic Lanes (Route Architecture, Security, Performance, Boundary Compliance, Behavioral Equivalence) via a Moderator subagent, then a spec-compliance reviewer subagent checks the design spec, then a code-quality reviewer subagent checks constitution compliance. At the Stamp Gate, three reviewers run in parallel (spec, quality, security). Claude uniquely supports **parallel dispatch**: the route graph topology (from `camel-kit-graph`) identifies independent routes (no shared `direct:`, `seda:`, or `vm:` endpoints, no shared configuration properties), and independent tasks are dispatched simultaneously to multiple subagents.

- **Gemini CLI** -- dispatches via a unified `invoke_subagent` tool to 6 specialized subagents. The scheduler natively supports **parallel tool execution** via `Promise.all()` (default-parallel). However, subagents cannot invoke other subagents (hardcoded `Kind.Agent` filter), so `/camel-execute` runs in the **main agent context** where it can dispatch to all subagents. Within-wave parallelism is achieved through the scheduler batching multiple `invoke_subagent` calls.

- **Qwen** -- dual dispatch model: **named subagents** (clean context, parent blocks) and **forks** (inherit parent context, run in background). The fork model enables parallel review and research tasks. Read-only tools (Read, Search, Fetch) are concurrent with a configurable cap (max 10). The `"MUST BE USED for..."` phrasing in description fields forces automatic delegation.

- **OpenCode** -- 7 agents with granular, per-type glob permissions. The executor agent has `task: {"*": allow}` permission. Subagent-to-subagent delegation is now opt-in (PR #7756) with configurable depth limits and call budgets. LLM-level parallel tool calls are supported. Each agent has a `steps` limit (implementer: 50, executor: 100) that triggers graceful summarization rather than hard failure.

**IBM Project Bob does not support subagents.** It uses a fundamentally different architecture -- the **B+A (Behavior + Advanced) hybrid with mode switching**:

1. Each pipeline phase starts in **Advanced mode** (unrestricted), allowing the agent to read all skill files and project context
2. The first instruction in the gate file switches to a **restricted custom mode** (e.g., `camel-brainstorm`, `camel-implement`) with scoped tool permissions
3. The mode's tool group constrains what the AI can do for the remainder of that phase

This means Bob cannot isolate tasks into separate context windows or use independent reviewer agents. The compensation is that Bob's tool restrictions are **platform-enforced**, not instruction-based. During design, Bob's `camel-brainstorm` mode grants only `read`, `edit` (`.md` files only via `fileRegex`), `mcp`, and `browser` -- the AI physically cannot edit code files because the mode excludes the edit tool for non-markdown files. This is stricter than any instruction-based constraint, which the AI could rationalize away.

Bob also requires **monolithic gate files** (one per pipeline phase, 6-10 KB each) that inline complete orchestration logic, because it cannot chain skill references across mode switches the way subagent-based agents load skills into fresh contexts.

The trade-off table:

| Design Dimension | Subagent Dispatch | Mode Switching (Bob) |
|-----------------|-------------------|---------------------|
| Context isolation | Per-task (fresh subagent) | Per-session (accumulated) |
| Reviewer independence | Separate subagent | Same session self-reviews |
| Tool restriction mechanism | Instruction-based / tool whitelists / policies | Platform-enforced mode tool groups |
| Parallel execution | Claude (graph topology), Gemini (scheduler `Promise.all()`), Qwen (fork), OpenCode (LLM-level) | Not possible |
| Skill loading | Loaded into subagent context on dispatch | Inlined in monolithic gate files |
| Template complexity | 3-12 files per agent | 17+ files (gates + rules + modes) |
| Failure isolation | Subagent failure doesn't affect other tasks | Phase failure affects entire session |

### Per-Agent Summary

| Agent | Dispatch Model | Key Differentiator |
|-------|---------------|-------------------|
| Claude Code | Parallel subagent dispatch | Route graph topology, research isolation, parallel fan-out, adversarial code review |
| IBM Project Bob | B+A hybrid with 5 custom modes | Monolithic gate files, 3 checkpoint types |
| Gemini CLI | `invoke_subagent` + parallel scheduler | Default-parallel `Promise.all()`, TOML policy, MCP wildcards, A2A remote agents |
| Qwen | Dual dispatch (named + fork) | Fork background tasks, DashScope cache sharing, auto-delegation |
| OpenCode | `task` child sessions + opt-in delegation | 14 permission types, glob patterns, configurable depth limits |

For full per-agent deep dives (template files, tool restriction models, configuration examples, unique capabilities), see **[Agent Architectures](agent-architectures.md)**.

### Adding a New Agent

To add support for a new AI coding assistant:

1. Create a template directory: `templates/{agent-name}/`
2. Implement `{Agent}Generator extends DefaultGenerator` in `io.github.luigidemasi.camelkit.generator`
3. Register in `AgentGeneratorFactory` (`{agent-name}` → `{Agent}Generator`)
4. Generate the agent's instruction file with embedded iron laws and skill references
5. Map pipeline phases to the agent's native dispatch mechanism (modes, subagents, permissions, etc.)
6. Add MCP configuration for the agent's MCP config format
7. Write tests following existing patterns (verify structure + key content markers)

The `DefaultGenerator` provides shared logic (skill file copying, MCP config generation). Each agent-specific generator overrides template generation to produce the agent's native format.

---

## 6. MCP Integration

### Camel Catalog MCP

The Camel MCP server (`camel-jbang-mcp`) provides **15 tools** organized into 6 categories:

#### 1. Catalog Exploration (8 tools)

| Tool Name | Purpose |
|-----------|---------|
| `camel_catalog_components` | List Camel components with filtering by name, label, runtime |
| `camel_catalog_component_doc` | Get comprehensive component documentation |
| `camel_catalog_dataformats` | List data formats (JSON, XML, CSV, etc.) |
| `camel_catalog_dataformat_doc` | Get data format configuration options |
| `camel_catalog_languages` | List expression languages (Simple, JsonPath, XPath, JQ) |
| `camel_catalog_language_doc` | Get expression language documentation |
| `camel_catalog_eips` | List Enterprise Integration Patterns |
| `camel_catalog_eip_doc` | Get EIP documentation and configuration |

#### 2. Kamelet Catalog (2 tools)

| Tool Name | Purpose |
|-----------|---------|
| `camel_catalog_kamelets` | List available Kamelets with filtering |
| `camel_catalog_kamelet_doc` | Get Kamelet documentation and dependencies |

#### 3. Route Understanding (1 tool)

| Tool Name | Purpose |
|-----------|---------|
| `camel_route_context` | Extract components and EIPs from route (YAML/XML/Java) |

#### 4. Security Analysis (1 tool)

| Tool Name | Purpose |
|-----------|---------|
| `camel_route_harden_context` | Analyze routes for security concerns (47 checks) |

#### 5. Validation and Transformation (2 tools)

| Tool Name | Purpose |
|-----------|---------|
| `camel_validate_route` | Validate endpoint URIs against catalog schema |
| `camel_transform_route` | Convert routes between YAML and XML formats |

#### 6. Version Management (1 tool)

| Tool Name | Purpose |
|-----------|---------|
| `camel_version_list` | List Camel versions with LTS status and JDK requirements |

### Knowledge Layer MCP

Separate from the Camel Catalog MCP, the knowledge layer runs from the `camel-kit-knowledge` repository (a separate project with its own version line).

| Tool Name | Purpose |
|-----------|---------|
| `camel_knowledge_search` | Semantic search across Apache Camel documentation |

### Tool Usage by Skill

| Skill | Tools Used | Count |
|-------|------------|-------|
| `camel-brainstorm` | `camel_version_list`, `camel_catalog_*` (all 8) | 9 |
| `camel-brainstorm` | `camel_catalog_components`, `camel_catalog_component_doc`, `camel_catalog_eips`, `camel_catalog_eip_doc`, `camel_catalog_dataformats`, `camel_catalog_dataformat_doc`, `camel_catalog_languages`, `camel_catalog_language_doc` | 8 |
| `camel-migrate` | Same as `camel-brainstorm` (Phase 2) | 8 |
| `camel-implement` | `camel_catalog_component_doc`, `camel_catalog_dataformat_doc`, `camel_catalog_eip_doc`, `camel_catalog_language_doc`, `camel_route_context`, `camel_validate_route` | 6 |
| `camel-validate` | `camel_validate_route`, `camel_route_harden_context` | 2 |
| `camel-test` | `camel_route_context`, `camel_catalog_component_doc` | 2 |
| `camel-knowledge` | `camel_knowledge_search` | 1 |

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

The verification pipeline (`camel-verify`) is a 3-phase feedback loop that builds and tests the generated application using Citrus integration tests. It runs as an internal subagent within `camel-execute`, not as a standalone pipeline stage. After verification completes inside execute, `camel-validate` runs as the final pipeline stage.

### Phases

1. **Build Verification** -- compile the project with `./mvnw`, classify and fix build errors (skipped for JBang runtime)
2. **Test Verification** -- run Citrus YAML integration tests via `camel test run`, classify and fix test failures. Citrus tests are self-contained: Testcontainers start external services, `camel:jbang:run` starts the Camel integration, send/receive actions validate behavior.
3. **Report** -- structured summary of all phases, fixes applied, and issues found

Each phase has an independent iteration budget of **max 15 attempts**. On each iteration, errors are classified and routed to the appropriate fix strategy.

### Environment Probe

Before the verify loop runs, `camel-execute` performs an **environment probe** as its first step. The probe generates a throwaway skeleton (pom.xml, docker-compose, empty route) and checks dependency resolution, Docker service availability, and runtime startup. Failures are classified as **mechanical** (auto-fix and re-probe) or **architectural** (trigger re-plan loop). Mechanical failures route to the automated self-repair path (fix and re-probe without entering the re-plan loop). Architectural failures trigger the re-plan loop, which modifies affected TDDs and re-executes.

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
5. **re-plan** -- trigger the re-plan loop for architectural failures (modifies affected TDDs, max 3 rounds)
6. **Escalate to user** -- when the error is outside the pipeline's control

`re-plan` is not a separate error category -- it is a promotion destination. When the same error class persists after failed fix attempts, the error promotes to re-plan via the two-tier promotion model (Tier 1: MCP confirms structural after 1 attempt; Tier 2: 3 failed attempts on same error class).

### Re-Plan Promotion

When fix attempts fail persistently, errors promote to re-planning via a two-tier model:

- **Tier 1 (immediate):** After 1 failed fix, MCP catalog confirms the failure is structural (component doesn't exist for this runtime). Triggers re-plan immediately.
- **Tier 2 (progressive):** After 3 failed fix attempts on the same error class. Triggers re-plan.

The re-plan loop modifies affected TDD(s) only (never the BRD), max 3 rounds, with short-circuit on same failure class.

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

Before this fix, XSLT generation varied between runs because the LLM would re-derive XPaths differently each time from the same schema. The solution: pre-compute Source XPaths and Target Elements once during the canonicalize stage (design time), store them in the TDD, and use them verbatim during implementation. The key insight is that for LLM code generation, providing the exact template per case produces consistent output -- never a single template with conditional rules.

### Version Alignment

Camel-Kit defaults to the latest Apache Camel version, configured in `distribution.properties` (the single source of truth for all version numbers and MCP settings). Users can override any property via `-p key=value` CLI flags or a custom config file (`-c path`). Component availability is verified via the MCP catalog layer.

### Multi-Agent Parity

Skills are markdown instruction files -- the same skill works across all five supported agents. Agent-specific differences (subagent dispatch vs. custom modes vs. inline execution) are handled by the template layer, not the skill layer. A bug fix or improvement to a guide file benefits every agent.

### Constitution vs Iron Laws

The **Constitution** defines 7 route quality rules (what makes a good route): route structure, single responsibility, separation of concerns, naming conventions, observability, external configuration, component support verification.

The **Iron Laws** define pipeline process enforcement rules (how the pipeline operates): MCP verification, constitution compliance, no code without design approval, spec compliance before quality.

Iron Law 3 explicitly incorporates and enforces the 7 constitution rules. They are complementary, not overlapping -- the constitution says what to check, the iron laws say when and how to enforce it.

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

**Note:** Only `camel-start` should have `user_invocable: true`. All other skills have `user_invocable: false`. Slash commands still work independently of this metadata.

3. **Write guide files** in `guides/`. Each guide is a self-contained markdown instruction file loaded by the agent when the skill is active.

4. **If registering slash commands:** update agent templates to register the command. Each agent needs the slash command added to its instruction file:
   - Claude Code: update `templates/claude/claude-md.md`
   - IBM Project Bob: update `templates/bob/custom_modes.yaml` and add rules directory
   - Gemini CLI: update `templates/gemini/gemini-md.md`
   - Qwen: update `templates/qwen/qwen-md.md`
   - OpenCode: update `templates/opencode/agents-md.md`

5. **If internal:** update the loading skill's `SKILL.md` to reference the new guides (e.g., add a guide reference to `camel-execute`'s guide manifest).

6. **Update `docs/commands.md`** if the skill is user-invocable.
