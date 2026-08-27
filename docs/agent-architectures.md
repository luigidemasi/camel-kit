# Agent Architectures

> Per-agent deep dive into dispatch models, tool restriction systems, template files, and unique capabilities. For the shared equalization layer (skills, iron laws, MCP), see [Architecture](architecture.md#5-agent-templates).

---

## Three-Layer Composition Model

Camel-Kit uses a three-layer composition model to separate concerns between user interaction, workflow orchestration, and domain expertise:

| Layer | What it is | Examples |
|-------|-----------|---------|
| **Skill** | Workflow with steps and exit criteria | `camel-brainstorm`, `camel-execute`, `camel-debug` |
| **Persona** | Role with perspective, output format, and composition rules | `integration-architect`, `catalog-researcher`, `code-quality-reviewer` |
| **Command** | User-facing entry point | `/camel-brainstorm`, `/camel-ship`, `/camel-validate` |

### Composition Rules

1. **Personas do not invoke other personas.** Composition is the job of skills or the user.
2. Every persona has a `## Composition` block stating: invoke directly when / invoked via / do not invoke from another persona.
3. **Max depth = 1:** command → persona (no deep persona trees).

### Subagent Dispatch Patterns

| Pattern | Purpose | Example |
|---------|---------|---------|
| **Direct dispatch** | One persona, one task, structured output | Implementer generates route YAML |
| **Parallel fan-out with merge** | Multiple independent reviewers, merged reports | Adversarial review critic lanes |
| **Research isolation** | Batch lookups, return summary only | `catalog-researcher` verifies 8 components, returns 100-token summary |
| **Adversarial Code Review** | Parallel Critic Lanes review implementation against the design spec | Moderator + Critic Lanes with 3-cycle cap |

### Context Savings

Each subagent has its own context window. Only structured summaries flow back to the orchestrator:
- **Research isolation** prevents ~500 tokens per MCP call from accumulating (8 components = ~4,000 tokens saved per task)
- **Review isolation** prevents review traces (file reads, reasoning, MCP spot-checks) from accumulating (~2,000-5,000 tokens per review)
- **Verification isolation** prevents build output and fix cycles from accumulating (~5,000-10,000 tokens)
- For a typical 5-task plan with adversarial and two-stage review per task, subagent isolation prevents ~60-70% of pipeline tokens from accumulating in the main conversation

---

## Design Philosophy

Each agent uses a different architecture designed to **maximize that agent's native capabilities** -- not lowest-common-denominator parity. The equalization layer aligns workflow and output contracts, while the template layer exploits each agent's strongest features for dispatch, tool restriction, and configuration. Most targets consume the shared skills directly; Bob 1 legacy installs seven self-contained monolithic gate variants.

**What equalization covers:**
- Workflow content (shared `SKILL.md` and guides for most targets; corresponding monolithic gates for Bob 1)
- Six Iron Laws (Bob 1 uses a same-session sequential adversarial fallback because fresh parallel critic contexts are unavailable)
- Constitution rules (enforced identically)
- MCP tool calls (same tools, same parameters)
- Output formats (same YAML routes, properties, test files)

**What equalization does NOT cover (handled by traits and templates):**
- Dispatch mechanism (subagents vs. modes vs. inline)
- Tool restriction model (each agent's permission system is different)
- File reading patterns (context isolation varies)
- Parallelization strategy (Claude and Bob 2 support parallel implementation-wave dispatch; other agents vary)
- Configuration format (YAML modes, TOML policies, markdown frontmatter)

**Agent traits** bridge the gap: they append agent-specific instructions during `camel-kit init`. For targets that consume shared phase skills, Claude's trait adds `Agent` tool parallel dispatch, Bob 2 adds `spawn_subagent` orchestration, Gemini adds named agent delegation, and OpenCode adds step-limited subagents. Bob 1's traits append `switch_mode` orchestration to its gate-backed phase files. See [Architecture Guide](architecture.md#agent-traits) for details.

---

## Claude Code -- Parallel Subagent Dispatch

### Dispatch Model

Claude's `Agent` tool dispatches subagents with isolated context windows. Each subagent receives the skill content and executes independently. During `/camel-execute`, independent tasks are dispatched to multiple subagents simultaneously, using `camel-kit plan analyze` waves from structured task metadata, logical dependencies, and file overlap.

### Template Files

| File | Purpose |
|------|---------|
| `templates/claude/claude-md.md` | `CLAUDE.md` -- project rules (iron laws, Camel version, command prefix, MCP setup) |
| `templates/claude/settings.json` | Claude Code project settings |

### How It Works

```
User: /camel-execute
  └── Claude reads plan, identifies independent tasks
      ├── Agent tool → subagent 1 (flow A implementation)
      ├── Agent tool → subagent 2 (flow B implementation)  ← parallel
      └── Agent tool → subagent 3 (flow C implementation)  ← parallel
          └── Each subagent: reads SKILL.md → follows guides → generates artifacts
```

### Tool Restriction Model

Claude has no formal permission system. It relies on skill instructions to constrain agent behavior -- skills say "do NOT generate code" during brainstorm, and the agent follows these instructions. The trade-off: simpler configuration, but no hard enforcement at the platform level.

### Unique Capabilities

- **Parallel dispatch:** only agent that can run multiple pipeline tasks simultaneously
- **Route graph topology:** uses `camel-kit graph` to determine route independence for parallelization decisions
- **Context isolation:** each subagent gets a fresh context window -- no cross-contamination between phases
- **Research isolation:** `catalog-researcher` and `knowledge-researcher` subagents batch MCP lookups and return only summaries
- **Parallel reviewer fan-out:** adversarial review dispatches independent critic lanes simultaneously
- **Adversarial Code Review:** parallel Critic Lanes (via Moderator subagent) adversarially review implementation before two-stage review
- **Simplest configuration:** 3 template files total (fewest of any agent)

---

## IBM Bob 1 Legacy -- B+A Hybrid with Custom Modes

### Dispatch Model

The `--ai bob` target is the Bob 1 legacy path. It uses a "Behavior + Advanced" hybrid. Skills load in **Advanced mode** (unrestricted), then the first step in each gate file switches to a **restricted custom mode**. This two-phase approach gives initial access to load all skill files, then constrains behavior for the actual work.

### Template Files

| File | Purpose |
|------|---------|
| `templates/bob/custom_modes.yaml` | 7 custom modes with scoped tool groups |
| `templates/bob/gates/*.md` | 7 monolithic gate files (one per replaced skill) |
| `templates/bob/rules/iron-laws.md` | Shared iron laws loaded across all modes |
| `templates/bob/rules-camel-{phase}/*.md` | Per-mode custom rules |

Bob 1 has the most template files (17+) because it cannot chain skill references -- each gate file must be self-contained and inline the complete orchestration logic for its skill.

### How It Works

```
User: /camel-start
  └── Bob loads the shared camel-start router
      └── Routes to migrate / plan / execute / validate / debug / brainstorm
          └── The selected Bob gate switches to its restricted custom mode
              └── Follows gate instructions with phase-scoped tools
```

### Custom Modes and Tool Groups

| Mode | Tools Allowed | Purpose |
|------|---------------|---------|
| `camel-brainstorm-mode` | read, design-markdown/config edit, command, mcp, browser | Design interview; commands are limited by instructions to pipeline metadata and read-only graph queries |
| `camel-plan-mode` | read, edit (`.md` only), command, mcp | Planning from approved spec; commands manage document metadata/staleness |
| `camel-implement-mode` | read, edit, command, mcp | Route implementation |
| `camel-execute-mode` | read, edit, command, mcp | Orchestration and ordered reviews |
| `camel-validate-mode` | read, command, report-only edit, mcp | Static quality report |
| `camel-debug-mode` | read, edit, command, mcp | Standalone diagnosis and repair |
| `camel-test-mode` | read, edit, command, mcp | Test generation and execution |

### Checkpoint Types

Bob supports three checkpoint types used in gate files:

| Type | Behavior | Use Case |
|------|----------|----------|
| **Hard gate** | Blocks until condition is met | User must approve spec before planning begins |
| **Soft gate** | Warns but allows proceeding | Constitution violation detected but non-critical |
| **Review point** | Summarize and wait for approval | End of brainstorm -- present design for review |

### Unique Capabilities

- **Three checkpoint types** for fine-grained pipeline flow control
- **Custom rules per mode:** each mode loads additional rules files (e.g., `interview-gates.md` enforces one-question-at-a-time during brainstorm)
- **Monolithic gate files:** complete phase logic in a single file -- most self-contained of any agent
- **Path-scoped edits:** brainstorm can edit design Markdown plus `.camel-kit/config.properties`, `.camel-kit/pipeline.json`, and `.camel-kit/project-snapshot.md`; plan is Markdown-scoped, test is limited to test resources/reports, and validate can write only its selected report path

---

## IBM Bob 2 -- Native Subagents with Bob Modes

### Dispatch Model

The `--ai bob2` target uses Bob 2 native `spawn_subagent` while still generating files under `.bob/`. The target name is only the Camel-Kit selector; Camel-Kit installs commands, skills, capability-scoped agent presets, role personas, modes, rules, and MCP configuration under `.bob/`.

Bob 2 keeps the shared Camel-Kit skills and appends Bob 2 traits. It does not replace `SKILL.md` files with monolithic gates.

### Template Files

| File | Purpose |
|------|---------|
| `templates/bob2/custom_modes.yaml` | Bob 2 custom modes using `read`, `edit`, `execute`, `mcp`, `skill`, `todo`, `artifact`, `subagent`, and `mode` groups |
| `templates/bob2/agents/*.md` | Project presets for writable workers and tool-enforced read/MCP-only reviewers |
| `agents/*.md` → `.bob/personas/*.md` | Full catalog, implementation, Moderator, critic, spec, quality, and supporting role contracts supplied to the scoped presets |
| `templates/bob2/rules*/` | Lightweight project and mode rules |
| `templates/traits/bob2/*.append.md` | Native `spawn_subagent` orchestration guidance |
| `templates/dispatch/bob2.md` | Shared dispatch block naming `spawn_subagent`, `explore`, `camel-worker`, `camel-reviewer`, and `fork_context` |

### How It Works

```
User: /camel-execute
  └── Parent Bob task loads shared camel-execute skill
      ├── camel-kit plan analyze groups independent waves
      ├── spawn_subagent name="camel-worker" for implementation/test/fix/verification
      ├── spawn_subagent name="camel-reviewer" for research, ACR, spec, quality, and validation judgment
      ├── spawn_subagent name="explore" only for factual source search and inventory
      └── Parent merges summaries and keeps orchestration state
```

Multiple `spawn_subagent` calls in one parent turn run in parallel. Subagents return summaries and must not spawn subagents; the parent Bob task owns orchestration and follow-up dispatch.

Because Bob subagents cannot spawn children, the parent coordinates separate fresh calls for Moderator Phase 1 lane
selection, the selected critic lanes, and Moderator Phase 2 synthesis, then dispatches spec review through
`camel-reviewer`. The Catalog Researcher, Knowledge Researcher, and every review lane use that generated read/MCP-only preset, so Bob enforces
non-mutation while still exposing live catalog tools. Implementation, testing, fixes, and verification use
`camel-worker` from the broad execute or debug orchestration modes. Standalone implement and test modes keep mutations
inline instead of dispatching the broader worker; test retains its path-scoped edit restriction. The built-in
`explore` preset is reserved for factual source discovery because its raw prompt is not a review persona.
Before each worker or reviewer call, the parent loads the complete applicable role from `.bob/personas/` and includes it
in the prompt; those persona files are deliberately outside `.bob/agents/`, so Bob does not register them as accidental
broad-capability presets.

### Unique Capabilities

- **Native isolated subagents:** `explore` for factual discovery, `camel-worker` for edit/execute work, and
  `camel-reviewer` for tool-enforced read/MCP research and judgment
- **Parallel same-turn dispatch:** independent tasks in the current wave can be spawned together
- **Mode restrictions plus shared skills:** Bob 2 modes constrain tools while shared Camel-Kit skills define behavior
- **Restricted inline mutation:** standalone implement and test modes mutate in the parent mode rather than delegating
  to a broader worker preset; test retains its path-scoped edit boundary
- **Bob-readable metadata:** command stubs use markdown frontmatter and skills include `user-invocable`

---

## Gemini CLI -- Parallel Scheduler + Policy Engine + Modular Imports

### Dispatch Model

Gemini exposes a single unified `invoke_subagent` tool (`AgentTool` class) that dispatches to subagents by name. Three invocation types are supported: **Local** (in-process context loop), **Remote** (A2A protocol), and **Browser** (headless automation). Users can also invoke via `@subagent_name` syntax.

The `Scheduler` class implements **native parallel tool execution** -- all tool calls within a turn are assessed for parallelizability and batched via `Promise.all()` by default. Tools opt-out of parallelism via `wait_for_previous: true`.

However, subagents **cannot invoke other subagents** -- `Kind.Agent` tools are hardcoded-filtered during registry creation. This means `/camel-execute` runs in the **main agent context** so it can dispatch to all 6 subagents for orchestration.

### Template Files

| File | Purpose |
|------|---------|
| `templates/gemini/gemini-md.md` | `GEMINI.md` -- project root with `@file.md` imports |
| `templates/gemini/instructions/*.md` | 3 imported files: `iron-laws.md`, `mcp-usage.md`, `pipeline-overview.md` |
| `templates/gemini/policies/camel-kit.toml` | TOML policy rules (MCP auto-allow, destructive command deny) |
| `templates/gemini/agents/*.md` | 6 subagent definitions (no executor -- runs in main agent) |
| `templates/gemini/geminiignore` | Excludes build output from agent context |

### How It Works

```
# GEMINI.md (generated)
@.gemini/instructions/iron-laws.md       ← modular import
@.gemini/instructions/mcp-usage.md       ← modular import
@.gemini/instructions/pipeline-overview.md

User: /camel:validate
  └── Gemini dispatches to camel-validator subagent
      └── Subagent reads SKILL.md → follows guides
          └── Tool access governed by subagent tools array + TOML policy

User: /camel:execute
  └── Runs in MAIN agent context (not a subagent)
      └── Main agent reads plan, dispatches tasks to subagents
          ├── → camel-implementer subagent (flow A)
          ├── → camel-validator subagent (quality check)
          └── → camel-tester subagent (test generation)
```

### Policy Engine (TOML)

Policies handle cross-cutting concerns that apply globally (main agent AND all subagents). Subagent tool arrays handle per-phase restrictions.

```toml
[[rules]]
name = "Allow Camel MCP tools"
toolName = "mcp_camel_*"          # server-scoped wildcard
decision = "allow"
priority = 3                      # workspace level

[[rules]]
name = "Allow Knowledge MCP tools"
toolName = "mcp_camel-knowledge_*"
decision = "allow"
priority = 3

[[rules]]
name = "Allow Citrus MCP tools"
toolName = "mcp_citrus_*"
decision = "allow"
priority = 3

[[rules]]
name = "Allow Maven commands"
toolName = "run_shell_command"
commandRegex = "^(\\./mvnw|mvn)\\s+"
decision = "allow"
priority = 3

[[rules]]
name = "Deny destructive shell commands"
toolName = "run_shell_command"
commandRegex = "rm\\s+-rf"
decision = "deny"
priority = 4                      # higher priority overrides
```

Priority levels: user policies (priority 4) override workspace policies (priority 3), so users can customize without editing the generated file.

### Subagent Tool Wildcards

Gemini subagents use server-scoped MCP wildcards -- no other agent supports this:

```yaml
name: camel-validator
tools:
  - read_file
  - write_file
  - replace
  - glob
  - grep_search
  - run_shell_command
  - mcp_camel_*           # all tools from Camel catalog MCP server
max_turns: 20
timeout_mins: 20
```

Server-scoped wildcards automatically include new tools when a configured MCP server adds them. Different subagents can have different MCP server access (e.g., validator gets catalog while tester gets catalog plus Citrus).

### Path-Scoped Edits via Policy Engine

The Policy Engine supports per-subagent tool restrictions based on regex matching against serialized tool arguments:

```toml
[[rules]]
toolName = "edit_file"
subagent = "frontend-specialist"
argsPattern = "\"file_path\":\"src/frontend/"
decision = "allow"
priority = 600

[[rules]]
toolName = "edit_file"
subagent = "frontend-specialist"
decision = "deny"
priority = 500
```

This restricts the `frontend-specialist` to only edit files under `src/frontend/`.

### Unique Capabilities

- **Default-parallel scheduler:** `Promise.all()` batches all parallelizable tool calls within a turn -- only agent with native scheduler-level parallelism
- **`@file.md` modular imports:** only agent that supports composing instructions from multiple files -- each concern is independently editable
- **Server-scoped MCP wildcards:** `mcp_camel_*` grants all tools from a specific server, auto-discovers new tools
- **TOML policy engine with priority tiers:** workspace policies can be overridden by user policies, per-subagent `argsPattern` targeting
- **Three invocation kinds:** Local (in-process), Remote (A2A protocol), Browser (headless)
- **Execution limits per subagent:** `max_turns` and `timeout_mins` prevent runaway execution
- **Execute in main agent:** the only agent where `/camel-execute` runs outside a subagent (platform constraint turned into a feature -- main agent has full delegation ability)

---

## Qwen -- Primary Orchestration with Bounded Leaves

### Dispatch Model

Qwen keeps user-invoked workflows in the **primary session** so interviews, design approval, command arguments, and
automatic phase handoffs remain available. The canonical lowercase `agent` tool is used only for bounded work:

1. **Generated leaf agents** -- `camel-implementer`, `camel-reviewer`, `camel-tester`, and `camel-validator` provide
   clean contexts with role-specific tools. Gating calls set `run_in_background: false`.
2. **Fork** -- explicit `subagent_type: "fork"` inherits all or a bounded number of parent turns and always runs
   detached. A fork cannot dispatch any further subagent.

Omitting `subagent_type` launches the regular `general-purpose` agent; it does not fork. The fork mechanism shares the
parent's system prompt and tool declarations for prompt-cache reuse, while `fork_tools` can narrow execution without
changing the model-visible declarations.

### Template Files

| File | Purpose |
|------|---------|
| `QwenGenerator` | Generates primary-session commands, bounded leaves, retired-profile cleanup, and the persona library |
| `templates/qwen/qwen-md.md` | `QWEN.md` -- project root workflow and leaf routing |
| `templates/qwen/agents/*.md` | 4 bounded leaf definitions with tool allowlists or disallowlists |
| `templates/qwen/qwenignore` | Excludes build output |
| `templates/dispatch/qwen.md` and `templates/traits/qwen/` | Primary-session and leaf-dispatch rules |
| `templates/mcp-configs/qwen-mcp.json` | `.qwen/settings.json` with runtime-supported `includeTools` filters |

### How It Works

```text
/camel-brainstorm <request>
  -> primary session reads .qwen/skills/camel-brainstorm/SKILL.md
  -> primary session owns ordered questions and design approval
  -> optional top-level fork performs bounded factual research
  -> primary session hands the approved design to /camel-plan
```

Every generated non-Ship command includes Qwen's `{{args}}` placeholder. Internal skill copies also receive Qwen's
hyphenated `user-invocable` metadata, so guide-only skills do not become accidental slash commands.

### Tool Whitelist Profiles

| Leaf | Purpose | Tools Included | Tools Excluded |
|---------|--------|----------------|----------------|
| `camel-implementer` | One implementation or fix task | Inherited implementation tools and MCP access | `agent` explicitly disallowed |
| `camel-reviewer` | Catalog research and adversarial/spec/quality roles | Read/search and exact Camel/Knowledge MCP tools | No write, shell, user-question, or agent tool |
| `camel-tester` | One isolated test task | Read/write/shell plus exact Camel/Citrus MCP test tools | `agent` is not allowlisted |
| `camel-validator` | One report-producing validation task | Read/search/shell/MCP plus report writes | `agent` is not allowlisted |

All leaves set `approvalMode: default`; the plan handoff explicitly asks the user to select and confirm either
`auto-edit` or `default` before execution.

### Explicit Context

Generated agent definitions contain no assumed runtime placeholders. Regular-agent calls receive exact project paths,
task context, and complete roles from `.qwen/camel-kit-personas/`; optional forks inherit only the bounded parent turns
selected by `fork_turns`.

### Parallel Execution

Qwen implements a **batch-based concurrency model** in the `CoreToolScheduler`. Independent `agent` calls emitted in
one turn can run concurrently, so the primary executor emits one foreground named-agent call per independent task in a plan
wave and waits for all results before starting a dependent wave.

Explicit forks provide additional detached parallelism for non-gating factual discovery that needs parent context. They
set `run_in_background: true` when their later result will be consumed, then deliver it through a completion notification.
The generated workflow never assigns implementation or a
review gate to a fork and never asks a fork to dispatch another agent.

### Unique Capabilities

- **Explicit dual dispatch:** named subagents for clean-context tasks; `subagent_type: "fork"` only for detached work with parent context
- **DashScope prompt caching:** fork model shares parent's exact system prompt prefix, saving 80%+ tokens across concurrent forks
- **Interactive primary workflow:** questions, approval, arguments, and phase handoffs stay in the user-facing session
- **Explicit context passing:** named-agent prompts carry project paths and contracts; forks use bounded inherited turns
- **Allowlist + blocklist:** `tools` and `disallowedTools` for flexible per-subagent tool control
- **Explicit leaf approval mode:** generated leaves use `default`; the plan handoff controls primary edit approval
- **Per-call background control:** gating named agents set `run_in_background: false`; result-bearing forks set it to `true`

---

## OpenCode -- Granular Permission System + Opt-In Delegation

### Dispatch Model

OpenCode dispatches via the `task` tool, which creates a **child session** with `parentID` and derived permissions. Its
documented permission keys support glob patterns with last-match-wins evaluation, enabling path-scoped file edits,
command-level bash control, and per-agent execution limits.

The `/camel-execute` command selects the generated primary executor in the current session. That executor can create an
allowlisted implementer, migrator, planner, researcher, reviewer, or tester child. Every leaf denies `task`, so the generated topology is
one level deep. Brainstorm, plan, migrate, and validate commands remain primary-session skill stubs so they retain user
questions, arguments, and phase handoffs. Users can also invoke bounded subagents via `@agent-name` syntax.

**Permission inheritance:** Parent agent deny rules are forwarded to child sessions via `deriveSubagentSessionPermission()`. Known gap: permissions are not fully transitive — a restricted parent can invoke a subagent with broader permissions.

### Template Files

| File | Purpose |
|------|---------|
| `templates/shared/agents-md.md` | Shared `AGENTS.md` bootstrap rendered by `AgentsMdGenerator` |
| `templates/opencode/agents/*.md` | 9 agent definitions with per-type permissions and agent-owned step limits |
| `templates/mcp-configs/opencode-mcp.json` | Supported MCP server fields plus top-level `ask` patterns for all three server tool namespaces |

Camel-Kit generates `AGENTS.md` through the shared generator flow. Its compact bootstrap points to `/camel-start`, which loads the full context through progressive skill loading.

No `.opencodeignore` -- OpenCode uses `.gitignore` for file exclusion (simpler than Qwen/Gemini).

### How It Works

```yaml
# Tester -- path-scoped edits
name: tester
mode: subagent
permission:
  edit:
    "*": ask               # ask before editing source files
    "src/test/**": allow   # auto-allow test file edits
    "test/**": allow
  bash:
    "*": allow
    "rm -rf *": deny       # safety net
  task: deny
steps: 40

# Primary executor -- full access with leaf delegation
name: executor
mode: primary
permission:
  edit: allow
  bash:
    "*": allow
  task:
    "*": deny
    implementer: allow
    planner: allow
    researcher: allow
    reviewer: allow
steps: 100
```

### Permission Tiers

| Tier | Agents | Edit | Bash | Task | Steps |
|------|--------|------|------|------|-------|
| **Phase-scoped write** | brainstormer, validator, planner | design, plan, or report paths only; deny everything else | `"*": ask`, safe commands allowed | `deny` | 20-200 |
| **Test-write** | tester | path-scoped (test dirs auto-allowed) | `"*": allow`, `"rm -rf *": deny` | `deny` | 40 |
| **Full access** | implementer, migrator | `allow` | `"*": allow`, `"rm -rf *": deny` | `deny` | 50 |
| **Read-only leaves** | researcher, reviewer | `deny` | `deny` | `deny` | 30-50 |
| **Orchestrator** | executor | `allow` | `"*": allow` | explicit six-agent allowlist | 100 |

### Steps Limits

Each agent has a `steps` limit. When reached, OpenCode instructs the agent to summarize completed work and list remaining tasks -- graceful degradation rather than hard failure.

| Agent | Steps | Rationale |
|-------|-------|-----------|
| brainstormer | 200 | Bounded non-interactive discovery and design analysis |
| planner | 30 | Bounded plan creation or architectural re-planning |
| validator | 20 | Static analysis and its required report should complete quickly |
| tester | 40 | TDD cycle (write test, run, fix, run again) needs more iterations |
| implementer | 50 | Route implementation with MCP lookups, file creation, smoke tests |
| migrator | 50 | Bounded migration analysis or implementation |
| executor | 100 | Orchestrating multiple tasks via sub-agent dispatch |
| researcher | 30 | Focused read-only catalog, knowledge, or source research |
| reviewer | 50 | Read-only Moderator, critic, specification, or quality review |

### Unique Capabilities

- **Permission keys with glob patterns:** granular tool control without a brittle hard-coded count
- **Path-scoped edits:** tester can auto-edit `src/test/**` but must ask before touching source files
- **Command-level bash control:** validator can run `mvn validate` but not `rm -rf`
- **`steps` limit per agent:** prevents runaway execution with graceful summarization
- **`doom_loop` detection:** catches agents stuck in repetitive tool call patterns (inherited default behavior)
- **Last-match-wins evaluation:** glob patterns are order-sensitive, allowing fine-grained overrides
- **Leaf-bounded delegation:** the primary executor has a six-agent task allowlist and every child denies `task`
- **MCP permission prompts:** supported top-level namespace patterns set Camel, Knowledge, and Citrus tool calls to `ask`
- **LLM-level parallel tool calls:** multiple tool calls in a single LLM response execute concurrently
- **Mixed-provider model support:** agents can use different model providers (e.g., `anthropic/claude-opus-4-6`, `openai/gpt-4o`)

---

## GitHub Copilot CLI -- Project Skills + Custom Agents + Hooks

### Dispatch Model

GitHub Copilot CLI uses repository-native customization surfaces rather than Camel-Kit slash commands:

- `.github/copilot-instructions.md` for project instructions.
- `.github/skills/` for project skills.
- `.github/agents/*.agent.md` for custom agents.
- `.github/mcp.json` for workspace MCP servers.
- `.github/hooks/*.json` for repository safety hooks.

Users start by asking Copilot to "Use the `/camel-start` skill." Run `/skills list` to inspect available project skills. Pipeline skills can then delegate implementation, validation, testing, migration, catalog research, and security review to the generated custom agents.

### Template Files

| File | Purpose |
|------|---------|
| `templates/copilot/copilot-instructions.md` | `.github/copilot-instructions.md` -- project entry point, laws, MCP guidance, and safety policy |
| `templates/copilot/agents/*.agent.md` | 7 custom agents with Copilot tool aliases and MCP server prefixes |
| `templates/copilot/hooks/camel-kit-safety.json` | `preToolUse` hook that denies destructive or secret-sensitive shell commands |
| `templates/copilot/agents-md.md` | `AGENTS.md` bridge for clients that also read root agent instructions |
| `templates/mcp-configs/copilot-mcp.json` | `.github/mcp.json` using Copilot's `tools` schema |
| `templates/dispatch/copilot.md` | Shared dispatch block for Copilot skill copies |

### How It Works

```text
User: "Use the /camel-start skill to design this integration"
  └── Copilot loads .github/copilot-instructions.md
      ├── Skill retrieval selects .github/skills/camel-start/SKILL.md
      ├── Pipeline skills use .github/agents/* for isolated work
      ├── MCP tools come from .github/mcp.json after the repository folder is trusted
      └── .github/hooks/camel-kit-safety.json denies obvious destructive shell commands
```

### Tool Restriction Model

Copilot custom agents use the documented `tools` aliases: `read`, `search`, `edit`, `execute`, `agent`, and `web`. MCP tools are exposed through server prefixes such as `camel/*`, `camel-knowledge/*`, and `citrus/*`.

The generated safety hook is deliberately narrow. It denies `git push`, broad `rm -rf`, `chmod 777`, and reads of common secret files. It returns no decision for normal commands, so Copilot's regular permission prompts and any user or organization policy still apply.

### Unique Capabilities

- **GitHub-native project skills:** Camel Kit skills live where Copilot CLI discovers project skills by default.
- **Custom agents:** planner, implementer, tester, validator, migrator, catalog researcher, and security reviewer map directly to Camel Kit pipeline roles.
- **Workspace MCP config:** `.github/mcp.json` is version-controlled with the project and uses Copilot's `tools` allowlist schema.
- **Repository hooks:** guardrails are committed with the project and can be disabled locally through Copilot settings when needed.
- **Cloud/local sandbox compatibility:** generated instructions recommend sandboxed execution for high-autonomy runs and avoid `--yolo` defaults.

---

## OpenAI Codex CLI -- Project Skills + Native Custom Agents

### Dispatch Model

Codex reads repository instructions from `AGENTS.md`, discovers project skills under `.agents/skills/`, and loads
custom roles from `.codex/agents/*.toml`. Camel-Kit generates planner, implementer, tester, validator, migrator,
catalog researcher, and security reviewer roles. The parent session remains the orchestrator, dispatches independent
tasks from the same implementation wave in parallel, and keeps dependent waves sequential. Delegated agents return
results to the parent and do not spawn another layer. If delegation is unavailable, the skill runs inline.

Codex does not use generated slash-command files, so Camel-Kit does not create `.codex/commands/`. Project config and
any user-added project hooks are skipped until repository trust; Camel-Kit itself generates no hooks. After trusting
the repository, users start with `$camel-start`, inspect skills with `/skills`, and inspect MCP servers with `/mcp`.

### Template Files

| File | Purpose |
|------|---------|
| `templates/codex/agents-md.md` | `AGENTS.md` -- entry point, laws, trust, sandbox, and approval guidance |
| `templates/codex/agents/*.toml` | 7 custom-agent role definitions |
| `templates/mcp-configs/codex-mcp.toml` | Three project MCP servers in `.codex/config.toml` |
| `templates/dispatch/codex.md` | Parent-owned dispatch, parallel-wave, and inline-fallback guidance |

### How It Works

```text
User: $camel-start
  └── Codex loads AGENTS.md and .agents/skills/camel-start/SKILL.md
      ├── Parent selects a generated .codex/agents role for isolated work
      ├── Independent tasks in one plan wave may run in parallel
      ├── Child roles return concise evidence to the parent orchestrator
      └── MCP tools come from .codex/config.toml after repository trust
```

### Tool Restriction Model

Camel-Kit leaves the user's Codex sandbox and approval policy in force. The catalog researcher and security reviewer
declare `sandbox_mode = "read-only"`; other roles inherit the active policy. Each generated MCP server uses an exact
`enabled_tools` list and `default_tools_approval_mode = "prompt"`. Camel-Kit writes only repository-scoped files: it
does not change global Codex configuration or authentication and does not generate executable hooks.

When `.codex/config.toml` already exists, init preserves unrelated valid settings and replaces only Camel-Kit's
marked MCP block. Invalid TOML or conflicting managed server tables fail clearly without changing the existing file.

### Unique Capabilities

- **Native repository skills:** all shared Camel-Kit skills use Codex's `.agents/skills/` discovery path.
- **Seven custom roles:** pipeline work maps to explicit Codex custom-agent definitions.
- **Parent-owned parallel dispatch:** independent implementation-wave tasks can run together without recursive delegation.
- **Trust-gated project config:** MCP configuration loads only for a trusted repository.
- **Least-privilege MCP:** exact allowlists and per-call prompt approval for Camel, knowledge, and Citrus tools.
- **Safe config composition:** managed-block replacement is idempotent and preserves unrelated project settings.

---

## Pi -- Native Skills + Prompt Templates + Guard Extension

### Dispatch Model

Pi reads `AGENTS.md` and native project skills from `.pi/skills/`. Camel-Kit also writes command stubs to `.pi/prompts/`
so users can launch prompt templates, but the primary entry remains `/skill:camel-start`.

Pi has no native subagents. `/camel-execute` keeps orchestration in the main Pi session. For isolated read-only
research, users can launch separate Pi sessions manually with tool constraints such as `pi --tools read,grep,find,ls`
and bring the findings back to the main task.

### Template Files

| File | Purpose |
|------|---------|
| `templates/pi/agents-md.md` | `AGENTS.md` -- Pi entry point, trust guidance, laws, and adapter setup |
| `templates/pi/extensions/camel-kit-guard.ts` | Static `tool_call` guard extension |
| `templates/pi/extensions/camel-kit-guard-policy.json` | Declarative policy interpreted by the guard extension |
| `templates/mcp-configs/pi-mcp.json` | `.mcp.json` for `pi-mcp-adapter` using `directTools` |
| `templates/dispatch/pi.md` | Shared dispatch block for Pi skill copies |

### How It Works

```text
User: /trust
User: /skill:camel-start
  └── Pi loads AGENTS.md after project trust
      ├── Skill retrieval selects .pi/skills/camel-start/SKILL.md
      ├── Prompt templates are available from .pi/prompts/
      ├── MCP tools come from .mcp.json through pi-mcp-adapter
      └── .pi/extensions/camel-kit-guard.ts blocks policy-matched tool calls
```

### Tool Restriction Model

Pi has no built-in permission prompt model. Camel-Kit provides a project guard extension that blocks obvious
destructive or secret-sensitive tool calls and keeps the policy in JSON. Broader sandboxing remains an external
container or VM concern.

### Unique Capabilities

- **Native skills:** Camel Kit skills use Pi's `.pi/skills/` discovery path.
- **Trust-gated project resources:** post-init guidance and doctor checks use `pi -a` for headless validation.
- **Adapter-backed MCP:** `.mcp.json` uses `pi-mcp-adapter` and `directTools` allowlists.
- **Static guard extension:** generated policy is declarative JSON; executable TypeScript stays fixed.

---

## Agent Comparison

| Aspect | Claude | Bob 1 legacy | Bob 2 | Gemini | Codex | Copilot | Pi | Qwen | OpenCode |
|--------|--------|--------------|-------|--------|-------|---------|----|------|----------|
| Dispatch model | Parallel subagents | Mode switching | `spawn_subagent` (`explore`, `camel-worker`, `camel-reviewer`) | `invoke_subagent` unified tool (local/remote/browser) | Project skills + custom agents | Project skills + custom agents | Project skills + prompt templates | Primary workflow + bounded named leaves/forks | `task` tool creating child sessions |
| Template files | 3 | 17+ | Modes + scoped agents/personas + traits + rules + dispatch | 12 | 10 | 11 | 5 | Generator + 4 leaves + traits/dispatch | 9 agent definitions + traits |
| Tool restriction | Instruction-based | Mode tool groups | Mode tool groups + `allowedSubagents` | Allowlist + TOML policy + server-scoped wildcards | Inherited sandbox/approvals + read-only research roles | Custom-agent `tools` plus hooks | Guard extension + external sandbox | Allowlist + blocklist | 3-state permissions + bash glob patterns |
| Path-scoped edits | No | Phase-specific `fileRegex` (design Markdown/config, test resources, validation reports) | Mode-dependent `fileRegex` | Yes (Policy Engine) | No | Tool-level, not path-scoped | No | No | Yes (glob patterns) |
| MCP auto-approval | No (manual) | No (manual) | No (manual) | Yes (TOML policy) | No (`prompt`) | No (permission prompts) | Adapter `directTools` | No (`includeTools`, approval prompts) | No (permission prompts) |
| Parallel execution | Yes (graph-based) | No | Yes (same-turn `spawn_subagent`) | Yes (scheduler `Promise.all()`) | Yes (independent waves) | Unknown | No native subagents | Yes (same-turn agents; detached forks) | Partial (LLM-level parallel tool calls) |
| Subagent recursion | Yes (no limit) | N/A | No (subagents must not spawn subagents) | No (hardcoded `Kind.Agent` filter) | No (parent-owned) | Unknown | N/A | Generated leaves and forks cannot dispatch | Primary executor to task-denying leaves |
| Execute phase | Subagent with parallel dispatch | Gate file with mode switch | Parent task orchestrates subagents | Main agent (recursion prevention) | Parent dispatches custom roles with inline fallback | Project skill delegates when available | Main Pi session | Primary session dispatches bounded leaves | Executor dispatches allowlisted implementation/research/review leaves |
| Instruction composition | Single `CLAUDE.md` | Modes + gates + rules | Shared skills + Bob 2 traits + modes | `@file.md` modular imports | `AGENTS.md` + `.agents/skills` | `.github/copilot-instructions.md` + project skills | `AGENTS.md` + `.pi/skills` | Single `QWEN.md` | Ultra-minimal `AGENTS.md` |
