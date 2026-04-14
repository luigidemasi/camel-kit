# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).


## [Unreleased]

### Added

- **3-phase orchestrated pipeline** -- replaced the linear `/camel-project` → `/camel-flow` → `/camel-implement` → `/camel-validate` → `/camel-test` workflow with a structured 3-phase pipeline:
  - `/camel-brainstorm` -- interactive design session producing a Blueprint Reference Document (BRD) with Technical Design Documents (TDDs)
  - `/camel-plan` -- reviews approved design, creates detailed implementation plan with task decomposition
  - `/camel-execute` -- orchestrated execution dispatching tasks to internal skills (implement → validate → test → verify) with two-stage review (spec compliance then code quality)
  - `/camel-flow` remains as a single-flow shortcut (brainstorm + plan + execute in one command)
  - `/camel-implement`, `/camel-validate`, `/camel-test` are now internal skills loaded by `/camel-execute`

- **`/camel-verify` -- runtime verification skill**
  - 5-phase verification loop: environment preparation, build, startup, behavioral, report
  - Camel-specific error classification taxonomy with 14 error patterns (4 build, 7 startup, 3 runtime)
  - Fix routing: self-repair (pom.xml, properties, docker-compose), route to camel-validate, route to camel-implement, escalate to user
  - Behavioral verification using `camel cmd send` for payload injection and semantic comparison
  - Max 15 iteration attempts per phase
  - Invocable manually or automatically at the end of `/camel-execute`
  - Integration with all runtimes: Quarkus (`./mvnw quarkus:dev`), Spring Boot (`./mvnw spring-boot:run`), JBang (`camel run`)

- **Groovy DataMapper engine** -- alternative to XSLT for simple data transformations
  - Engine selection: < 20 fields → Groovy; no schemas → Groovy; otherwise → XSLT
  - Decision made automatically during design (canonicalize stage)
  - Inline Groovy scripts in YAML route (no external `.xsl` file)
  - Supports all 4 format pairs: JSON→JSON, XML→JSON, JSON→XML, XML→XML
  - No `.kaoto` metadata (Kaoto IDE only supports XSLT)

- **Multi-agent parity** -- expanded from 3 to 5 supported AI agents
  - Added Qwen (`--ai qwen`) and OpenCode (`--ai opencode`)
  - Skills-based equalization layer: same skill guides work across all agents
  - Agent-specific templates: Claude=CLAUDE.md with subagents, Bob=custom_modes.yaml with 5 modes, Gemini=GEMINI.md, Qwen=QWEN.md, OpenCode=AGENTS.md
  - Iron laws embedded in each agent's instruction file

- **Iron laws** -- 5 non-negotiable pipeline rules enforced across all skills
  1. MCP Catalog Verification -- every component verified via MCP before use
  2. Red Hat Build Only -- only supported versions and components
  3. Constitution Compliance -- every route passes all 7 constitution rules
  4. No Code Without Spec Approval -- brainstorm → approval → plan → approval → execute
  5. Spec Compliance Before Quality -- two-stage review in correct order

- **Migration support expanded** -- `/camel-migrate` now handles Apache Camel 2.x/3.x and JBoss Fuse migrations in addition to MuleSoft Mule

### Changed

- **Documentation rewritten** -- all docs updated to reflect the 3-phase orchestrated pipeline, 6 user-invocable commands, 5 AI agents, Groovy DataMapper, and runtime verification
- **`/camel-project` deprecated** -- replaced by `/camel-brainstorm`
- **`/camel-knowledge` internalized** -- now used by pipeline skills, no longer user-invocable

### Fixed

- **`.kaoto` filename and format hardening against hallucination**
  - Filename must be `.kaoto` (single project-level file) — NOT `kaoto-datamapper-{id}.kaoto` (per-mapping file invented by analogy with XSL naming)
  - Content must use Kaoto's internal format (`sourceBody`, `targetBody`, `sourceParameters`, `namespaceMap`) — NOT a custom JSON schema with invented keys (`mappingId`, `flowName`, `fieldMappings`, `conditionalMappings`)
  - Added "WRONG names" column to artifact table, filename/format rules table with wrong examples, and explicit allowed-keys list in `datamapper-implement.md` Step 5

### Changed

- **Constitution is now a static file — no generation step**
  - Removed Step 1.5 (Produce Constitution) from `camel-migrate-mule/SKILL.md`
  - Removed constitution generation logic and `constitution-template.md` guide from `camel-project/SKILL.md`
  - Deleted `camel-project/guides/constitution-template.md` (redundant with `templates/constitution.md`)
  - `camel-implement`, `camel-validate`, `camel-flow`: if `.camel-kit/constitution.md` is missing, copy from `templates/constitution.md` and continue

### Fixed

- **Route generation runtime fixes (Rules 0h, run.sh, docker-compose)**
  - Rule 0h — HTTP response body marshal: when a route starts with an HTTP consumer (`platform-http`, etc.) and has an `unmarshal` mid-route, add a matching `marshal` step at the end so the response body is serializable (otherwise `NoTypeConversionAvailableException: LinkedHashMap → InputStream`)
  - `run.sh` template: use `jbang camel@apache/camel run` (JBang alias) instead of non-existent `org.apache.camel:camel-jbang:VERSION:runner` Maven artifact; include `*.xsl` in the `camel run` command so DataMapper XSLT files are on the classpath
  - `docker-compose.yaml` template: `apache/camel-jbang` image entrypoint is `camel`, so `command:` must be `run ...` not `camel run ...`; mount and include all `.xsl` files in volumes and command

- **`.kaoto` metadata type values must use Kaoto display strings**
  - `datamapper-implement.md` Step 5: `.kaoto` `type` field must use `"JSON Schema"` / `"XML Schema"` (space-separated display strings), not `"JSON_SCHEMA"` / `"XML_SCHEMA"` (underscore enum keys) — Kaoto silently falls back to Primitive and wipes XSLT mappings with wrong format
  - Added explicit mapping table and warning in the `.kaoto` template
  - Internal TDD labels continue to use underscore format (`JSON_SCHEMA`/`XML_SCHEMA`); translation happens at the single `.kaoto` output point

- **Primitive type fallback — correct type when no schema file exists**
  - "No schema file" ≠ "Primitive data": structured JSON without a schema is `JSON_SCHEMA` with path `"none"`, not `Primitive`
  - `datamapper-migrate.md`: keep `JSON_SCHEMA`/`XML_SCHEMA` from format detection when no schema file is found
  - `datamapper-interview.md`: ask user for data format instead of defaulting to Primitive
  - `datamapper-canonicalize.md`: new pre-check auto-corrects Primitive to correct type when field mappings show dotted paths or array access
  - `datamapper-implement.md`: new Step 1.5b auto-corrects Primitive type and N/A approach; `filePath: []` valid for any type
  - `camel-migrate-mule/SKILL.md`: added type selection rules note on TDD template

- **MCP catalog verification for component properties and hardened DataMapper XSLT generation**
  - `application.properties` must use the exact URI scheme from the route (e.g., `smtp`, not `mail`) — enforced in both `camel-implement/SKILL.md` Step 2.1 and Step 5.1 via mandatory `camel_catalog_component_doc` verification
  - Every `camel.component.<name>.<property>` must be verified against the catalog — no invented property names
  - `platform-http` has no `host` or `port` component options — Mule HTTP Listener port converts to `camel.server.enabled=true` + `camel.server.port=XXXX` (explicit conversion rule in `mule-component-mapping.md`)
  - DataMapper TDD validation (Step 1.5b–d in `datamapper-implement.md`): auto-corrects wrong XSLT Pattern (e.g., D→B when both source and target are JSON_SCHEMA), detects plain Source XPaths (`/name` instead of `/fn:map/fn:string[@key='name']`) and plain Target Elements (`city` instead of `<string key="city">`) and recomputes them
  - Explicit `json-to-xml()` prohibition for Approach A — calling it on lossless XML causes `Invalid numeric literal: multiple points`
  - Split Step 4 (YAML injection) into three per-approach blocks: Approach A with mandatory `useJsonBody: true`, Approach B with `setHeader`/`setBody`, Approach N/A without special params — missing `useJsonBody: true` causes `Content is not allowed in prolog`
  - New Step 3.5b: post-injection route YAML verification ensures `useJsonBody: true` presence matches the XSLT Approach

- **Deterministic DataMapper XSLT generation with canonical XPaths and self-validation**
  - Pre-compute Source XPaths and Target Elements during flow design (`/camel-flow`) and migration (`/camel-migrate`) so that `/camel-implement` performs mechanical translation rather than re-interpreting semantic field paths on every run
  - New shared guide `skills/shared/datamapper-canonicalize.md` — enriches semantic field mappings with XSLT-ready structural data (pattern, approach, XPaths, target elements); used by both `datamapper-interview.md` and `datamapper-migrate.md`, eliminating duplicated TDD-writing logic
  - Enriched Field Mappings table: 8 columns (added Source XPath + Target Element) plus header-level XSLT Pattern and XSLT Approach metadata
  - Split Pattern B (JSON→JSON) and Pattern C (JSON→XML) skeletons into per-approach variants (Approach A: useJsonBody vs Approach B: header param) to eliminate skeleton/TDD conflicts that caused empty XSLT output
  - New mandatory Step 3.5 self-validation pass in `datamapper-implement.md` — verifies every TDD field mapping row has a matching XSLT element with correct Source XPath, Target Element, type consistency, and approach purity before proceeding
  - Removed redundant ~200-line "Two correct approaches" section, replaced with concise reference sections for runtime behavior and correctness checks

- **JSON DataMapper XSLT correctness rules** — two new mandatory checks added to `guides/datamapper-implement.md` Step 4 to prevent incorrectly generated XSLT files:
  - **`json-to-xml($paramName)` not `json-to-xml(.)`** — the JSON string arrives via `xsl:param`, not as the context node; using `fn:json-to-xml(.)` on the `<root/>` placeholder context document produces wrong output; it must always be `json-to-xml($paramName)` on the named param that received the JSON string
  - **Why intermediate XML is required** — XSLT 3.0 has no `json-to-json()` function; `json-to-xml()` converts a JSON string to W3C lossless XML (`fn:map`, `fn:array`, `fn:string[@key]`, `fn:number[@key]`, `fn:boolean[@key]`), and `xml-to-json()` converts it back; this lossless XML intermediate is the only way XPath can navigate JSON data
  - **Structural checklist** — every generated JSON XSLT must have: `xsl:param` for each JSON source, `json-to-xml($paramName)` variable (not `.`), `xsl:template match="/"`, and `xml-to-json($mapped-xml)` output
  - **`unmarshal: json:` ordering** — Rule 0g in `camel-implement/SKILL.md`: never place `unmarshal: json:` before an xslt-saxon DataMapper step when `useJsonBody: true` — it converts the body to `LinkedHashMap` which cannot be passed as a JSON string to the XSLT param; `unmarshal: json:` goes after the DataMapper step if needed

- **`toD` for dynamic URIs and parameters** — Rule 0f in `camel-implement/SKILL.md`: `to` evaluates its URI once at startup; any `${...}` Simple expression in the `uri` or in any `parameters:` value is treated as a literal string and never evaluated at runtime; use `toD` and inline all dynamic values in the URI string; static `{{...}}` property placeholders are safe in both `to` and `parameters:`

### Fixed

- **`to` vs `toD` for dynamic destinations** — new mandatory generation rule (Rule 0f) in `/camel-implement`: `to` resolves its URI once at startup as a static string — any `${...}` Simple expression in the URI path **or** in a `parameters:` value is never evaluated at runtime; when either contains a Simple expression the step must use `toD` instead, with all dynamic values inlined into the URI string (e.g. `?q=${header.city}`); a static pre-check in Step 4.1 scans every `to:` step for `${...}` in the URI and `parameters:` block before calling `camel_validate_route`, ensuring the rule is enforced even if the generator misses it; `{{...}}` property placeholders remain safe in both `to` and `parameters:`

### Added

- **Run without installing** — `jbang run camel-kit@luigidemasi/camel-kit` runs camel-kit directly without a global install; local clone and local SNAPSHOT variants also documented in README

### Added

- **`camel-kit init --silent`** — new flag that suppresses all output (no banner, no TUI, no progress messages, no summary); useful for scripted/CI environments where only the exit code matters; `Printer.noop()` added to the `Printer` interface as the no-op implementation

### Fixed

- **HTTP header cleanup between HTTP endpoints** — new mandatory generation rule (Rule 0e) in `/camel-implement`: when a route has both an inbound HTTP consumer (`platform-http`, `servlet`, `jetty`, `netty-http`) and one or more outbound HTTP producer calls (`http`, `https`), a `removeHeaders("CamelHttp*")` step is now inserted before each outbound call to prevent inbound headers (`CamelHttpMethod`, `CamelHttpPath`, `CamelHttpQuery`, etc.) from leaking into the outbound request; references the existing `guides/sequential-http-calls.md` for detailed examples

- **DataMapper XSLT generation — empty skeleton prevented**
  - `guides/datamapper-implement.md`: new Step 1.5 validation gate — if the `#### Field Mappings` table in the TDD DataMapper section has no data rows, generation stops with an actionable error message instead of producing an empty, non-functional XSLT skeleton
  - `guides/datamapper-migrate.md`: Step 6 now refuses to append a DataMapper section with an empty Field Mappings table — warns the user to review the DataWeave script manually instead
  - `guides/datamapper-implement.md`: Step 2 pattern-selection table now includes the `xsl:output method` for each pattern with an explicit CRITICAL note that Patterns B (JSON→JSON) and D (XML→JSON) use `method="text"` — not `method="xml"` — and that an empty `<xsl:template match="/">` is always wrong
  - `guides/datamapper-implement.md`: Pattern B (JSON→JSON) rules expanded with a field-path translation table (DataWeave paths → XSLT lossless XML XPath), array iteration guidance (relative paths inside `xsl:for-each`), and a concrete end-to-end example matching a real migration scenario

### Changed

- **MCP tool invocation — try-first, handle-failure** — all skills now attempt MCP tool calls directly without pre-checking for `.mcp.json` or trying to detect MCP availability upfront; if a call fails (tool not found, network error, timeout) the skill falls back to bundled component skill files or manual analysis; affects `camel-flow`, `camel-implement`, `camel-project`, `camel-validate`, `camel-test`

- **`/camel-migrate` — error handling inferred from artifacts, not asked** — error handlers, retry policies, DLQ endpoints, and alert mechanisms are extracted from the source artifacts (vendor XML, properties files) during the scan phase; the sub-skill's technical interview no longer asks about error handling strategy

- **`/camel-migrate` — API compatibility assumed by default** — Camel routes preserve the same HTTP paths, queue/topic names, and data contracts as the original integration unless the user explicitly opts out; removed API compatibility as a question from both the orchestrator confirmation step and the Mule sub-skill gap-filling step

---

### Added

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

### Changed

- **Constitution rewritten to v2.0** — reduced from 700 lines to ~100; contains only the six enforced rules: Route Structure, Single Responsibility, Separation of Concerns, Naming Conventions, Observability, External Configuration; all informational-only sections (Resilience, Transactions, Idempotency, VETRO, Kafka Scaling, Kubernetes, Data Format Discipline) removed; Error Handling, Retry Policy, Throttling, and Kubernetes guidance moved to `/camel-flow` Q5/Q6 where they are applied context-specifically

- **`/camel-flow` Q5 (Error Handling)** — retry policy guidance (3–5 retries, exponential backoff, max 30 s delay) now documented inline; stale constitution principle reference in Q5d replaced with transaction propagation policies inline

- **`/camel-flow` Q6 (Performance)** — expanded to also trigger on "Kubernetes/cloud/scale/replicas"; throttling strategies and Kafka `consumersCount` guidance added; Kubernetes deployment guidance (ConfigMaps, health probes, Secrets) added

- **`/camel-migrate-mule` updated to sub-skill contract (v2.0)** — Phase 1 receives the pre-populated summary from the orchestrator and does not re-ask confirmed questions; Step 1.2 (proprietary connectors) now uses `pom.xml` dependencies to pre-suggest replacement options; Step 1.3 asks only genuine gaps (typically API compatibility only); TDD Section 9 (Constitution Gate Checks) updated to constitution v2.0 six rules; BRD Best Practices updated to name the six constitution rules explicitly; Step 1.5 references constitution v2.0 template

- **Split-screen TUI for `camel-kit init` (TamboUI integration)**
  - Full-screen two-panel layout on terminals that support native image protocols
    (Kitty, iTerm2, Sixel): left panel shows the Camel-Kit logo; right panel shows
    live task progress with animated DOTS spinner and green tick on completion
  - Task list with emoji icons: 📁 project structure · 📝 configuration · 🤖 AI commands
    · 📚 skills · 🔌 MCP & Maven wrapper · ⬇️ Citrus schemas
  - Word-wrapping in the right panel so text never overflows the border
  - Panels are 37.5 % of terminal width and 85 % of terminal height (minimum 30 × 15),
    centred horizontally and vertically with amber/gold borders
  - Auto-exits when all tasks complete — no keypress required; Ctrl+C as emergency exit
  - Falls back gracefully to the existing banner + sequential output on terminals that do
    not support native image protocols or when running as a Camel JBang plugin with an
    incompatible classloader (ServiceLoader isolation bypassed via explicit `JLineBackend`)
  - Non-TUI fallback shows the logo via the Kitty/iTerm2/Sixel protocol inline above the
    output when the terminal supports it; degrades to ASCII art otherwise
  - New dependencies: `dev.tamboui:tamboui-core`, `tamboui-image`, `tamboui-tui`,
    `tamboui-widgets`, `tamboui-jline3-backend` at version `0.1.0`

- **`LogoRenderer` utility** — writes the `logo.png` image directly to the terminal
  output stream using the best available native image protocol; sizes the image
  proportionally, accounting for the 8 × 16 px/cell aspect ratio; centers it horizontally
  using the Kitty protocol's `rect.x()` coordinate

- **`TaskTracker` interface** — allows `InitCommand` to report task lifecycle events
  (`startTask`, `finishTask`) to the TUI; a no-op implementation is used in normal mode
  so the existing init logic requires no branching

### Changed

- **`CitrusSchemaDownloader`** — `fetchCitrusSchemas()` now accepts an optional
  `Consumer<String>` logger parameter; defaults to `System.out::println` for backward
  compatibility; in TUI mode the printer is passed so download messages appear in the
  right panel instead of going directly to the terminal

- **`camel-kit init` completion message** — reformatted to a minimal clean layout:
  summary line (`✓ project-name · version · agent · N schemas`), followed by a
  `Next steps` section with a divider line and colour-coded command references

- **`CamelKitMain` banner** — tagline split into a dim prefix ("Camel-Kit —") and a
  bold amber suffix ("Design Apache Camel Integrations with AI") for better visual
  hierarchy; terminal height now passed to `LogoRenderer` so the image is sized to fit
  both dimensions

- **`/camel-migrate` skill — vendor migration workflow**
  - New user-invocable skill that detects the source platform from a provided XML file, project directory, or ZIP archive
  - Delegates to vendor-specific sub-skills; first implementation: MuleSoft Mule 3.x / 4.x
  - Detection by XML namespace (`mulesoft.org`) and `pom.xml` groupId (`org.mule`, `com.mulesoft`)
  - Easter egg: 🫏 → 🐪 displayed on Mule detection
  - Unknown vendors report found signatures and link to GitHub issues

- **`camel-migrate-mule` internal sub-skill (MuleSoft Mule → Apache Camel)**
  - Phase 1 (Business Analyst): parses all Mule XML flows, resolves proprietary connectors
    (Anypoint MQ, Object Store, SAP, Workday, etc.) by asking the user before proceeding,
    conducts a one-question-at-a-time business interview, produces
    `.camel-kit/business-requirements.md` and `.camel-kit/constitution.md`
  - Phase 2 (Integration Architect): maps Mule components to Camel equivalents,
    converts DataWeave transformations into TDD field-mapping tables, produces one
    `.camel-kit/flows/{name}/{name}.tdd.md` per Mule flow — identical format to `/camel-flow` output
  - `guides/mule-component-mapping.md`: reference table for 40+ Mule → Camel component mappings
    including proprietary connectors requiring user decisions
  - `guides/mule-dataweave-conversion.md`: DataWeave 1.0 / 2.0 conversion guide with
    9 common patterns mapped to TDD Section 3 table format

- **`camel-kit init` — `camel-migrate` command registered automatically**
  - `"migrate"` added to the commands list; `.claude/commands/camel-migrate.md` (and equivalent
    for Bob / Gemini) is generated during `camel-kit init`
  - "Next steps" output updated to mention `/camel-migrate <export-file>` alongside `/camel-project`

- **Workflow diagram** — `camel-kit-workflow.excalidraw` showing the dual-path workflow
  (greenfield and migration converging at `/camel-implement`)

- **Jakarta EE namespace rule in `/camel-implement`**
  - When Camel version ≥ 4.0, `jakarta.*` packages are used for all Jakarta EE APIs
    (Servlet, JPA, JMS, Bean Validation, JAX-RS, JSON, Annotation, Mail, JTA, etc.)
  - Java SE packages (`javax.sql.*`, `javax.xml.*`) are explicitly exempt
  - Validation gate scans all generated files and replaces offending `javax.` references
    before saving

- **`onException` ordering constraint in `/camel-implement`**
  - Global `onException` (top-level `- onException:`) must be declared before all `- route:` blocks
  - Route-scoped error handling (`errorHandler:`, `doTry`/`doCatch`) stays inside the route
  - Placing a global `onException` after a route is a schema validation error, not a warning
  - Generated YAML template updated to show `onException` as the first element

### Changed

- **`/camel-flow` — simplified defaults, advanced patterns now opt-in**
  - `unmarshal`/`marshal` no longer suggested by default; included only when the user
    explicitly needs typed Java object processing
  - DataMapper/XSLT-saxon is now the preferred transformation approach for JSON↔JSON,
    JSON↔XML, and XML↔XML when schemas are available; `unmarshal` is a fallback only
  - Circuit Breaker, Idempotent Consumer, and Transactions moved from default questions
    to separate conditional questions asked only when contextually relevant:
    - Circuit Breaker: only when source or sink is an external HTTP/REST service
    - Idempotent Consumer: only when source is a message broker or deduplication is needed
    - Transactions: only when the flow writes to more than one external system

- **`/camel-implement` — `unmarshal` removed from default YAML template**
  - Generated route no longer includes `unmarshal` as a default step
  - `unmarshal` added only when TDD explicitly requires typed object processing and
    no DataMapper XSLT covers the transformation

- **Constitution — Principles 6, 7, 8 changed to informational**
  - Resilience (Circuit Breaker): enforcement changed from "trigger warnings" to
    "Informational only — apply when explicitly requested during flow design"
  - Transaction Handling: same change
  - Idempotent Processing: same change
  - Reference content preserved; enforcement removed to avoid adding advanced patterns by default

- **`camel-kit init` — removed MCP guide file copying**
  - `MCP-SETUP.md` and `MCP-TESTING.md` are no longer copied to `.camel-kit/` during init
  - Corresponding "See .camel-kit/MCP-SETUP.md" output lines removed

- **README.md rewritten** — focused on installation and the two entry points
  (`camel-kit init` for greenfield, `camel-kit init` + `/camel-migrate` for migration);
  detailed content delegated to `docs/`

- **Documentation updated** (`docs/commands.md`, `docs/user-guide.md`)
  - `/camel-flow` interactive steps reflect new conditional question structure
  - `/camel-implement` section documents the four generation constraints:
    unmarshal opt-in, DataMapper preference, `onException` ordering, Jakarta EE namespaces
  - Best Practices table updated: Resilience and Idempotency marked as opt-in;
    Data Format Discipline updated to reflect DataMapper-first approach;
    two new rows for Jakarta EE namespaces and `onException` ordering


### Added

- **Skills-based architecture with MCP integration**
  - Converted 5 commands to skills standard with YAML frontmatter and metadata
  - Commands now use kebab-case naming: `/camel-project`, `/camel-flow`, `/camel-implement`, `/camel-validate`, `/camel-test`
  - All skills are user-invocable and discoverable by AI agents
  - On-demand guide loading for token optimization (60-70% token savings)
  - Bundled component skills structure for offline use

- **Apache Camel MCP Server Integration**
  - Automatic project-specific MCP configuration during `camel-kit init`
  - Support for 3 AI agents: Claude Code (`.mcp.json`), IBM Bob (`.bob/mcp.json`), Gemini CLI (`.gemini/mcp.json`)
  - 15 MCP tools available, 7 actively used across skills
  - Real-time catalog queries: `camel_catalog_components`, `camel_catalog_component_doc`
  - Route validation: `camel_validate_route`, `camel_route_context`
  - Security analysis: `camel_route_harden_context` with 47 automated checks
  - Version management: `camel_version_list`
  - 60-70% token savings compared to loading full catalog
  - Always-current documentation matching exact Camel version

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
  - Support for all transformation types:
    - Direct copy, nested flattening, date/time formatting
    - String concatenation, numeric calculations
    - Conditional logic (IF, CHOOSE-WHEN-OTHERWISE)
    - Array iteration with position tracking
    - Parameter usage from Camel context
  - JSON transformation with `fn:json-to-xml()` and `fn:xml-to-json()`
  - XML namespace preservation and handling
  - Automatic integration in route YAML with xslt-saxon component
  - Parameter passing from route to XSLT
  - Best practices and limitations guidance

- **Documentation**
  - `docs/MCP-TOOLS-REFERENCE.md` - Comprehensive MCP tools documentation with 23 invocation points
  - Updated all documentation with MCP integration details
  - Updated all slash command references to kebab-case
  - Enhanced transformation sections in user guide
  - Added MCP configuration examples for all 3 agents

### Changed

- **InitCommand improvements**
  - Create MCP configs only for selected AI agent (not all 3)
  - Fixed JAR filesystem handling for bundled skill distribution
  - Skills copied to both `.bob/commands/` (flat) and `.bob/skills/` (full structure)
  - Clean project initialization without unnecessary files
  - **Removed redundant catalog downloads** - Component and Kamelet catalogs are no longer downloaded during init
    - MCP server queries catalogs in real-time from Maven Central and GitHub
    - YAML DSL schema not needed (Maven validator plugin downloads its own)
    - Only Citrus schemas are downloaded (used by `/camel-test` skill)
    - Faster init, smaller `.camel-kit/.cache/` folder
    - 60-70% reduction in cached data

- **camel-implement skill - Route validation with MCP**
  - Replaced Maven YAML DSL Validator with MCP `camel_validate_route` tool
  - Validates all endpoint URIs against Camel catalog in real-time
  - Checks component options and required parameters
  - Catches typos and suggests corrections automatically
  - Consistent MCP-first approach throughout workflow
  - Faster validation feedback, no Maven execution needed
  - Renumbered steps: Step 5 is now Route Validation with MCP, Steps 6-11 adjusted

- **File generation locations corrected**
  - All generated routes now in project root (NOT in `.camel-kit/`)
  - `{flow-name}.camel.yaml` → Project root
  - `application.properties` → Project root
  - `docker-compose.yaml` → Project root
  - `run.sh` → Project root (executable)
  - XSLT files → Project root (same folder as route)
  - Test files → `test/` directory in project root
  - Schemas → `schemas/` directory in project root
  - `.camel-kit/` reserved ONLY for internal metadata

- **Skills structure enhanced**
  - `/camel-flow` now captures detailed field mappings, parameters, and conditional logic
  - `/camel-implement` generates DataMapper XSLT automatically
  - All skills include explicit file location instructions
  - TDD template expanded to 7 sections for transformations:
    - Section 3.2: Field Mappings
    - Section 3.3: Transformation Parameters
    - Section 3.4: Conditional Mappings
    - Section 3.5: Collection/Array Mappings
    - Section 3.6: Transformation Rules
    - Section 3.7: Additional Processing Steps (EIPs)

- **Documentation updates**
  - All command references changed from `/camel.X` to `/camel-X` throughout
  - README.md updated with MCP features and transformation capabilities
  - docs/user-guide.md enhanced with MCP Integration section
  - docs/commands.md updated with MCP tools by command
  - docs/skills-architecture.md updated with MCP + skills comparison
  - examples/order-processing/README.md updated with kebab-case commands
  - CONTRIBUTING.md updated with skills-based template format

### Fixed

- MCP configuration generation now creates only the config for the selected agent
- Project initialization no longer creates backup/temporary files
- Skills are properly distributed from JAR filesystem to project folders
- File paths in skills explicitly state project root vs .camel-kit folder

## [0.2.0] - 2025-02-18

### Added
- **Camel version updated to 4.18.0** (LTS)

- **Camel-Kit logo** - Added camel-kit.gif logo inspired by K.I.T.T. from Knight Rider
  - Displayed at the top of README.md
  - Represents AI-guided integration development

- **Enhanced error handling guidance** in constitution and design patterns:
  - Three exception handling approaches: `doTry/doCatch/doFinally`, `errorHandler`, `onException`
  - Error handler types: `noErrorHandler`, `defaultErrorHandler`, `deadLetterChannel`
  - `onException` clause with `handled()`, `continued()`, `markRollbackOnly()`
  - Detailed examples for each approach

- **Transaction handling patterns**:
  - Transaction propagation policies (PROPAGATION_REQUIRED, PROPAGATION_REQUIRES_NEW, etc.)
  - Using `.transacted()` DSL for transaction management
  - Combining transactions with exception handling via `markRollbackOnly`
  - Examples for local and distributed transactions

- **Kafka consumer scaling guidance**:
  - Consumer-to-partition relationship and assignment rules
  - Starving consumer scenarios and optimal configurations
  - `consumersCount` parameter usage with Kubernetes replicas
  - Offset reset strategies (earliest, latest, none)

- **Kubernetes deployment best practices**:
  - ConfigMaps and Secrets patterns for configuration
  - Health probes (liveness, readiness, startup)
  - Resource requests and limits configuration
  - Configuration hierarchy (environment variables, ConfigMaps, defaults)

### Changed

- **Rewritten in Java** - Complete rewrite from Python to Java for better JBang integration
  - Multi-module Maven project structure (camel-kit-core, camel-kit-main, camel-kit-plugins)
  - Installation via JBang: `jbang app install camel-kit@io.github.luigidemasi:camel-kit-main:0.2.0-SNAPSHOT`
  - Uses PicoCLI for command-line parsing
  - Uses JLine for terminal handling

- **Camel version updated to 4.14.5** (LTS)

- **Citrus version updated to 4.9.2**

- **Maven Wrapper included in generated projects**
  - `mvnw` (Unix) and `mvnw.cmd` (Windows) generated during init
  - Enables portable Maven execution without pre-installed Maven

- **Validation uses MCP and Maven plugins**:
  - Camel route validation: MCP `camel_validate_route` tool (validates URIs, options, catches typos)
  - Citrus test validation: `./mvnw com.dataliquid.maven:json-yaml-validator-maven-plugin:2.0.0:validate`

- **Citrus JSON schemas downloaded during init**
  - Schemas extracted from `citrus-catalog-schema` JAR on Maven Central
  - Cached in `.camel-kit/.cache/citrus/{version}/`
  - Quick reference files generated for AI agent consumption

- **Updated constitution.md** - Renumbered sections after adding transaction handling
  - Section 4: Enhanced error handling with three approaches
  - Section 7: New transaction handling section
  - Section 15: New Kafka consumer scaling section
  - Section 16: New Kubernetes deployment section

- **Updated design-patterns.md**:
  - Enhanced Data Integrity Pattern with transaction propagation policies
  - Enhanced Service Instance Pattern with Kafka consumer scaling details
  - Added offset reset strategies and Kubernetes scaling patterns

- **Updated docs/constitution.md**:
  - Added principles 11-13 (Transaction Handling, Kafka Consumer Scaling, Kubernetes Deployment)
  - Added validation codes CONST-009 through CONST-011
  - Enhanced error handling section with three approaches

- **Rewrote CONTRIBUTING.md**:
  - Changed from Python development to Java/Maven development
  - Updated prerequisites (Java 17+, JBang, Maven)
  - Updated build commands (./mvnw instead of uv/pip)
  - Changed coding standards from Python/PEP 8 to Java conventions
  - Updated testing from pytest to JUnit 5
  - Changed contribution types (commands/templates instead of agents)

- **Updated README.md**:
  - Added camel-kit.gif logo at the top
  - Better visual presentation with centered logo

### Fixed

- Template consistency across all locations (templates/, camel-kit-core/src/main/resources/templates/, src/camel_kit_cli/templates/)

### Removed

- **Python implementation** - Replaced with Java/JBang
- **`camel-kit catalog` command** - Catalogs are downloaded during init and cached
- **`camel-kit agents` command** - Agent information available via `--help`
- **`camel-kit version` command** - Use `camel-kit --help` for version info

## [0.1.3] - 2025-02-13

### Added

- **YAML DSL Schema download**: Schema is now automatically fetched and cached during `camel-kit init`
  - Cached alongside component and Kamelet catalogs in `.camel-kit/.cache/camelYamlDsl-{version}.json`
  - Can be refreshed with `camel-kit catalog fetch --force`
  - Schema status shown in `camel-kit catalog info`

### Changed

- **`/camel-implement` now uses component catalog** during YAML generation:
  - New Step 3: Component Catalog Lookup before generating YAML
  - Looks up each component in `.camel-kit/.cache/components-{version}.json`
  - Verifies component exists and can be used as consumer/producer
  - Identifies required vs optional options from `properties[*].required`
  - Determines option placement: `kind: "path"` in URI, `kind: "parameter"` in parameters block
  - Uses `componentProperties` from catalog for `camel.component.<name>.<prop>` configuration
  - Generates Component Verification Report showing catalog lookup results

## [0.1.2] - 2025-02-13

### Added

- **Claude Code support**: Added Anthropic Claude Code as a supported AI agent
  - Commands are generated in Markdown format (`.claude/commands/`)
  - Uses `$ARGUMENTS` placeholder for arguments
  - Requires `claude` CLI tool
- YAML schema validation in `/camel-validate` and `/camel-implement`
  - Schema fetched from GitHub: `https://raw.githubusercontent.com/apache/camel/camel-{version}/dsl/camel-yaml-dsl/camel-yaml-dsl/src/generated/resources/schema/camelYamlDsl.json`
  - Validates syntax, schema compliance, and property placeholders
  - **Auto-fix**: Automatically fixes common validation errors (handled expressions, property case, etc.)
  - Quick validation via `camel run --check <file>.camel.yaml application.properties`

### Changed

- Renamed `/camel-context` to `/camel-project` for clarity
- `/camel-project` now focuses only on business landscape (purpose, systems, integration goals)
- Technical details (sources, sinks, components) moved to `/camel-flow` command
- Removed test generation prompt from `/camel-implement` (use `/camel-test` instead)
- Updated `camel run` examples to include `application.properties` file
- `/camel-implement` now generates `application.properties` with component-level configuration
- `/camel-implement` now generates `camel.jbang.dependencies` in `application.properties` for Maven dependencies
- `/camel-validate` now checks generated YAML files against schema and application.properties
- Updated "Data Format Discipline" constitution principle: unmarshal is now guidance-based (when needed) instead of mandatory
- Clarified validation order: schema validation (JSON Schema, XSD) happens before unmarshal; bean validation after
- **Improved `/camel-test` command**:
  - Testcontainers are now mandatory for external systems (Kafka, PostgreSQL, MongoDB)
  - Added `application.test.properties` generation with testcontainer variables
  - Improved Citrus YAML syntax with correct property names
  - Added testcontainer-exposed variables reference table
  - Better structured test template with infrastructure setup, test execution, and cleanup phases

### Fixed

- Fixed Camel JBang configuration: use `camel.component.<name>.<prop>` for component settings
- Fixed bean definitions: use `#class:` prefix for bean instantiation
- Fixed property loading: `application.properties` must be included in `camel run` command
- Fixed Citrus `camel.jbang.run` YAML schema: use `files` list instead of `integration.file`
- Removed invalid `systemProperties` property from Citrus test examples
- Fixed `onException` YAML syntax: `handled` requires expression format (`constant: expression: "true"`), not boolean

## [0.1.1] - 2025-02-12

### Added

- **Gemini CLI support**: Added Google Gemini CLI as a supported AI agent
  - Commands are generated in TOML format (`.gemini/commands/`)
  - Uses `{{args}}` placeholder for arguments
  - Requires `gemini` CLI tool

### Changed

- Merged `/camel-flow` and `/camel-route` commands into single `/camel-flow` command
- Renamed `/camel-generate` to `/camel-implement` for clarity
- Simplified `/camel-project` to ask only high-level questions (purpose, systems, flows)
- Updated `/camel-flow` to ask questions one at a time interactively
- Technical details (protocols, EIPs, error handling) now captured in `/camel-flow` instead of `/camel-project`

### Fixed

- Fixed Citrus YAML schema issues in `/camel-test`:
  - Variables now use list format with `name`/`value` properties
  - Testcontainers use simple format (`kafka: {}`, `postgresql: {}`)
  - SQL actions use `dataSource` (camelCase) and `statement:` property
  - Removed invalid `wait` property from `camel.jbang.run` action
  - Message body uses `data:` for inline content (not `file:`)
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

[Unreleased]: https://github.com/luigidemasi/camel-kit/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/luigidemasi/camel-kit/compare/v0.1.3...v0.2.0
[0.1.3]: https://github.com/luigidemasi/camel-kit/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/luigidemasi/camel-kit/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/luigidemasi/camel-kit/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/luigidemasi/camel-kit/releases/tag/v0.1.0
