# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).


## [Unreleased]

## [0.4.0] - 2026-09-15

Changes since 0.3.1. Ship remains a Technology Preview.

### Added

- **Claude Code native Ship (#222)** — eligible sessions relay controller-issued stages through the dedicated
  read-only `camel-ship-worker` project subagent via the `Agent` tool. `--backend claude-native` reuses the native
  task/result, recovery, oversight and deterministic publication contract while preserving Claude Code permissions,
  hooks and existing roles. Fresh and regenerated standalone/plugin workspaces receive the native skill and worker;
  runs retain their recorded host.

- **GitHub Copilot CLI native Ship (#226)** — eligible sessions relay controller-issued stages through the dedicated
  read-only `camel-ship-worker` custom agent. `--backend copilot-native` reuses the native task/result, recovery,
  oversight and deterministic publication contract while preserving Copilot permissions, hooks and existing roles.
  Fresh and regenerated standalone/plugin workspaces receive the native skill; runs retain their recorded host.

- **Bob 2 native Ship (#223)** — eligible Bob sessions relay controller-issued stages to a dedicated read-only native
  subagent. The controller accepts bounded text proposals, binds task/result hashes in run state, and retains oversight,
  deterministic validation and guarded publication. `--backend bob2-native`, `--json` and `--submit` expose the handoff;
  resumable runs retain their backend, and legacy state migrates to Pi. Other agent execution models stay unchanged.
  The relay preserves failed-run diagnostics and documents explicit recovery for unreadable handoffs.
  Ship preserves dependency JAR filenames in its isolated validation payloads so Citrus resource lookup also works
  when the temporary directory has no spaces.

- **Google Antigravity** (`--ai antigravity`) — native `.agents/skills/`, worker/reviewer custom agents,
  `AGENTS.md`, and `.agents/mcp_config.json`. Shared pipeline traits keep orchestration and report writes in the
  primary conversation. Existing unrelated MCP servers and settings are preserved; native permissions remain active.
  Initialization rejects duplicate MCP object keys before writing project files, and Doctor reports them as failures.
- **Retired-agent migration** — Gemini (`--ai gemini`) and IBM Bob v1 (`--ai bob`) are no longer supported.
  Reinitialize with `--ai antigravity` or `--ai bob2`, respectively; Bob 2 remains the default. Init and Doctor
  provide migration guidance. Antigravity reinit removes only the former generated Gemini imports from `GEMINI.md`,
  preserving custom rules; Bob 2 reinit removes known obsolete Bob v1 mode rules.

- **Unanswered migration questions (#208)** — bounded `camel-migrate` steps return open decisions to the parent
  conversation, which asks one question at a time and resumes the step with confirmed answers. Ship workers receive
  the active oversight policy; stage records and command summaries retain unanswered questions and applied defaults
  under `--ask never`, while `always` and `smart` keep their existing pause behavior. Later stages receive the
  recorded questions and defaults as worker-reported context. Run-state schema advances from v4 to v5 and worker-result
  schema from v1 to v2; v4 records and v1 results remain readable without inventing missing historical questions.
  Legacy results that report material ambiguity without structured questions produce a warning in final/status output.
  Native dispatch instructions distinguish missing context (`NEEDS_CONTEXT`) from action-specific authorization
  (`NEEDS_USER_CONFIRMATION`) and defer question handling to the owning workflow.

- **Design-spec catalog evidence (#207)** — section 5 now records the matched runtime, full platform BOM, returned
  Camel version, and each selected artifact's verification result and tool provenance. Design-time catalog producers,
  migration gates, and re-planning share that evidence location; failed verification stays an open design question.

- **Evidence-qualified migration analysis (#78)** — `camel-migrate` now materializes `migration-analysis.md` between
  vendor discovery and design. The register records independently testable behavioral assumptions and evidence gaps as
  Confirmed, Inferred, or Unknown, and carries unresolved risks into Phase 2 instead of assuming API compatibility.
  The same artifact adds a coverage-qualified source-retirement candidate audit for both graph-assisted and graph-less
  discovery, preserving broken references and unknowns without treating candidates as dead or safe to delete. Migration
  strategy is classified per independently switchable scope as `Incremental candidate`, `Single cutover required`, or
  `Undetermined - evidence needed`: incremental guidance requires current confirmation of an existing controllable seam
  plus confirmed target constraints and pre-cutover validations. It identifies design candidacy, not cutover readiness.
  A single cutover requires a closed, operator-confirmed ingress/control inventory and complete confirmed evidence that
  every seam candidate inside its named source and operational boundaries is absent or unsafe. After design,
  `camel-migrate` also produces `migration-runbook.md` with deployment, cutover, validation, rollback, reconciliation,
  soak, and source-retirement decision steps. Missing operational facts use
  `Unknown — operator decision required: <missing fact>` instead of invented values. Package approval does not
  authorize provisioning, deployment, cutover, traffic switching, rollback, reconciliation, or source retirement;
  retirement remains a separate named operator decision after validation, reconciliation, and soak have passed.

- **Explicit design scope boundaries** — greenfield design specs now record a top-level `Not Doing (and Why)` section
  with a concrete reason for every excluded capability. Brainstorm captures these boundaries during the interview, and
  planning omits excluded work while execution passes the boundaries to implementers and spec-compliance reviewers, so
  excluded features are treated as scope violations instead of opportunistic improvements.

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
  - Copilot MCP config uses the documented `tools` schema; Qwen uses `includeTools`, OpenCode retains permission prompts without ignored approval fields, and `doctor` validates each target's runtime-supported contract
  - Internal Copilot guide skills are marked so Copilot does not directly or automatically invoke them
  - README, command reference, user guide, architecture docs, agent architecture guide, and changelog document the Copilot target and skill-based invocation model

- **Citrus MCP integration for test generation** — generated agent MCP configs now include the published Citrus MCP server (`org.citrusframework:citrus-mcp-server:5.0.1`) so `camel-test` can verify Citrus YAML actions, endpoints, and schemas during test generation.
  - Added Citrus distribution properties (`citrus.version`, `citrus.mcp.version`, `citrus.mcp.repos`)
  - `--citrus-version default` now resolves to `5.0.1`
  - Generated project config records `citrus.version`
  - Citrus MCP is preferred over cached quick references, with same-version cache fallback only

- **IBM Bob 2 AI target (`--ai bob2`)** — added native Bob 2 project assets and subagent orchestration.
  - New `bob2` agent registry descriptor, generator strategy, and `Bob2Generator`
  - Bob 2 workspaces still generate under `.bob/` with `.bob/commands`, `.bob/skills`, capability-scoped `.bob/agents`, role text under `.bob/personas`, `.bob/custom_modes.yaml`, and `.bob/mcp.json`
  - Bob 2 custom modes use the current Bob 2 tool groups (`read`, `edit`, `execute`, `mcp`, `skill`, `todo`, `artifact`, `subagent`, `mode`) with `allowedSubagents`
  - New Bob 2 rules, dispatch template, and traits for native `spawn_subagent` orchestration with factual-discovery `explore`, generated `camel-worker` and read/MCP-only `camel-reviewer` presets, and `fork_context`
  - Bob 2 command stubs include markdown frontmatter from workflow metadata, including `description` and argument hints
  - Bob 2 generated skills keep the shared `SKILL.md` content and append Bob 2 traits
  - Bob 2 and Qwen skill copies include their runtime-readable `user-invocable` aliases while preserving source metadata

- **Bob 2 coverage and regression tests** — added registry, factory, generator, command-frontmatter, skill-metadata, custom-mode, doctor, and CLI default tests for Bob 2.

- **Project graph analysis (`camel-kit-graph` module)** — new module that builds an in-memory graph of an entire Camel project and exposes it through CLI commands
  - 9 parsers: `YamlRouteParser`, `XmlRouteParser`, `JavaGraphParser`, `GroovyGraphParser`, `ConfigParser`, `PomParser`, `MuleXmlFlowParser`, `DataWeaveParser`, `CrossLinker` (for direct/seda, component, and config cross-references)
  - Graph model: `ProjectGraph` container with `GraphNode`, `GraphEdge`, `NodeType`, `EdgeType` records; JSON round-trip via `GraphSerializer`
  - Query engine: `GraphQuery` (find, neighbors, path, subgraph, impact, stats), `RouteFlowTracer`, `RouteTopology`, `DeadCodeAnalyzer` (unused deps, orphaned routes, stale config)
  - `GraphBuilder` orchestrator with parallel parsing across all file types
  - `GraphVisualizer` — generates interactive HTML visualizations with 4 library options: Cytoscape, D3, vis-network, AntV G6

- **Graph CLI commands** — commands under `camel-kit graph`:
  - Navigation: `find`, `neighbors`, `path`, `subgraph`
  - Camel-specific: `route-flow`, `route-topology`, `impact`, `dead-code`, `stats`
  - Composite (AI-facing): `project-context`, `project-norms`, `route-context`
  - Output: `generate` (builds graph JSON), `visualize` (produces HTML)
  - Command prefix detection wired into `CamelKitMain`

- **Graph-aware skills** — skills leverage graph analysis when available
  - `camel-implement`: graph-project-context guide for consistent property naming, bean reuse, version alignment across routes
  - `camel-validate`: graph-project-context for project-aware validation; graph-dead-code-report for dead code detection; `PROJECT_NORMS` for dynamic quality thresholds
  - `camel-test`: graph-project-context for cross-route test awareness and endpoint classification
  - `camel-migrate`: Step 0 graph detection fork; graph snapshot in Phase 1 analysis; per-route graph impact analysis in Phase 2 design-spec generation
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

- **Four-stage orchestrated pipeline** — replaced the linear `/camel-project` → `/camel-flow` workflow with
  Design → Plan → Execute → Validate:
  - `/camel-brainstorm` produces `design-spec.md`; `/camel-migrate` is the alternate Design entry for migrations
  - `/camel-plan` decomposes the approved design into an implementation plan
  - `/camel-execute` dispatches implementation, testing, adversarial review, spec-compliance review, code-quality review,
    and internal runtime verification; `/camel-validate` provides the final static validation stage
  - Design approval authorizes downstream planning and execution; artifacts live under `docs/camel-kit/<PIPELINE_ID>/`
  - `/camel-start` routes requests to the appropriate entry point; `/camel-flow` has been removed

- **`camel-kit plan analyze` command** — parses implementation plan markdown files and computes parallel execution waves; outputs a JSON task graph showing which tasks can run concurrently

- **`--source-platform` option for `camel-kit init`** — allows specifying the source platform during project initialization for migration workflows

- **`camel-verify` — internal runtime verification** — `/camel-execute` dispatches a bounded build-or-smoke,
  Citrus-test, and report loop for Camel Main/JBang, Spring Boot, and Quarkus projects. Failures are classified and
  routed to the appropriate repair step, with up to 15 build/test repair attempts. The skill is an internal guide;
  standalone quality checks use `/camel-validate`.

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
  - Generated skills and agent templates receive distribution values through Qute placeholders
  - `InitCommand` and catalog classes read Maven repo URLs from `DistributionConfig`

- **Multi-agent parity** — expanded the supported AI targets while preserving shared workflow and output contracts
  - Added Qwen (`--ai qwen`) and OpenCode (`--ai opencode`)
  - Shared skills preserve workflow contracts across the supported targets
  - Agent-specific generators apply each target's native instructions, permissions, and dispatch model
  - `AgentGenerator` interface with `AgentGeneratorFactory` routing
  - `QuteTemplateEngine` for agent-specific template rendering (replaced `String.replace()`)
  - `InitContext` carries distribution and agent info through the init pipeline
  - Iron laws embedded in each agent's instruction file
  - Platform-specific dispatch block templates appended to SKILL.md during init
  - Qwen generates four bounded leaves plus a non-auto-discovered persona library; interactive workflows remain in the primary session

- **Iron laws** — 6 non-negotiable pipeline rules enforced across all skills
  1. MCP Catalog Verification — every component verified via MCP before use
  2. Constitution Compliance — every route follows the project constitution
  3. No Code Without Design Approval and an Existing Plan — one design approval authorizes plan and execution
  4. Spec Compliance Before Quality — ordered review in the correct sequence
  5. Adversarial Code Review — critic lanes run before spec and quality review
  6. Surgical Changes — implementation tasks touch only their requested scope

- **Migration support expanded** — `/camel-migrate` now handles Apache Camel 2.x/3.x and JBoss Fuse migrations in addition to MuleSoft Mule

### Changed

- **Knowledge MCP 0.0.1** — pin the fixed server release; development snapshots of Camel Kit
  continue to use the released Knowledge server.

- **GitHub Actions Node 24 migration (#234)** — update JavaScript actions to Node 24 versions across build,
  snapshot, Pages, and website-impact workflows. Ship's Node runtime pin is unchanged.

- **Camel Ship is a Technology Preview** — all backends are still being stabilized, may change, and are not recommended for production use. CLI help and text summaries, generated skills, and documentation now show this status. The staged `/camel-start` workflow remains the recommended path. Runtime compatibility tiers and `--accept-experimental` keep their existing meaning; Ship validation and publication gates are unchanged.

- **Quarkus platform 3.33.3.2** — update the default platform BOM and its Camel version to 4.18.3,
  matching the published BOM's Camel Quarkus 3.33.2 dependencies. Update the supported-version mapping,
  installed guidance, documentation, and companion Forage stream to 1.4.0.

- **Ship state inside the project (#217)** — `camel-kit ship` now stores run records, evidence, Pi session
  transcripts, the staged Execute workspace and the default validation catalog repository under
  `<project>/.camel-kit/ship/state/` instead of `$XDG_STATE_HOME/camel-kit/ship` or `~/.local/state/camel-kit/ship`.
  `CAMEL_KIT_SHIP_STATE_HOME` still overrides the location; the XDG and home fallbacks are removed. `.camel-kit/ship/`
  is reserved for the controller: the tree policy denies it (policy schema v6 → v7), so it is never copied into the
  staged workspace, never digested and never published, and the state directory carries a self-ignoring `.gitignore`.
  Any other state directory inside the project is still rejected with `state-project-overlap`; an existing
  incompatible `.gitignore` in the state root is rewritten and a symbolic link there fails as `state-corrupt`. Git
  discovery is bounded at the staged Execute workspace, the local command runner and the frozen validation snapshot
  (`GIT_CEILING_DIRECTORIES`), so Git commands run by the worker or by validation inside the nested state directory
  cannot inspect or alter the live repository; because Git reads that variable as a colon-separated list, project and
  state directory paths containing `:` are rejected at start (`project-invalid`, `state-root-invalid`) instead of
  running unbounded. Material identities no longer embed the tree-policy digest (framing
  v1 → v2), and publication journals written by earlier releases are still recognised, so a publication interrupted
  before the upgrade recovers or rolls back on resume. Summary continuation commands include `--project-dir` when the
  run was started with it. Runs recorded under the previous default are not found at the new location; set
  `CAMEL_KIT_SHIP_STATE_HOME` to the old path to finish them. Execute workspaces bound under the previous release fail
  their staleness check on resume and restart the stage.

- **Citrus 5.0.1 (#215)** — upgrade the default Citrus test dependencies and MCP server to 5.0.1,
  remove the temporary M1 server pin, and update Ship compatibility for Camel 4.22.0 and 4.18.4.
  Adapt the direct Citrus launcher to the GA context builder and test-engine API packages. The 5.0.1 server
  supersedes the temporary M1 startup workaround (#147).

- **Camel 4.22 LTS default and centralized distribution versions (#209)** — Camel Main, Spring Boot, and the Camel MCP
  server now default to `4.22.0`. The supported Main and Spring Boot matrix is `4.22.0,4.18.4`; Spring Boot maps those
  lines to `4.1.0` and `3.5.16`, and Forage maps them to `1.6.0` and `1.4.1`. Quarkus remains on its independent matrix.
  Runtime defaults, generated MCP configuration, installed skill guidance, Forage tables, and Ship functional rows now
  derive from `distribution.properties`; a small idempotent helper synchronizes the remaining Maven model-time mirror.
  Ship records exact 4.22.0 and 4.18.4 validator/catalog evidence. Camel 4.22 adds Jactl to the known-expression
  classifier, while Ship v1 continues to reject it under the existing Simple-only manifest policy.

- **Shared Camel security checklist (#205)** — the security rules restated across the design guide, the validation
  guides, and the review personas now have one canonical source, `skills/shared/camel-security-checklist.md`. The
  consumers reference it instead of restating the rules, the drifted vault-reference and log-masking snippets are
  reconciled, and the canonical snippets use documented Camel placeholder functions and component options. The
  remaining restatements in the implement advanced-patterns guide, the foundational pattern guides, the constitution
  example, and the Bob gate templates are aligned with it.

- **Context authority across workflows (#76)** — loaded files, logs, MCP responses, documentation, and delegated results
  supply only purpose-specific data after validation; they cannot direct actions, expand scope, waive gates, or provide
  approval. Actions proposed only by loaded content require action-specific user confirmation; normal in-scope actions
  remain governed by the shipped workflow and the user's request. The shared context-authority guide carries this
  contract into generated workflow instructions.

- **Ship VALIDATE runs evidence commands as direct JVMs — Bubblewrap is no longer required** — the OS-level sandbox was removed from VALIDATE in line with the Ship product boundary. Evidence commands now launch as direct child JVMs on a frozen read-only copy of the accepted candidate tree, with a scrubbed environment and a command-private home and temporary directory; network access during validation is avoided by replacing every non-direct Camel endpoint with an in-memory stub, not by OS-level sandboxing.
  - Linux hosts no longer need `bwrap` for `camel-kit ship`; the authenticated Pi/Linux live gate likewise runs without it
  - The internal attestation stack, the Maven Central double-download verification, and the redundant catalog artifact reader were removed with it; catalog evidence keeps its digest and length checks

- **Certified Pi versions: `0.84.2` and `0.83.0`** — the bundled distribution now carries a certified-version list (`pi.supported`); Ship reports every listed version as `supported`, and each entry has completed an authenticated Pi/Linux live-gate run. `pi.version=0.84.2` is the primary install target named in guidance messages (Node stays `22.22.2`; Pi `0.84.2` requires Node `>=22.19.0`). Other detected versions still run only with `--accept-experimental`.

- **Ship harness entry points now delegate to the local controller** — `/camel-ship`, `$camel-ship`, and `/skill:camel-ship` forward their arguments to the configured registered `camel-kit ship` or `camel kit ship` command instead of maintaining a prompt-owned workflow. The local controller is the sole owner of Ship stages, run state, evidence, oversight, and guarded publication.
  - Existing generated workspaces must be regenerated with the same command surface and agent, using `camel-kit init --here --ai <same-agent> --force` or `camel kit init --here --ai <same-agent> --force`; commit or back up workspace customizations first because `--force` rewrites generated configuration, instructions, skills, and templates
  - Initialization aborts up front — before writing any project files — when a managed agent path (for example a symlinked `.claude` or `.bob` from a dotfiles setup) is a symbolic link; the error names the link. Replace the link with a real directory before running the upgrade command
  - Re-initialization removes obsolete generated Ship assets; eligible Bob 2, Copilot CLI, and Claude Code sessions
    receive the native Ship skills and workers described above
  - Pre-controller `.camel-kit/ship-state.json` and non-manual `.camel-kit/pipeline.json` state is intentionally not resumable and must be archived outside the project before starting Ship; manual-mode `.camel-kit/pipeline.json` remains supported by standalone pipeline skills and validated `--start-from` imports
  - GitHub Copilot CLI uses native project skills under `.github/skills/` without generating unsupported `.github/commands/`; older command files are inert and may be removed after preserving local edits
  - Pi exposes Ship through `/skill:camel-ship` and removes the older `.pi/prompts/camel-ship.md` alias, whose argument expansion could flatten quoted option values

- **Default AI target changed to IBM Bob 2** — `camel-kit init` and `camel kit init` now default to `--ai bob2` when no `--ai` option is supplied.
  - CLI help and documentation now mark Bob 2 as the default target

- **Progressive skill loading via meta-router** — `/camel-start` routes users into the greenfield or migration
  pipeline and loads guides on demand. It is the sole auto-discovered entry point in shared source metadata;
  generated public entry points follow each agent's native discovery rules, including the Bob 2 overrides above.
  Pipeline skills are brainstorm or migrate, plan, execute, and validate; standalone utilities are ship, knowledge,
  and debug. Design, implement, test, and verify remain internal guide libraries.

- **Skill architecture refactored to orchestrator pattern** — the implementation, validation, migration, and test skills rewritten as slim orchestrator manifests that load micro-guides on demand; monolithic `SKILL.md` files split into focused, reusable topic guides; large template files split into topic-specific micro-templates

- **`camel-kit-knowledge` separated to its own repository** — knowledge indexer, embedding, schema, index, and MCP modules moved to `camel-kit-knowledge` (separate repo with independent `0.0.1-SNAPSHOT` version line); Camel-Kit connects through the separately packaged MCP runner artifact

- **Template engine migrated to Qute** — all templates migrated from `String.replace()` to Qute engine (`qute-core` dependency); supports conditional blocks, loops, and distribution-property substitution

- **Documentation rewritten** — all docs updated to reflect the four-stage orchestrated pipeline, user-invocable workflows, AI-agent targets, Groovy DataMapper, and internal runtime verification

- **`/camel-project` deprecated** — replaced by `/camel-brainstorm`

- **`/camel-knowledge` progressive-loaded** — available through its generated command and used internally by pipeline skills without automatic skill discovery

- **Agent traits system — build-time append of agent-specific instructions** — `applyTraits()` in `DefaultGenerator` scans `templates/traits/{agent}/` and appends skill- or guide-level `.append.md` files during `camel-kit init`. Traits are idempotent through HTML comment sentinels (`<!-- TRAIT:agent -->`).

- **BizTalk documentation updated** — added BizTalk migration references to `docs/user-guide.md`, `docs/commands.md`, `docs/architecture.md`, `docs/camel-kit-overview.md`, `README.md`, `CONTRIBUTING.md`. BizTalkMigrationStarter repository URL corrected. Camel validator-starter component reference corrected.

### Removed

- **`/camel-flow` skill** — redundant 14-line redirect to `/camel-brainstorm` with greenfield preset, now handled by `/camel-start` routing

- **`camel-kit-graph-mcp` module** — graph MCP server removed; graph analysis now exposed exclusively through CLI commands (reduced MCP servers from 3 to 2)

- **Offline/standalone mode** — removed all offline mode code and the `--offline` flag

- **Red Hat references in Java source** — all distribution-specific values externalized to `distribution.properties`; Java code is distribution-neutral

- **Distribution variant selection** — simplified to single distribution; removed `--distribution` field and variant selection UI

### Fixed

- **OpenCode duplicate MCP configuration** — initialization rejects duplicate JSON/JSONC object keys in every
  project configuration layer before writing workspace files, and Doctor reports the offending file and key.
  Remove duplicate keys before retrying initialization; valid layered overrides, comments and trailing commas remain supported.

- **Bob 2 native workflow discovery (#213)** — Bob 2 generation marks the nine public
  command skills user-invocable in both metadata spellings, making them available through
  `/skills` and `$camel-*` in Shell and `/camel-*` in IDE. Setup guidance documents both
  native entry points. Doctor identifies hidden or empty public skills and unreadable
  invocation metadata, with regeneration instructions. Regeneration repairs earlier workspaces;
  internal helpers, compatibility command stubs, and Ship CLI delegation are preserved.

- **JBang launcher release synchronization (#145)** — Maven release preparation now keeps both tracked launcher fallbacks aligned with the release version and the following development snapshot.

- **Citrus MCP Doctor validation (#146)** — `doctor` now fails when persisted Citrus metadata or a post-Citrus-only JSON agent proves that the generated `citrus` server is required; legacy-capable agents without that metadata retain the actionable pre-Citrus regeneration warning.

- **Pi worker failure recovery (#169)** — recovered failure text is normalized before selecting a fallback diagnostic,
  so blank or NUL-only text in a worker-result marker cannot leave Ship stuck in `RUNNING`. The run reaches a failed
  state that can be retried.

- **Camel plugin command parity and public documentation (#193)** — registered `doc` and `nextId` under `camel kit`, added a direct standalone/plugin parity regression, and aligned stable-versus-snapshot installation, prerequisites, workflow, graph, Knowledge, agent, and Ship documentation.
  - Review hardening keeps validator leaves read-only, preserves unrelated OpenCode configuration during regeneration, resolves command prefixes only in Camel-Kit-owned resources, and installs the complete persona library for every supported target
  - `doctor` accepts pre-upgrade Qwen/OpenCode MCP layouts with upgrade warnings while retaining failures for malformed current layouts, and checks registered target assets for drift
  - Regeneration reports each retired generated asset it removes while preserving neighboring files
  - OpenCode regeneration recognises `opencode.json`, `opencode.jsonc`, `.opencode/opencode.json`, and `.opencode/opencode.jsonc` as project layers, updates them in place (comments, trailing commas, newline style, and symbolic links preserved), moves the Camel-managed `permission` and `mcp` entries into the highest-precedence existing layer, validates every layer before writing anything, and reports a malformed file as one concise error instead of a stack trace
  - `doctor` evaluates OpenCode permission rules per managed MCP server in OpenCode's last-match order and reports each finding against the layer that defines the rule

- **Ship Simple-expression validation (#179)** — replaced the narrow custom grammar with a bounded input gate, allowing
  Simple expressions such as dotted header and body lookups to reach Camel's own syntax validation. Size, character,
  and indirect-expansion checks remain enforced.

- **Ship resolver proxy and trust-store support (#177)** — dependency resolution now honors the JVM's proxy and TLS
  system properties, allowing downloads through configured proxies and custom trust stores.

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

- **README: `-d` flag in Camel JBang Plugin install command corrected to `--description`** — the `-d` short option is not recognized by current versions of Camel JBang. Fixed to use the correct `--description` long option.

- **Hardcoded version numbers in skill files replaced with Qute-substituted placeholders** — skill Markdown files contained hardcoded Camel/Quarkus version numbers (e.g., `3.33.0`, `4.18.0`, `3.27.2`) that drifted from `distribution.properties`, causing stale versions in generated projects. Added Qute-based placeholder substitution to `copySkills` using an escape-then-unescape approach (escape all `{` to `\{`, restore only known version keys, then Qute-render). New `{QUARKUS_PLATFORM_TABLE}` placeholder dynamically generates the Camel-to-Quarkus mapping table from `quarkus.platform.*` keys. `DistributionConfig.quarkusPlatformMappings()` added. 16 hardcoded values replaced across 8 skill files.

- **Fallback LTS version in `CatalogDownloader` no longer hardcoded** — `getLatestLtsVersion()` returned a hardcoded `"4.20.0"` when Maven Central was unreachable. Replaced with a `fallbackVersion` constructor parameter so callers provide the value from `DistributionConfig.camelMainVersion()`.

- **LTS version detection no longer relies on even-number heuristic** — `getLatestLtsVersion()` assumed LTS versions have even minor numbers (`minor % 2 == 0`), which is not officially guaranteed by Apache Camel. Replaced with an explicit `Set<String>` of known LTS minor versions passed via constructor, built from `DistributionConfig.camelMainSupported()`.

- **Bob guide resolution, splash screen, MCP stdio transport**

- **Qwen Code tool names** — corrected sub-agent definitions to use correct Qwen Code tool names

- **Skill quality audit** — 7 evaluation passes (55+ fixes) across all 6 skills: MCP param corrections, context pollution, anti-hedging, completion gates, batch mode, guide path notation, smoke test rollback strategy, DataMapper test examples, runtime-aware test config, vendor detection recovery

- **Knowledge MCP version lookup** — `knowledge.mcp.version` is used correctly in `maven-metadata.xml` parsing

## [0.3.1] - 2026-03-02

### Fixed

- Replace `{{DATE}}` and `{{CAMEL_VERSION}}` placeholders in constitution during init
- `.kaoto` format hardening and constitution simplification
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

## 0.1.3 - 2025-02-13

### Added

- **YAML DSL Schema download**: Schema is now automatically fetched and cached during `camel-kit init`

### Changed

- **`/camel-implement` now uses component catalog** during YAML generation:
  - New Step 3: Component Catalog Lookup before generating YAML
  - Looks up each component in `.camel-kit/.cache/components-{version}.json`
  - Verifies component exists and can be used as consumer/producer
  - Identifies required vs optional options from `properties[*].required`
  - Determines option placement: `kind: "path"` in URI, `kind: "parameter"` in parameters block

## 0.1.2 - 2025-02-13

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

## 0.1.1 - 2025-02-12

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

## 0.1.0 - 2024-XX-XX

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

[Unreleased]: https://github.com/luigidemasi/camel-kit/compare/camel-kit-0.4.0...HEAD
[0.4.0]: https://github.com/luigidemasi/camel-kit/compare/camel-kit-0.3.1...camel-kit-0.4.0
[0.3.1]: https://github.com/luigidemasi/camel-kit/compare/camel-kit-0.3.0...camel-kit-0.3.1
[0.3.0]: https://github.com/luigidemasi/camel-kit/compare/camel-kit-0.2.0...camel-kit-0.3.0
[0.2.0]: https://github.com/luigidemasi/camel-kit/tree/camel-kit-0.2.0
