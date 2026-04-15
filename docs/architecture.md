# Camel-Kit Architecture Guide

This document describes Camel-Kit's internal architecture for contributors and extenders. For user-facing documentation, see the [User Guide](user-guide.md) and [Command Reference](commands.md).

---

## 1. Overview

Camel-Kit combines two mechanisms to give AI agents accurate, efficient access to the Apache Camel ecosystem:

- **Skills** -- markdown instruction files that guide LLM agents through structured workflows (design, implementation, validation, testing, verification)
- **MCP Server** -- real-time queries against the live Camel catalog for component verification, validation, and security analysis

Together they enable AI-powered integration development targeting the Red Hat Build of Apache Camel. Skills carry the process knowledge (how to design a flow, how to generate YAML, how to validate a route), while MCP provides the data knowledge (which components exist, what options they accept, whether an endpoint URI is valid).

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
user_invocable: true
---

# /camel-{name}

> One-line purpose

## Guides

| Guide | When Loaded | Purpose |
|-------|-------------|---------|
| `guides/main-guide.md` | Always | Primary instruction guide |
| `guides/optional-guide.md` | When condition X | Supplementary guide |
```

The frontmatter fields:
- `name` -- skill identifier, used in cross-references
- `description` -- trigger keywords that help agents match user intent to the correct skill
- `user_invocable` -- `true` if users can invoke it directly via slash command; `false` if it is loaded by other skills

### All Skills

| Skill | User-Invocable | Loaded By | Purpose |
|-------|---------------|-----------|---------|
| `camel-design` | Yes | -- | Orchestrate design phase: interview user, produce BRD + TDDs |
| `camel-plan` | Yes | `camel-design` (after spec approval) | Produce detailed implementation plan from approved design spec |
| `camel-execute` | Yes | `camel-plan` (after plan approval) | Dispatch subagents per task with two-stage review |
| `camel-migrate` | Yes | -- | Migration entry point: shortcut into `camel-design` with project type pre-set |
| `camel-verify` | Yes | `camel-execute` (after all tasks) | 5-phase runtime verification loop |
| `camel-design-reference` | No | `camel-design` | Guides for component selection, EIP catalog, TDD assembly |
| `camel-implement` | No | `camel-execute` | Guides for YAML generation, properties, Docker Compose, DataMapper |
| `camel-validate` | No | `camel-execute` | Guides for schema validation, endpoint verification, security analysis |
| `camel-test` | No | `camel-execute` | Guides for route analysis and test generation with Citrus + Testcontainers |
| `camel-knowledge` | No | `camel-design`, `camel-execute` | Routes questions to Red Hat knowledge MCP tools |

### Shared Guides

Shared guides live at `camel-kit-core/src/main/resources/skills/shared/` and are loaded by multiple skills:

| Guide | Purpose |
|-------|---------|
| `iron-laws.md` | Five non-negotiable pipeline process enforcement rules |
| `datamapper-canonicalize.md` | Engine selection and field mapping enrichment for DataMapper |
| `flow-test-data.md` | Test data generation patterns for flow design |
| `mcp-setup.md` | MCP version mapping and connection parameters |
| `graph-availability.md` | Graph MCP server availability detection |
| `yaml-structure.md` | YAML DSL structure rules and Kaoto compatibility |
| `yaml-components.md` | Component URI syntax and parameter rules |
| `yaml-examples.md` | Reference YAML patterns for common integrations |
| `patterns-foundational.md` | Foundational EIP patterns (content-based routing, splitter, aggregator) |
| `patterns-error-handling.md` | Error handling patterns (dead letter channel, retry, circuit breaker) |
| `patterns-deployment.md` | Deployment patterns (health checks, graceful shutdown, scaling) |

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

Entry points diverge (`camel-design` for greenfield, `camel-migrate` for migration) but both produce the same artifact format -- a BRD (Business Requirements Document) with TDDs (Technical Design Documents). This means `camel-plan` and `camel-execute` work identically regardless of whether the project is greenfield or migrated.

### How camel-execute Dispatches Work

1. Read the approved implementation plan and extract all tasks
2. For each task:
   - Dispatch an implementer subagent with full task text, design spec section, guide paths, and MCP parameters
   - On completion, dispatch a **spec compliance reviewer** -- does the output match the design spec?
   - If spec review passes, dispatch a **code quality reviewer** -- constitution compliance, security, anti-patterns
   - If either reviewer finds critical issues, return to the implementer for fixes, then re-review
   - Mark task complete and immediately start the next task (no pause, no user confirmation)
3. After all tasks: run a **cross-cutting review** across all generated routes
4. Run the **verification phase** (`camel-verify`)
5. Print the completion summary

### Agent-Specific Execution

The dispatch model varies by AI agent:

- **Claude Code** -- dispatches fresh subagents per task. Each subagent runs in isolated context with no cross-contamination between tasks.
- **IBM Project Bob** -- switches between custom modes (`brainstorm`, `plan`, `implement`, `validate`, `test`) with scoped tool permissions per mode.
- **Gemini CLI, Qwen, OpenCode** -- inline execution within the same session. Skills are loaded as instruction context rather than dispatched as separate agents.

### The BRD+TDD Contract

Both `camel-design` (greenfield) and `camel-migrate` (migration) produce the same output format: a BRD with per-flow TDDs. This is the contract between design and implementation -- `camel-plan` consumes this format, and `camel-execute` implements from it. The design phase diverges (interview vs. source analysis), but the output converges.

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

### Iron Laws

The five Iron Laws from `skills/shared/iron-laws.md` are embedded in each agent's instruction file:

1. **MCP Catalog Verification** -- every component, EIP, dataformat, and language must be verified via MCP before being written to any spec, TDD, or YAML file
2. **Red Hat Build Only** -- only Red Hat supported versions and components; community-only versions are forbidden
3. **Constitution Compliance** -- every generated route must pass all 7 constitution rules (incorporates and enforces the constitution)
4. **No Code Without Spec Approval** -- never generate implementation artifacts before the user has approved the design spec
5. **Spec Compliance Before Quality** -- always run spec compliance review before code quality review; wrong order wastes effort

### Subagent-Driven Execution

The `/camel-execute` pipeline relies on dispatching discrete units of work to isolated agents. The design principle: the agent that writes the code should never be the same agent that reviews it, and each task should start from a clean context with no residual assumptions from previous tasks.

Four of the five agents support this natively through **subagent dispatch**:

- **Claude Code** -- uses the `Agent` tool to spawn fresh subagents per task. Each subagent receives the task text, relevant TDD section, guide file paths, and MCP parameters. After implementation, a separate reviewer subagent checks spec compliance, then a third checks code quality. Claude uniquely supports **parallel dispatch**: the route graph topology (from `camel-kit-graph`) identifies independent routes (no shared `direct:`, `seda:`, or `vm:` endpoints, no shared configuration properties), and independent tasks are dispatched simultaneously to multiple subagents.

- **Gemini CLI** -- dispatches to 6 specialized subagents (brainstormer, planner, implementer, validator, tester, migrator). However, `/camel-execute` runs in the **main agent context** because Gemini prevents recursive subagent invocation -- a subagent cannot dispatch another subagent. The main agent orchestrates by dispatching individual tasks to the implementer subagent, then to the validator subagent, sequentially.

- **Qwen** -- 7 sub-agents with description-based auto-delegation. The `"MUST BE USED for..."` phrasing in each sub-agent's description forces Qwen to automatically route work to the correct sub-agent based on intent keywords. The executor sub-agent has access to the `task` tool for dispatching work to other sub-agents.

- **OpenCode** -- 7 agents with granular, per-type glob permissions. The executor agent has `task: {"*": allow}` permission, enabling it to dispatch work to the implementer, validator, and tester agents. Each agent has a `steps` limit (implementer: 50, executor: 100) that triggers graceful summarization rather than hard failure.

**IBM Project Bob does not support subagents.** It uses a fundamentally different architecture -- the **B+A (Behavior + Advanced) hybrid with mode switching**:

1. Each pipeline phase starts in **Advanced mode** (unrestricted), allowing the agent to read all skill files and project context
2. The first instruction in the gate file switches to a **restricted custom mode** (e.g., `camel-design`, `camel-implement`) with scoped tool permissions
3. The mode's tool group constrains what the AI can do for the remainder of that phase

This means Bob cannot isolate tasks into separate context windows or use independent reviewer agents. The compensation is that Bob's tool restrictions are **platform-enforced**, not instruction-based. During design, Bob's `camel-design` mode grants only `read`, `edit` (`.md` files only via `fileRegex`), `mcp`, and `browser` -- the AI physically cannot edit code files because the mode excludes the edit tool for non-markdown files. This is stricter than any instruction-based constraint, which the AI could rationalize away.

Bob also requires **monolithic gate files** (one per pipeline phase, 6-10 KB each) that inline complete orchestration logic, because it cannot chain skill references across mode switches the way subagent-based agents load skills into fresh contexts.

The trade-off table:

| Design Dimension | Subagent Dispatch | Mode Switching (Bob) |
|-----------------|-------------------|---------------------|
| Context isolation | Per-task (fresh subagent) | Per-session (accumulated) |
| Reviewer independence | Separate subagent | Same session self-reviews |
| Tool restriction mechanism | Instruction-based / tool whitelists / policies | Platform-enforced mode tool groups |
| Parallel execution | Claude only (graph topology) | Not possible |
| Skill loading | Loaded into subagent context on dispatch | Inlined in monolithic gate files |
| Template complexity | 3-12 files per agent | 17+ files (gates + rules + modes) |
| Failure isolation | Subagent failure doesn't affect other tasks | Phase failure affects entire session |

### Per-Agent Summary

| Agent | Dispatch Model | Key Differentiator |
|-------|---------------|-------------------|
| Claude Code | Parallel subagent dispatch | Route graph topology for parallelization |
| IBM Project Bob | B+A hybrid with 5 custom modes | Monolithic gate files, 3 checkpoint types |
| Gemini CLI | 6 subagents + main-agent execute | `@file.md` imports, TOML policy engine, MCP wildcards |
| Qwen | 7 sub-agents with auto-delegation | Description matching, template variables |
| OpenCode | 7 agents with granular permissions | 14 permission types, glob patterns, path-scoped edits |

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
| `camel_rh_build_component_info` | Component support status lookup (Production Support, Technology Preview, etc.) |
| `camel_rh_build_search` | Semantic search across Red Hat Build documentation, errata, and CVEs |

Data sources: product guides (HTML from docs.redhat.com), KB articles, errata (RHSA/RHBA/RHEA), CVEs enriched with CVSS/CWE data. The knowledge index contains approximately 26,000 chunks with hybrid semantic search (20% BM25 + 80% vector).

### Tool Usage by Skill

| Skill | Tools Used | Count |
|-------|------------|-------|
| `camel-design` | `camel_version_list`, `camel_catalog_*` (all 8) | 9 |
| `camel-design` | `camel_catalog_components`, `camel_catalog_component_doc`, `camel_catalog_eips`, `camel_catalog_eip_doc`, `camel_catalog_dataformats`, `camel_catalog_dataformat_doc`, `camel_catalog_languages`, `camel_catalog_language_doc` | 8 |
| `camel-migrate` | Same as `camel-design` (Phase 2) | 8 |
| `camel-implement` | `camel_catalog_component_doc`, `camel_catalog_dataformat_doc`, `camel_catalog_eip_doc`, `camel_catalog_language_doc`, `camel_route_context`, `camel_validate_route` | 6 |
| `camel-validate` | `camel_validate_route`, `camel_route_harden_context` | 2 |
| `camel-test` | `camel_route_context`, `camel_catalog_component_doc` | 2 |
| `camel-knowledge` | `camel_rh_build_component_info`, `camel_rh_build_search` | 2 |

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

The verification pipeline (`camel-verify`) is a 5-phase feedback loop that builds, starts, and behaviorally tests the generated application.

### Phases

1. **Environment Preparation** -- start external services via `docker-compose`
2. **Build Verification** -- compile the project with `./mvnw`, classify and fix build errors
3. **Startup Verification** -- start the application, classify and fix startup errors
4. **Behavioral Verification** -- send test data, compare output, fix mismatches
5. **Report** -- structured summary of all phases, fixes applied, and issues found

Each phase has an independent iteration budget of **max 15 attempts**. On each iteration, errors are classified and routed to the appropriate fix strategy.

### Error Taxonomy

14 error patterns organized by fix target:

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

### Behavioral Verification

Uses `camel cmd send` to inject test payloads into the running application. Reads from sinks and performs semantic comparison: field-by-field matching that ignores key ordering and whitespace differences.

### Fix Routing

Errors route to one of four destinations:

1. **Self-repair** -- fix pom.xml, application.properties, or docker-compose directly
2. **camel-validate** -- route to validation skill for endpoint URI fixes
3. **camel-implement** -- route to implementation skill for route logic fixes
4. **Escalate to user** -- when the error is outside the pipeline's control (build tool issues, Quarkus augmentation failures)

---

## 8. Key Design Decisions

### DataMapper Consistency Fix (Feb 2026)

Before this fix, XSLT generation varied between runs because the LLM would re-derive XPaths differently each time from the same schema. The solution: pre-compute Source XPaths and Target Elements once during the canonicalize stage (design time), store them in the TDD, and use them verbatim during implementation. The key insight is that for LLM code generation, providing the exact template per case produces consistent output -- never a single template with conditional rules.

### Red Hat Build Alignment

Camel-Kit defaults to Red Hat Build of Apache Camel versions. Component support is verified via the MCP knowledge layer (`camel_rh_build_component_info`). For catalog MCP calls, the `.redhat-XXXXX` suffix is stripped from version numbers because Maven Central (which the catalog queries) only has community version numbers.

### Multi-Agent Parity

Skills are markdown instruction files -- the same skill works across all five supported agents. Agent-specific differences (subagent dispatch vs. custom modes vs. inline execution) are handled by the template layer, not the skill layer. A bug fix or improvement to a guide file benefits every agent.

### Constitution vs Iron Laws

The **Constitution** defines 7 route quality rules (what makes a good route): route structure, single responsibility, separation of concerns, naming conventions, observability, external configuration, component support verification.

The **Iron Laws** define 5 pipeline process enforcement rules (how the pipeline operates): MCP verification, Red Hat Build only, constitution compliance, no code without spec approval, spec compliance before quality.

Iron Law 3 explicitly incorporates and enforces the 7 constitution rules. They are complementary, not overlapping -- the constitution says what to check, the iron laws say when and how to enforce it.

---

## 9. How to Add a Skill

### Steps

1. **Create directory:** `camel-kit-core/src/main/resources/skills/camel-{name}/`

2. **Write `SKILL.md`** with YAML frontmatter and guides table:

```markdown
---
name: camel-{name}
description: Brief description with trigger keywords
user_invocable: true
---

# /camel-{name}

> One-line purpose

## Guides

| Guide | When Loaded | Purpose |
|-------|-------------|---------|
| `guides/main-guide.md` | Always | Primary instruction guide |
```

3. **Write guide files** in `guides/`. Each guide is a self-contained markdown instruction file loaded by the agent when the skill is active.

4. **If user-invocable:** update agent templates to register the command. Each agent needs the slash command added to its instruction file:
   - Claude Code: update `templates/claude/claude-md.md`
   - IBM Project Bob: update `templates/bob/custom_modes.yaml` and add rules directory
   - Gemini CLI: update `templates/gemini/gemini-md.md`
   - Qwen: update `templates/qwen/qwen-md.md`
   - OpenCode: update `templates/opencode/agents-md.md`

5. **If internal:** update the loading skill's `SKILL.md` to reference the new guides (e.g., add a guide reference to `camel-execute`'s guide manifest).

6. **Update `docs/commands.md`** if the skill is user-invocable.
