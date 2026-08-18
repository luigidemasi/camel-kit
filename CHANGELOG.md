# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).


## [Unreleased]

### Added

- **OpenAI Codex CLI AI target (`--ai codex`)** — added a first-class Codex target with native repository assets.
  - Generated workspaces include `AGENTS.md`, `.agents/skills/`, `.codex/config.toml`, and seven `.codex/agents/*.toml` roles without an unused command directory
  - Codex skill references use native `$camel-*` invocation, with `/skills`, `$camel-start`, and `/mcp` guidance
  - Repository-scoped TOML config preserves unrelated settings and configures Camel, Camel Knowledge, and Citrus with exact tool allowlists and prompt approval defaults
  - Doctor validates Codex skills, custom-agent TOML, MCP tables, and least-privilege defaults; an isolated Codex CLI smoke verifies instruction, skill, agent, and MCP discovery

- **Pi AI target (`--ai pi`)** — added a first-class Pi target for `@earendil-works/pi-coding-agent`.
  - New `pi` agent registry descriptor, generator strategy, and `PiGenerator`
  - Generated workspaces include `AGENTS.md`, `.pi/skills/`, `.pi/prompts/`, `.mcp.json`, `.pi/extensions/camel-kit-guard.ts`, and `.pi/camel-kit-guard-policy.json`
  - Pi MCP config uses `pi-mcp-adapter` with `directTools` allowlists, and `doctor` validates the Pi schema plus guard resources
  - Internal guide skills are marked with `disable-model-invocation: true` and future-proof agent alias `user-invocable: false`
  - Distribution defaults record the tested Pi and adapter versions

- **GitHub Copilot CLI AI target (`--ai copilot`)** — added a first-class Copilot CLI target that generates GitHub-native project assets.
  - New `copilot` agent registry descriptor, generator strategy, and `CopilotGenerator`
  - Generated workspaces include `.github/copilot-instructions.md`, `.github/skills/`, `.github/agents/`, `.github/mcp.json`, and `.github/hooks/camel-kit-safety.json`
  - Copilot MCP config uses the documented `tools` schema while `doctor` continues to validate existing `autoApprove`/`alwaysAllow` configs for other agents; wildcard Copilot tool filters produce a least-privilege warning
  - Internal Copilot guide skills are marked so Copilot does not directly or automatically invoke them
  - README, command reference, user guide, architecture docs, agent architecture guide, and changelog document the Copilot target and skill-based invocation model

### Changed

- **Ship harness entry points now delegate to the local controller** — `/camel-ship`, `$camel-ship`, and `/skill:camel-ship` forward their arguments to the configured registered `camel-kit ship` or `camel kit ship` command instead of maintaining a prompt-owned workflow. The local controller is the sole owner of Ship stages, run state, evidence, oversight, and guarded publication.
  - Existing generated workspaces must be regenerated with the same command surface and agent, using `camel-kit init --here --ai <same-agent> --force` or `camel kit init --here --ai <same-agent> --force`; commit or back up workspace customizations first because `--force` rewrites generated configuration, instructions, skills, and templates
  - Initialization aborts up front — before writing any project files — when a managed agent path (for example a symlinked `.claude` or `.bob` from a dotfiles setup) is a symbolic link; the error names the link. Replace the link with a real directory before running the upgrade command
  - IBM Bob and Bob 2 Ship commands forward the invocation options in prose because IBM Bob documents only positional `$1`/`$2` command placeholders; Gemini and Qwen Ship commands interpolate their documented `{{args}}` placeholder
  - Re-initialization removes obsolete Ship guides, harness traits, and Bob 2 Ship mode/rule assets
  - Pre-controller `.camel-kit/ship-state.json` and non-manual `.camel-kit/pipeline.json` state is intentionally not resumable and must be archived outside the project before starting Ship; manual-mode `.camel-kit/pipeline.json` remains supported by standalone pipeline skills and validated `--start-from` imports
  - GitHub Copilot CLI uses native project skills under `.github/skills/` without generating unsupported `.github/commands/`; older command files are inert and may be removed after preserving local edits
  - Pi exposes Ship through `/skill:camel-ship` and removes the older `.pi/prompts/camel-ship.md` alias, whose argument expansion could flatten quoted option values

- **Default Camel version updated to 4.21.0** — Camel Main and Spring Boot runtimes now default to Camel `4.21.0`, with the Spring Boot framework mapped to `4.1.0` (`spring.boot.4.21.0=4.1.0`, matching camel-parent 4.21.0). Supported version lists for Main and Spring Boot move to `4.21.0, 4.18.3, 4.14.7` (LTS fix release 4.18.3 maps to Spring Boot `3.5.16`).
  - Camel MCP server now pinned to the released `4.21.0` instead of `4.21.0-SNAPSHOT`
  - Compiled-in fallback defaults in `DistributionConfig` kept in lockstep with `distribution.properties`
  - `spring.boot.4.20.0` mapping removed — 4.20 is not an LTS line; 4.21.0 is the current latest release alongside LTS `4.18.3` and `4.14.7`
  - Quarkus runtime stays on Camel `4.18.2` — the latest camel-quarkus release (3.33.1, Quarkus platform 3.33.2.x) still bundles Camel 4.18.2

- **Default AI target changed to IBM Bob 2** — `camel-kit init` and `camel kit init` now default to `--ai bob2` when no `--ai` option is supplied.
  - CLI help and documentation now mark Bob 2 as the default target
  - `--ai bob` remains supported for IBM Bob 1 legacy workspaces
  - Selecting `--ai bob` emits a non-blocking legacy warning recommending `--ai bob2` for new IBM Bob projects

- **Bob documentation split by generation** — README, user guide, command reference, and architecture docs now describe `--ai bob` as IBM Bob 1 legacy support and `--ai bob2` as IBM Bob 2 support.
  - Bob 1 mode/gate architecture remains documented as legacy behavior
  - Bob 2 documentation describes native subagents and no longer inherits broad "Bob does not support subagents" language
  - The "Adding a New Agent" architecture guide now includes registry descriptor and `camel-kit doctor` validation steps

- **Progressive skill loading via meta-router** — introduced `/camel-start` as the single auto-discovered skill that routes users into two pipelines (greenfield: brainstorm → plan → execute → verify, migration: migrate → plan → execute → verify). All other skills set to `user_invocable: false` — slash commands still work as on-demand loaders. Context baseline reduced from ~1,260 to ~110 tokens (91% reduction).
  - New `camel-start/SKILL.md` with decision tree, "When NOT to use" table, pipeline overview, and Tier 2 utility references
  - AGENTS.md rewritten to ultra-minimal bootstrap (~80 tokens): compressed iron laws + entry point directive
  - Skill tiering: Tier 1 (pipeline: brainstorm, migrate, plan, execute, verify), Tier 2 (utilities: validate, ship, knowledge), Internal (guide libraries: design, implement, test)

### Removed

- **`/camel-flow` skill** — redundant 14-line redirect to `/camel-brainstorm` with greenfield preset, now handled by `/camel-start` routing

### Fixed

- **Citrus MCP startup (#147)** — downgraded the generated MCP runner from `5.0.0-M2`, which fails during Quarkus startup with an incompatible JSON Schema Generator dependency, to the verified working `5.0.0-M1` release. Citrus test schemas and dependencies remain on `5.0.0-M2`.

- **Adversarial review findings (#126)** — hardened graph building, init/doctor contracts, generator failure handling, distribution assets, and shipped skill content:
  - Secure XML parsing (XXE/DTD disabled) in `XmlRouteParser` and `MuleXmlFlowParser`; parser failures and warnings now surface through `graph generate`, `doctor`, and `init` instead of producing silently empty graphs
  - `GraphSerializer.read` validates format version and required fields; graph visualizer escapes embedded JSON against `</script>` injection
  - `init` persists `project.runtime`, `project.camelVersion`, and `project.platformBomVersion` (spring-boot projects additionally get `project.springBootVersion`); `doctor` validates them
  - Missing MCP config, skill resources, templates, and dispatch blocks now fail init loudly instead of degrading to warnings; `plan analyze` exits non-zero with a JSON error on failure
  - `doc stale`/`unstale` preserve unknown frontmatter keys and fail closed on malformed staleness metadata
  - Mule `flow-ref` targets resolve across files regardless of parse order; DataWeave node IDs unified on classpath-relative paths so Mule references and `.dwl` scans converge on one node
  - JBang launcher ships snapshot repositories; removed broken `camel-kit-aio` alias; fixed the JBang plugin `init` forwarding (`--force`, shared `CamelKitMain`) and the plugin GAV in the README
  - Corrected shipped Camel YAML guidance (steps under `from:`, `enrich`/`pollEnrich` expressions, `idempotentRepository`, `mimeMultipart`, `json` + `library: Jackson`, circuit-breaker seconds, `toD` for dynamic URIs) and removed stale migration/removal claims (`spel`, `mvel`, `hl7terser`, `activemq`, `pgevent`, `xstream`)
  - Distribution defaults, compiled-in fallbacks, and tests now share the single repo-root `distribution.properties` (stale test fixture removed)

- **Skill pipeline contract drift** — aligned shipped skills, templates, personas, and docs on the active `docs/camel-kit/<PIPELINE_ID>/` artifact model, deterministic Spring Boot version mappings, lowercase test-data flow tokens, and design-spec terminology.
- **`camel-kit doctor` Bob 2 MCP validation** — doctor now resolves MCP config paths through the agent registry descriptor instead of a duplicated hard-coded switch, so Bob 2 projects validate `.bob/mcp.json` correctly.
- **Incorrect relative path in Bob test template** — `camel-test.md` used `../main/resources/` instead of `../../main/resources/` for route YAML references in test examples
- **Stale body text in `camel-validate` and `camel-knowledge`** — both had "NOT user-invocable" text contradicting their actual invocability via slash commands

### Added

- **Citrus MCP integration for test generation** — generated agent MCP configs now include the published Citrus MCP server (`org.citrusframework:citrus-mcp-server:5.0.0-M1`) so `camel-test` can verify Citrus YAML actions, endpoints, and schemas during test generation.
  - Added Citrus distribution properties (`citrus.version`, `citrus.mcp.version`, `citrus.mcp.repos`)
  - `--citrus-version default` now resolves to `5.0.0-M2`
  - Generated project config records `citrus.version`
  - Citrus MCP is preferred over cached quick references, with same-version cache fallback only

- **IBM Bob 2 AI target (`--ai bob2`)** — added a new Bob 2 target while preserving `--ai bob` as the IBM Bob 1 legacy path.
  - New `bob2` agent registry descriptor, generator strategy, and `Bob2Generator`
  - Bob 2 workspaces still generate under `.bob/` with `.bob/commands`, `.bob/skills`, `.bob/custom_modes.yaml`, and `.bob/mcp.json`
  - Bob 2 custom modes use the current Bob 2 tool groups (`read`, `edit`, `execute`, `mcp`, `skill`, `todo`, `artifact`, `subagent`, `mode`) with `allowedSubagents`
  - New Bob 2 rules, dispatch template, and traits for native `spawn_subagent` orchestration with `explore`, `general`, and `fork_context`
  - Bob 2 command stubs include markdown frontmatter from workflow metadata, including `description` and argument hints
  - Bob 2 generated skills keep the shared `SKILL.md` content and append Bob 2 traits instead of replacing skills with Bob 1 monolithic gates
  - Bob 2 skill metadata includes Bob-readable `user-invocable` aliases while preserving existing metadata for other agents

- **Bob 2 coverage and regression tests** — added registry, factory, generator, command-frontmatter, skill-metadata, custom-mode, doctor, and CLI default tests for Bob 2, plus explicit guards that legacy Bob 1 output remains unchanged.

- **Project graph analysis (`camel-kit-graph` module)** — new module that builds an in-memory graph of an entire Camel project and exposes it through CLI commands
  - 9 parsers: `YamlRouteParser`, `XmlRouteParser`, `JavaGraphParser`, `GroovyGraphParser`, `ConfigParser`, `PomParser`, `MuleXmlFlowParser`, `DataWeaveParser`, `CrossLinker` (for direct/seda, component, and config cross-references)
  - Graph model: `ProjectGraph` container with `GraphNode`, `GraphEdge`, `NodeType`, `EdgeType` records; JSON round-trip via `GraphSerializer`
  - Query engine: `GraphQuery` (find, neighbors, path, subgraph, impact, stats), `RouteFlowTracer`, `RouteTopology`, `DeadCodeAnalyzer` (unused deps, orphaned routes, stale config)
  - `GraphBuilder` orchestrator with parallel parsing across all file types
  - `GraphVisualizer` — generates interactive HTML visualizations with 4 library options: Cytoscape, D3, vis-network, AntV G6

- **Graph CLI commands** — 15 commands under `camel-kit graph`:
  - Navigation: `find`, `neighbors`, `path`, `subgraph`
  - Camel-specific: `route-flow`, `route-topology`, `impact`, `dead-code`, `stats`
  - Composite (AI-facing): `project-context`, `project-norms`, `route-context`
  - Output: `generate` (builds graph JSON), `visualize` (produces HTML)
  - Command prefix detection wired into `CamelKitMain`

- **Graph-aware skills** — skills leverage graph analysis when available
  - `camel-implement`: graph-project-context guide for consistent property naming, bean reuse, version alignment across routes
  - `camel-validate`: graph-project-context for project-aware validation; graph-dead-code-report for dead code detection; `PROJECT_NORMS` for dynamic quality thresholds
  - `camel-test`: graph-project-context for cross-route test awareness and endpoint classification
  - `camel-migrate`: Step 0 graph detection fork; graph snapshot in Phase 1 BRD; per-route graph impact analysis in Phase 2 TDD generation
  - Shared `graph-availability.md` primitive for graceful fallback when graph is unavailable

- **MuleSoft graph parsers** — `MuleXmlFlowParser` (Mule 3.x/4.x XML) and `DataWeaveParser` (`.dwl` files) with dedicated node and edge types; `XmlRouteParser` skips Mule XML automatically

- **BizTalk migration support** — Microsoft BizTalk Server added as the 4th supported migration source platform
  - `BizTalkParser` — hybrid GraphParser with 4 internal StAX-based parsers for ODX orchestrations (37 shape types), BTM maps (45 functoid type mappings), BTP pipelines, and binding XML files
  - 10 new `NodeType` and 7 new `EdgeType` values for BizTalk artifacts
  - XmlRouteParser exclusion for BizTalk XML files (namespace and content sniffing)
  - `--source-platform biztalk` option for `camel-kit init`
  - BizTalk project detection in `detectProjectType()` (orchestrations, maps, pipelines)
  - 6 migration skill guides: `biztalk-phase1.md`, `biztalk-phase2.md`, `biztalk-component-mapping.md` (37 shape-to-EIP mappings, 16+ adapter mappings), `biztalk-map-conversion.md`, `biztalk-expression-mapping.md`, `biztalk-pipeline-mapping.md`
  - `/camel-migrate` SKILL.md updated with BizTalk vendor detection signals and guide manifest
  - UTF-16 binding file detection (BizTalk Admin Console exports UTF-16 by default)
  - Atomic graph mutation via buffering (prevents partial graph corruption on parse failures)
  - Deferred BTP component emission (handles FriendlyName appearing after Component elements)
  - Suspend Shape marked as not supported (BizTalk dehydration has no Camel equivalent)

- **3-phase orchestrated pipeline** — replaced the linear `/camel-project` → `/camel-flow` → `/camel-implement` → `/camel-validate` → `/camel-test` workflow with a structured 3-phase pipeline:
  - `/camel-brainstorm` — interactive design session producing a Blueprint Reference Document (BRD) with Technical Design Documents (TDDs)
  - `/camel-plan` — reviews approved design, creates detailed implementation plan with task decomposition
  - `/camel-execute` — orchestrated execution dispatching tasks to internal skills (implement → validate → test → verify) with two-stage review (spec compliance then code quality)
  - `/camel-flow` remains as a single-flow shortcut (brainstorm + plan + execute in one command)
  - `/camel-implement`, `/camel-validate`, `/camel-test` are now internal skills loaded by `/camel-execute`

- **`camel-kit plan analyze` command** — parses implementation plan markdown files and computes parallel execution waves; outputs a JSON task graph showing which tasks can run concurrently

- **`--source-platform` option for `camel-kit init`** — allows specifying the source platform during project initialization for migration workflows

- **`/camel-verify` — runtime verification skill**
  - 5-phase verification loop: environment preparation, build, startup, behavioral, report
  - Camel-specific error classification taxonomy with 14 error patterns (4 build, 7 startup, 3 runtime)
  - Fix routing: self-repair (pom.xml, properties, docker-compose), route to camel-validate, route to camel-implement, escalate to user
  - Behavioral verification using `camel cmd send` for payload injection and semantic comparison
  - Max 15 iteration attempts per phase
  - Invocable manually or automatically at the end of `/camel-execute`
  - Integration with all runtimes: Quarkus (`./mvnw quarkus:dev`), Spring Boot (`./mvnw spring-boot:run`), JBang (`camel run`)

- **Groovy DataMapper engine** — alternative to XSLT for simple data transformations
  - Engine selection: < 20 fields → Groovy; no schemas → Groovy; otherwise → XSLT
  - Decision made automatically during design (canonicalize stage)
  - Inline Groovy scripts in YAML route (no external `.xsl` file)
  - Supports all 4 format pairs: JSON→JSON, XML→JSON, JSON→XML, XML→XML
  - No `.kaoto` metadata (Kaoto IDE only supports XSLT)

- **Distribution system (`DistributionConfig`)** — externalized all distribution-specific configuration into `distribution.properties`
  - Single source of truth for Camel versions (main, Spring Boot, Quarkus), MCP server versions, and Maven repository URLs
  - Per-platform version defaults and supported version lists
  - Quarkus platform BOM version mapping
  - Skill variant selection: skills like iron-laws, constitution, mcp-setup, version-selection, maven-deps, quality-checks, and camel-knowledge have community/redhat variants auto-selected by distribution
  - `DefaultGenerator` selects variant files by distribution at init time
  - `BobGenerator` passes distribution values to Qute templates
  - `InitCommand` and catalog classes read Maven repo URLs from `DistributionConfig`

- **Multi-agent parity** — expanded from 3 to 5 supported AI agents
  - Added Qwen (`--ai qwen`) and OpenCode (`--ai opencode`)
  - Skills-based equalization layer: same skill guides work across all agents
  - Agent-specific generators: `ClaudeGenerator` (CLAUDE.md + parallel dispatch), `BobGenerator` (5 modes + monolithic skills), `GeminiGenerator` (@imports + policies + subagents), `QwenGenerator` (sub-agent auto-delegation), `OpenCodeGenerator` (permission-based agents)
  - `AgentGenerator` interface with `AgentGeneratorFactory` routing
  - `QuteTemplateEngine` for agent-specific template rendering (replaced `String.replace()`)
  - `InitContext` carries distribution and agent info through the init pipeline
  - Iron laws embedded in each agent's instruction file
  - Platform-specific dispatch block templates appended to SKILL.md during init
  - Pre-registered Qwen sub-agent definitions generated at init time

- **Iron laws** — 5 non-negotiable pipeline rules enforced across all skills
  1. MCP Catalog Verification — every component verified via MCP before use
  2. Constitution Compliance — every route passes all 7 constitution rules
  4. No Code Without Spec Approval — brainstorm → approval → plan → approval → execute
  5. Spec Compliance Before Quality — two-stage review in correct order

- **Migration support expanded** — `/camel-migrate` now handles Apache Camel 2.x/3.x and JBoss Fuse migrations in addition to MuleSoft Mule

- **Mandatory MCP catalog lookups in `/camel-flow`**
  - `camel_catalog_components` + `camel_catalog_component_doc` required before any component is suggested (Q2 source, Q4 sink); `CAMEL_VERSION` from `config.yaml` must be passed — training-data component names are forbidden
  - `camel_catalog_dataformats` + `camel_catalog_dataformat_doc` required before any data format is chosen (Q1); verifies availability in `CAMEL_VERSION` and records Maven coordinates
  - `camel_catalog_eips` + `camel_catalog_eip_doc` required before any EIP is suggested (Q3); catalog descriptions replace hardcoded examples
  - `camel_catalog_languages` + `camel_catalog_language_doc` required before any expression language is chosen (Q3); prevents defaulting to `simple` without checking fit for the data format
  - `.camel-kit/config.yaml` is now REQUIRED at skill start to extract `CAMEL_VERSION`; skill asks the user if the file is missing

- **Mandatory MCP catalog lookups in `/camel-implement`**
  - Rule 0: all component scheme names, endpoint option names, component-level option names, and Maven coordinates must come from `camel_catalog_component_doc` — never from training data
  - Rule 0b: data format names and options must come from `camel_catalog_dataformat_doc`
  - Rule 0c: expression language names and syntax must come from `camel_catalog_language_doc`
  - Rule 0d: EIP names and options must come from `camel_catalog_eip_doc`
  - Step 2 (Load Component Documentation) is now MANDATORY — hard stop if a component is not found in the catalog
  - Step 4 (Route Validation) is a validate→fix→re-query→retry loop up to 3 attempts; fixes must re-query the catalog before editing the YAML; failure after 3 attempts stops generation and reports errors

- **Mandatory MCP catalog lookups in `/camel-migrate-mule`** (Phase 2)
  - Same rules as `/camel-flow` and `/camel-implement`: `camel_catalog_component_doc` before writing any Camel component to the TDD; `camel_catalog_eip_doc` for each EIP mapping; `camel_catalog_language_doc` for predicates/expressions; `camel_catalog_dataformat_doc` for data format choices
  - `mule-component-mapping.md` is a starting-point only — catalog verification is always required

- **`/camel-migrate` rewritten as a generic migration orchestrator (v2.0)**
  - New step order: locate artifacts → scan ALL files → detect vendor from full scan content → build pre-populated analysis summary → confirm gaps with user → delegate to sub-skill
  - Vendor detection now uses the complete picture from all scanned files (namespaces, groupIds, descriptor files, property key patterns, dependency names) rather than a single file
  - Pre-populated analysis summary covers: vendor & version, business purpose, owning team, SLA/throughput, compliance/security, failure behaviour, deployment target — all extracted from artifacts without asking the user
  - Only genuine gaps (fields not found in any artifact) are asked; API compatibility is the only field that cannot be inferred
  - Defines a generic contract for all future vendor sub-skills: receive summary → do vendor-specific work → fill gaps only

- **Run without installing** — `jbang run camel-kit@luigidemasi/camel-kit` runs camel-kit directly without a global install; local clone and local SNAPSHOT variants also documented in README

- **`camel-kit init --silent`** — new flag that suppresses all output (no banner, no TUI, no progress messages, no summary); useful for scripted/CI environments where only the exit code matters; `Printer.noop()` added to the `Printer` interface as the no-op implementation

- **Comprehensive Data Transformation & Field Mapping (Kaoto DataMapper)**
  - Interactive schema-based field mapping in `/camel-flow`
  - Support for both XML Schema (XSD) and JSON Schema
  - Automatic field name matching and automapping proposals
  - Nested field handling (e.g., `order.customer.name` → `customer.name`)
  - Detailed field mapping tables in TDD with transformation types
  - Parameter support for Camel Variables and Message Headers
  - Conditional mappings: IF and CHOOSE-WHEN-OTHERWISE
  - Collection processing with FOR-EACH and position tracking
  - Comprehensive XPath function library (string, numeric, date/time, boolean)

- **Automatic XSLT Generation**
  - Generate Kaoto-compatible DataMapper XSLT from TDD field mappings
  - File naming: `{flow-name}-datamapper-{random-8-char-id}.xsl`
  - XSLT 2.0 for XML transformations, XSLT 3.0 for JSON
  - Support for all transformation types: direct copy, nested flattening, date/time formatting, string concatenation, numeric calculations, conditional logic (IF, CHOOSE-WHEN-OTHERWISE), array iteration with position tracking, parameter usage from Camel context
  - JSON transformation with `fn:json-to-xml()` and `fn:xml-to-json()`
  - XML namespace preservation and handling
  - Automatic integration in route YAML with xslt-saxon component
  - Parameter passing from route to XSLT

- **Apache Camel MCP Server Integration**
  - Automatic project-specific MCP configuration during `camel-kit init`
  - Support for multiple AI agents: Claude Code (`.mcp.json`), IBM Bob (`.bob/mcp.json`), Gemini CLI (`.gemini/mcp.json`), Qwen, OpenCode
  - 15 MCP tools available, 7 actively used across skills
  - Real-time catalog queries: `camel_catalog_components`, `camel_catalog_component_doc`
  - Route validation: `camel_validate_route`, `camel_route_context`
  - Security analysis: `camel_route_harden_context` with 47 automated checks
  - Version management: `camel_version_list`
  - 60-70% token savings compared to loading full catalog

- **Skills-based architecture with MCP integration**
  - Converted 5 commands to skills standard with YAML frontmatter and metadata
  - Commands now use kebab-case naming: `/camel-project`, `/camel-flow`, `/camel-implement`, `/camel-validate`, `/camel-test`
  - All skills are user-invocable and discoverable by AI agents
  - On-demand guide loading for token optimization (60-70% token savings)
  - Bundled component skills structure for offline use

- **`/camel-migrate` skill — vendor migration workflow**
  - Detects the source platform from a provided XML file, project directory, or ZIP archive
  - Delegates to vendor-specific sub-skills; first implementation: MuleSoft Mule 3.x / 4.x
  - Detection by XML namespace (`mulesoft.org`) and `pom.xml` groupId (`org.mule`, `com.mulesoft`)
  - Unknown vendors report found signatures and link to GitHub issues

- **`camel-migrate-mule` internal sub-skill (MuleSoft Mule → Apache Camel)**
  - Phase 1 (Business Analyst): parses all Mule XML flows, resolves proprietary connectors, conducts a one-question-at-a-time business interview, produces `.camel-kit/business-requirements.md` and `.camel-kit/constitution.md`
  - Phase 2 (Integration Architect): maps Mule components to Camel equivalents, converts DataWeave transformations into TDD field-mapping tables, produces one `.camel-kit/flows/{name}/{name}.tdd.md` per Mule flow — identical format to `/camel-flow` output
  - `guides/mule-component-mapping.md`: reference table for 40+ Mule → Camel component mappings
  - `guides/mule-dataweave-conversion.md`: DataWeave 1.0 / 2.0 conversion guide with 9 common patterns mapped to TDD Section 3 table format

- **Split-screen TUI for `camel-kit init` (TamboUI integration)**
  - Full-screen two-panel layout on terminals that support native image protocols (Kitty, iTerm2, Sixel): left panel shows the Camel-Kit logo; right panel shows live task progress with animated DOTS spinner and green tick on completion
  - Auto-exits when all tasks complete; Ctrl+C as emergency exit
  - Falls back gracefully to the existing banner + sequential output on terminals that do not support native image protocols
  - New dependencies: `dev.tamboui:tamboui-core`, `tamboui-image`, `tamboui-tui`, `tamboui-widgets`, `tamboui-jline3-backend` at version `0.1.0`
  - `LogoRenderer` utility, `TaskTracker` interface for TUI lifecycle events

- **Jakarta EE namespace rule in `/camel-implement`**
  - When Camel version >= 4.0, `jakarta.*` packages are used for all Jakarta EE APIs
  - Java SE packages (`javax.sql.*`, `javax.xml.*`) are explicitly exempt
  - Validation gate scans all generated files and replaces offending `javax.` references before saving

- **`onException` ordering constraint in `/camel-implement`**
  - Global `onException` (top-level `- onException:`) must be declared before all `- route:` blocks
  - Route-scoped error handling (`errorHandler:`, `doTry`/`doCatch`) stays inside the route

### Changed

- **Skill architecture refactored to orchestrator pattern** — all major skills (`camel-flow`, `camel-implement`, `camel-validate`, `camel-migrate`, `camel-test`, `camel-migrate-camel2`) rewritten as slim orchestrator manifests that load micro-guides on demand; monolithic `SKILL.md` files split into focused, reusable topic guides; large template files split into topic-specific micro-templates

- **`camel-kit-knowledge` separated to its own repository** — knowledge indexer, embedding, schema, index, and MCP modules moved to `camel-kit-knowledge` (separate repo with independent `0.0.1-SNAPSHOT` version line); `IndexResolver` added for runtime index download via Maven Resolver API with classpath fallback

- **Offline/standalone mode removed** — removed `--offline` runtime variant and all standalone mode code; single distribution only

- **Red Hat references externalized from Java source code** — all distribution-specific values (version numbers, Maven repository URLs, product names) moved to `distribution.properties`; Java source is distribution-neutral

- **Template engine migrated to Qute** — all templates migrated from `String.replace()` to Qute engine (`qute-core` dependency); supports conditional blocks, loops, and distribution-aware rendering

- **Documentation rewritten** — all docs updated to reflect the 3-phase orchestrated pipeline, user-invocable workflows, AI-agent targets, Groovy DataMapper, and runtime verification

- **`/camel-project` deprecated** — replaced by `/camel-brainstorm`

- **`/camel-knowledge` internalized** — now used by pipeline skills, no longer user-invocable

- **Constitution is now a static file — no generation step**
  - Removed Step 1.5 (Produce Constitution) from `camel-migrate-mule/SKILL.md`
  - Removed constitution generation logic and `constitution-template.md` guide from `camel-project/SKILL.md`
  - `camel-implement`, `camel-validate`, `camel-flow`: if `.camel-kit/constitution.md` is missing, copy from `templates/constitution.md` and continue

- **Constitution rewritten to v2.0** — reduced from 700 lines to ~100; contains only the six enforced rules: Route Structure, Single Responsibility, Separation of Concerns, Naming Conventions, Observability, External Configuration; all informational-only sections removed

- **`/camel-flow` — simplified defaults, advanced patterns now opt-in**
  - `unmarshal`/`marshal` no longer suggested by default; included only when the user explicitly needs typed Java object processing
  - DataMapper/XSLT-saxon is now the preferred transformation approach
  - Circuit Breaker, Idempotent Consumer, and Transactions moved from default questions to separate conditional questions asked only when contextually relevant
  - Q5 (Error Handling) expanded with retry policy guidance inline
  - Q6 (Performance) expanded with throttling, Kafka `consumersCount`, and Kubernetes deployment guidance

- **`/camel-implement` — `unmarshal` removed from default YAML template** — `unmarshal` added only when TDD explicitly requires typed object processing and no DataMapper XSLT covers the transformation

- **`/camel-migrate-mule` updated to sub-skill contract (v2.0)** — Phase 1 receives the pre-populated summary from the orchestrator and does not re-ask confirmed questions

- **MCP tool invocation — try-first, handle-failure** — all skills now attempt MCP tool calls directly without pre-checking for `.mcp.json` or trying to detect MCP availability upfront; if a call fails the skill falls back to bundled component skill files or manual analysis

- **`/camel-migrate` — error handling inferred from artifacts, not asked** — error handlers, retry policies, DLQ endpoints, and alert mechanisms are extracted from the source artifacts during the scan phase

- **`/camel-migrate` — API compatibility assumed by default** — Camel routes preserve the same HTTP paths, queue/topic names, and data contracts as the original integration unless the user explicitly opts out

- **Constitution — Principles 6, 7, 8 changed to informational** — Resilience (Circuit Breaker), Transaction Handling, and Idempotent Processing enforcement removed

- **`camel-kit init` — removed MCP guide file copying** — `MCP-SETUP.md` and `MCP-TESTING.md` are no longer copied to `.camel-kit/` during init

- **`CitrusSchemaDownloader`** — `fetchCitrusSchemas()` now accepts an optional `Consumer<String>` logger parameter; in TUI mode the printer is passed so download messages appear in the right panel

- **InitCommand improvements**
  - Create MCP configs only for selected AI agent (not all 3)
  - Fixed JAR filesystem handling for bundled skill distribution
  - Skills copied to both `.bob/commands/` (flat) and `.bob/skills/` (full structure)
  - Removed redundant catalog downloads — component and Kamelet catalogs no longer downloaded during init (MCP queries catalogs in real-time)

- **File generation locations corrected** — all generated routes now in project root (NOT in `.camel-kit/`); `.camel-kit/` reserved only for internal metadata

- **`camel-implement` — Route validation with MCP** — replaced Maven YAML DSL Validator with MCP `camel_validate_route` tool; validates all endpoint URIs against Camel catalog in real-time

- **Agent traits system — build-time append of agent-specific instructions** — `applyTraits()` in `DefaultGenerator` scans `templates/traits/{agent}/` and appends skill- or guide-level `.append.md` files during `camel-kit init`. Traits are idempotent through HTML comment sentinels (`<!-- TRAIT:agent -->`).

### Changed

- **BizTalk documentation updated** — added BizTalk migration references to `docs/user-guide.md`, `docs/commands.md`, `docs/architecture.md`, `docs/camel-kit-overview.md`, `README.md`, `CONTRIBUTING.md`. BizTalkMigrationStarter repository URL corrected. Camel validator-starter component reference corrected.

### Fixed

- **README: `-d` flag in Camel JBang Plugin install command corrected to `--description`** — the `-d` short option is not recognized by current versions of Camel JBang. Fixed to use the correct `--description` long option.

- **Hardcoded version numbers in skill files replaced with Qute-substituted placeholders** — skill Markdown files contained hardcoded Camel/Quarkus version numbers (e.g., `3.33.0`, `4.18.0`, `3.27.2`) that drifted from `distribution.properties`, causing stale versions in generated projects. Added Qute-based placeholder substitution to `copySkills` using an escape-then-unescape approach (escape all `{` to `\{`, restore only known version keys, then Qute-render). New `{QUARKUS_PLATFORM_TABLE}` placeholder dynamically generates the Camel-to-Quarkus mapping table from `quarkus.platform.*` keys. `DistributionConfig.quarkusPlatformMappings()` added. 16 hardcoded values replaced across 8 skill files.

- **Fallback LTS version in `CatalogDownloader` no longer hardcoded** — `getLatestLtsVersion()` returned a hardcoded `"4.20.0"` when Maven Central was unreachable. Replaced with a `fallbackVersion` constructor parameter so callers provide the value from `DistributionConfig.camelMainVersion()`.

- **LTS version detection no longer relies on even-number heuristic** — `getLatestLtsVersion()` assumed LTS versions have even minor numbers (`minor % 2 == 0`), which is not officially guaranteed by Apache Camel. Replaced with an explicit `Set<String>` of known LTS minor versions passed via constructor, built from `DistributionConfig.camelMainSupported()`.

- **`.kaoto` filename and format hardening against hallucination**
  - Filename must be `.kaoto` (single project-level file) — NOT `kaoto-datamapper-{id}.kaoto` (per-mapping file invented by analogy with XSL naming)
  - Content must use Kaoto's internal format (`sourceBody`, `targetBody`, `sourceParameters`, `namespaceMap`) — NOT a custom JSON schema with invented keys
  - Added "WRONG names" column to artifact table and explicit allowed-keys list in `datamapper-implement.md`

- **Route generation runtime fixes (Rules 0h, run.sh, docker-compose)**
  - Rule 0h — HTTP response body marshal: when a route starts with an HTTP consumer and has an `unmarshal` mid-route, add a matching `marshal` step at the end
  - `run.sh` template: use `jbang camel@apache/camel run` (JBang alias) instead of non-existent Maven artifact; include `*.xsl` in the `camel run` command
  - `docker-compose.yaml` template: `apache/camel-jbang` image entrypoint is `camel`, so `command:` must be `run ...` not `camel run ...`

- **`.kaoto` metadata type values must use Kaoto display strings** — `type` field must use `"JSON Schema"` / `"XML Schema"` (space-separated display strings), not `"JSON_SCHEMA"` / `"XML_SCHEMA"` (underscore enum keys)

- **Primitive type fallback — correct type when no schema file exists** — "No schema file" != "Primitive data": structured JSON without a schema is `JSON_SCHEMA` with path `"none"`, not `Primitive`

- **MCP catalog verification for component properties and hardened DataMapper XSLT generation**
  - `application.properties` must use the exact URI scheme from the route (e.g., `smtp`, not `mail`)
  - Every `camel.component.<name>.<property>` must be verified against the catalog — no invented property names
  - `platform-http` has no `host` or `port` component options — Mule HTTP Listener port converts to `camel.server.enabled=true` + `camel.server.port=XXXX`
  - DataMapper TDD validation: auto-corrects wrong XSLT Pattern, detects plain Source XPaths and plain Target Elements and recomputes them
  - Explicit `json-to-xml()` prohibition for Approach A
  - Split Step 4 (YAML injection) into three per-approach blocks: Approach A with mandatory `useJsonBody: true`, Approach B with `setHeader`/`setBody`, Approach N/A without special params

- **Deterministic DataMapper XSLT generation with canonical XPaths and self-validation**
  - Pre-compute Source XPaths and Target Elements during flow design and migration so that `/camel-implement` performs mechanical translation
  - New shared guide `skills/shared/datamapper-canonicalize.md` — enriches semantic field mappings with XSLT-ready structural data; used by both `datamapper-interview.md` and `datamapper-migrate.md`
  - Split Pattern B (JSON→JSON) and Pattern C (JSON→XML) skeletons into per-approach variants
  - New mandatory Step 3.5 self-validation pass in `datamapper-implement.md`

- **JSON DataMapper XSLT correctness rules**
  - `json-to-xml($paramName)` not `json-to-xml(.)` — the JSON string arrives via `xsl:param`, not as the context node
  - `unmarshal: json:` ordering — Rule 0g: never place `unmarshal: json:` before an xslt-saxon DataMapper step when `useJsonBody: true`
  - Structural checklist: every generated JSON XSLT must have `xsl:param`, `json-to-xml($paramName)` variable, `xsl:template match="/"`, and `xml-to-json($mapped-xml)` output

- **`toD` for dynamic URIs and parameters** — Rule 0f in `/camel-implement`: `to` evaluates its URI once at startup; any `${...}` Simple expression in the `uri` or in any `parameters:` value is treated as a literal string; use `toD` instead

- **HTTP header cleanup between HTTP endpoints** — Rule 0e: when a route has both an inbound HTTP consumer and outbound HTTP producer calls, `removeHeaders("CamelHttp*")` is inserted before each outbound call

- **DataMapper XSLT generation — empty skeleton prevented** — new Step 1.5 validation gate stops generation with an actionable error message instead of producing an empty XSLT skeleton; Pattern B (JSON→JSON) rules expanded with field-path translation table

- **Bob guide resolution, splash screen, MCP stdio transport**

- **Qwen Code tool names** — corrected sub-agent definitions to use correct Qwen Code tool names

- **Skill quality audit** — 7 evaluation passes (55+ fixes) across all 6 skills: MCP param corrections, context pollution, anti-hedging, completion gates, batch mode, guide path notation, smoke test rollback strategy, DataMapper test examples, runtime-aware test config, vendor detection recovery

- **MCP configuration generation** — now creates only the config for the selected agent; `knowledge.mcp.version` tag used correctly in maven-metadata.xml parsing

### Removed

- **`camel-kit-graph-mcp` module** — graph MCP server removed; graph analysis now exposed exclusively through CLI commands (reduced MCP servers from 3 to 2)

- **Offline/standalone mode** — removed all offline mode code and the `--offline` flag

- **Red Hat references in Java source** — all distribution-specific values externalized to `distribution.properties`; Java code is distribution-neutral

- **Distribution variant selection** — simplified to single distribution; removed `--distribution` field and variant selection UI

## [0.3.1] - 2026-03-02

### Fixed

- Replace `{{DATE}}` and `{{CAMEL_VERSION}}` placeholders in constitution during init
- `.kaoto` format hardening and constitution simplification (see Unreleased for full details)
- Route generation runtime fixes
- MCP catalog verification for component properties
- Deterministic DataMapper XSLT generation with canonical XPaths
- `toD` for dynamic URIs/params
- HTTP header cleanup rule, DataMapper empty skeleton guard, `--silent` flag
- MCP try-first approach, error handling inference in camel-migrate, API compatibility default
- Mandatory MCP catalog lookups; constitution v2.0; camel-migrate skill reorganization

### Added

- TamboUI split-screen TUI for `camel-kit init`
- `camel-migrate` skill (MuleSoft Mule), simplified route defaults, Jakarta EE namespaces, `onException` ordering in `/camel-implement`
- `camel-migrate` command/skill registration
- Automated snapshot version merge to main after release

## [0.3.0] - 2026-02-23

### Added

- **Skills-based architecture with MCP integration** (initial release)
- **Apache Camel MCP Server Integration** (15 tools, 7 actively used)
- **Comprehensive Data Transformation & Field Mapping** (Kaoto DataMapper)
- **Automatic XSLT Generation** from TDD field mappings

### Changed

- MCP-first approach across all skills
- File generation locations corrected (project root, not `.camel-kit/`)
- `camel-implement` route validation via MCP `camel_validate_route`

## [0.2.0] - 2025-02-18

### Added
- **Camel version updated to 4.18.0** (LTS)

- **Camel-Kit logo** - Added camel-kit.gif logo inspired by K.I.T.T. from Knight Rider

- **Enhanced error handling guidance** in constitution and design patterns:
  - Three exception handling approaches: `doTry/doCatch/doFinally`, `errorHandler`, `onException`
  - Error handler types: `noErrorHandler`, `defaultErrorHandler`, `deadLetterChannel`
  - `onException` clause with `handled()`, `continued()`, `markRollbackOnly()`

- **Transaction handling patterns**:
  - Transaction propagation policies (PROPAGATION_REQUIRED, PROPAGATION_REQUIRES_NEW, etc.)
  - Using `.transacted()` DSL for transaction management
  - Combining transactions with exception handling via `markRollbackOnly`

- **Kafka consumer scaling guidance**:
  - Consumer-to-partition relationship and assignment rules
  - `consumersCount` parameter usage with Kubernetes replicas
  - Offset reset strategies (earliest, latest, none)

- **Kubernetes deployment best practices**:
  - ConfigMaps and Secrets patterns for configuration
  - Health probes (liveness, readiness, startup)
  - Resource requests and limits configuration

### Changed

- **Rewritten in Java** - Complete rewrite from Python to Java for better JBang integration
  - Multi-module Maven project structure (camel-kit-core, camel-kit-main, camel-kit-plugins)
  - Installation via JBang: `jbang app install camel-kit@io.github.luigidemasi:camel-kit-main:0.2.0-SNAPSHOT`
  - Uses PicoCLI for command-line parsing
  - Uses JLine for terminal handling

- **Camel version updated to 4.14.5** (LTS)
- **Citrus version updated to 4.9.2**
- **Maven Wrapper included in generated projects**
- **Validation uses MCP and Maven plugins**
- **Citrus JSON schemas downloaded during init**
- **Updated constitution.md** - Renumbered sections after adding transaction handling
- **Rewrote CONTRIBUTING.md** - Changed from Python development to Java/Maven development

### Fixed

- Template consistency across all locations

### Removed

- **Python implementation** - Replaced with Java/JBang
- **`camel-kit catalog` command** - Catalogs are downloaded during init and cached
- **`camel-kit agents` command** - Agent information available via `--help`
- **`camel-kit version` command** - Use `camel-kit --help` for version info

## [0.1.3] - 2025-02-13

### Added

- **YAML DSL Schema download**: Schema is now automatically fetched and cached during `camel-kit init`

### Changed

- **`/camel-implement` now uses component catalog** during YAML generation:
  - New Step 3: Component Catalog Lookup before generating YAML
  - Looks up each component in `.camel-kit/.cache/components-{version}.json`
  - Verifies component exists and can be used as consumer/producer
  - Identifies required vs optional options from `properties[*].required`
  - Determines option placement: `kind: "path"` in URI, `kind: "parameter"` in parameters block

## [0.1.2] - 2025-02-13

### Added

- **Claude Code support**: Added Anthropic Claude Code as a supported AI agent
  - Commands are generated in Markdown format (`.claude/commands/`)
  - Uses `$ARGUMENTS` placeholder for arguments
- YAML schema validation in `/camel-validate` and `/camel-implement`
  - Auto-fix for common validation errors

### Changed

- Renamed `/camel-context` to `/camel-project` for clarity
- `/camel-project` now focuses only on business landscape
- Technical details moved to `/camel-flow` command
- Removed test generation prompt from `/camel-implement`
- `/camel-implement` now generates `application.properties` and `camel.jbang.dependencies`
- **Improved `/camel-test` command** with Testcontainers and Citrus YAML fixes

### Fixed

- Fixed Camel JBang configuration: use `camel.component.<name>.<prop>` for component settings
- Fixed bean definitions: use `#class:` prefix for bean instantiation
- Fixed property loading: `application.properties` must be included in `camel run` command
- Fixed Citrus `camel.jbang.run` YAML schema
- Fixed `onException` YAML syntax: `handled` requires expression format, not boolean

## [0.1.1] - 2025-02-12

### Added

- **Gemini CLI support**: Added Google Gemini CLI as a supported AI agent
  - Commands are generated in TOML format (`.gemini/commands/`)

### Changed

- Merged `/camel-flow` and `/camel-route` commands into single `/camel-flow` command
- Renamed `/camel-generate` to `/camel-implement` for clarity
- Simplified `/camel-project` to ask only high-level questions
- Updated `/camel-flow` to ask questions one at a time interactively

### Fixed

- Fixed Citrus YAML schema issues in `/camel-test`
- Added `citrus-camel` dependency to jbang.properties

### Removed

- Removed obsolete `/camel-init` command (replaced by CLI `camel-kit init`)
- Removed separate `/camel-route` command (merged into `/camel-flow`)

## [0.1.0] - 2024-XX-XX

### Added

- Initial release of camel-kit CLI
- Project initialization with `camel-kit init`
- Support for IBM Project Bob AI agent
- Slash commands for AI-assisted integration design:
  - `/camel-init` - Bootstrap project with constitution and catalog
  - `/camel-project` - Define integration landscape
  - `/camel-route` - Design individual routes with EIP guidance
  - `/camel-validate` - Check specifications against catalog and constitution
  - `/camel-test` - Generate Citrus integration tests
  - `/camel-generate` - Output Kaoto-compatible Camel YAML DSL
- Live catalog fetching from Maven Central (components) and GitHub (Kamelets)
- Constitution-based best practices enforcement
- Kaoto-compatible YAML generation
- Citrus test generation with Testcontainers support
- Update mode for re-running context and route commands

### Notes

- Heavily inspired by [GitHub Spec-Kit](https://github.com/github/spec-kit)
- Built for the Apache Camel community

[Unreleased]: https://github.com/luigidemasi/camel-kit/compare/camel-kit-0.3.1...HEAD
[0.3.1]: https://github.com/luigidemasi/camel-kit/compare/camel-kit-0.3.0...camel-kit-0.3.1
[0.3.0]: https://github.com/luigidemasi/camel-kit/compare/v0.2.0...camel-kit-0.3.0
[0.2.0]: https://github.com/luigidemasi/camel-kit/compare/v0.1.3...v0.2.0
[0.1.3]: https://github.com/luigidemasi/camel-kit/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/luigidemasi/camel-kit/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/luigidemasi/camel-kit/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/luigidemasi/camel-kit/releases/tag/v0.1.0
