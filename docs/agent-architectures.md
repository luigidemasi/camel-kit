# Agent Architectures

> Per-agent deep dive into dispatch models, tool restriction systems, template files, and unique capabilities. For the shared equalization layer (skills, iron laws, MCP), see [Architecture](architecture.md#5-agent-templates).

---

## Three-Layer Composition Model

Camel-Kit uses a three-layer composition model to separate concerns between user interaction, workflow orchestration, and domain expertise:

| Layer | What it is | Examples |
|-------|-----------|---------|
| **Skill** | Workflow with steps and exit criteria | `camel-brainstorm`, `camel-execute`, `camel-ship` |
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
| **Parallel fan-out with merge** | Multiple independent reviewers, merged reports | Stamp Gate: spec + quality + security in parallel |
| **Research isolation** | Batch lookups, return summary only | `catalog-researcher` verifies 8 components, returns 100-token summary |
| **Adversarial Code Review** | Parallel Critic Lanes review implementation against the design spec | Moderator + Critic Lanes with 3-cycle cap |

### Context Savings

Each subagent has its own context window. Only structured summaries flow back to the orchestrator:
- **Research isolation** prevents ~500 tokens per MCP call from accumulating (8 components = ~4,000 tokens saved per task)
- **Review isolation** prevents review traces (file reads, reasoning, MCP spot-checks) from accumulating (~2,000-5,000 tokens per review)
- **Verification isolation** prevents build output and fix cycles from accumulating (~5,000-10,000 tokens)
- For a typical 5-task plan with 2-stage review per task, subagent isolation prevents ~60-70% of pipeline tokens from accumulating in the main conversation

---

## Design Philosophy

Each agent uses a different architecture designed to **maximize that agent's native capabilities** -- not lowest-common-denominator parity. The equalization layer ensures identical pipeline behavior (same skills, same output), while the template layer exploits each agent's strongest features for dispatch, tool restriction, and configuration.

**What equalization covers:**
- Skill content (all agents read the same `SKILL.md` and guide files)
- Iron Laws (embedded in every agent's instruction file)
- Constitution rules (enforced identically)
- MCP tool calls (same tools, same parameters)
- Output formats (same YAML routes, properties, test files)

**What equalization does NOT cover (handled by traits and templates):**
- Dispatch mechanism (subagents vs. modes vs. inline)
- Tool restriction model (each agent's permission system is different)
- File reading patterns (context isolation varies)
- Parallelization strategy (Claude and Bob 2 support parallel implementation-wave dispatch; other agents vary)
- Configuration format (YAML modes, TOML policies, markdown frontmatter)

**Agent traits** bridge the gap: they append agent-specific instructions to shared skill files during `camel-kit init`. For example, all agents share the same `camel-execute/SKILL.md`, but Claude's trait adds `Agent` tool parallel dispatch, Bob 1 legacy adds `switch_mode` orchestration, Bob 2 adds `spawn_subagent` orchestration, Gemini adds named agent delegation, and OpenCode adds step-limited subagents. See [Architecture Guide](architecture.md#agent-traits) for details.

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
- **Parallel reviewer fan-out:** Stamp Gate dispatches spec, quality, and security reviewers simultaneously
- **Adversarial Code Review:** parallel Critic Lanes (via Moderator subagent) adversarially review implementation before two-stage review
- **Simplest configuration:** 3 template files total (fewest of any agent)

---

## IBM Bob 1 Legacy -- B+A Hybrid with Custom Modes

### Dispatch Model

The `--ai bob` target is the Bob 1 legacy path. It uses a "Behavior + Advanced" hybrid. Skills load in **Advanced mode** (unrestricted), then the first step in each gate file switches to a **restricted custom mode**. This two-phase approach gives initial access to load all skill files, then constrains behavior for the actual work.

### Template Files

| File | Purpose |
|------|---------|
| `templates/bob/custom_modes.yaml` | 5 custom modes with scoped tool groups |
| `templates/bob/gates/*.md` | 7 monolithic gate files (one per pipeline phase) |
| `templates/bob/rules/iron-laws.md` | Shared iron laws loaded across all modes |
| `templates/bob/rules-camel-{phase}/*.md` | Per-mode custom rules |

Bob 1 has the most template files (17+) because it cannot chain skill references -- each gate file must be self-contained, inlining the complete orchestration logic for its phase (6-10 KB each).

### How It Works

```
User: /camel-start
  └── Bob loads gate file (Advanced mode -- can read all files)
      └── Step 1: Route to camel-brainstorm or camel-migrate
          └── Tool restrictions activate (read + .md edit + mcp only)
              └── Follows gate instructions with restricted tools
```

### Custom Modes and Tool Groups

| Mode | Tools Allowed | Purpose |
|------|---------------|---------|
| `camel-design` | read, edit (`.md` only), mcp, browser | Design interview, no code |
| `camel-plan` | read, edit (`.md` only), mcp | Planning from approved spec |
| `camel-implement` | read, edit, command, mcp | Route implementation |
| `camel-validate` | read, command | Quality review |
| `camel-test` | read, edit, command, mcp | Test generation and execution |

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
- **File-type-scoped edits:** brainstorm and plan modes can only edit `.md` files (via `fileRegex` in tool groups)

---

## IBM Bob 2 -- Native Subagents with Bob Modes

### Dispatch Model

The `--ai bob2` target uses Bob 2 native `spawn_subagent` while still generating files under `.bob/`. The target name is only the Camel-Kit selector; Bob reads `.bob/commands`, `.bob/skills`, `.bob/custom_modes.yaml`, and `.bob/mcp.json`.

Bob 2 keeps the shared Camel-Kit skills and appends Bob 2 traits. It does not replace `SKILL.md` files with monolithic gates.

### Template Files

| File | Purpose |
|------|---------|
| `templates/bob2/custom_modes.yaml` | Bob 2 custom modes using `read`, `edit`, `execute`, `mcp`, `skill`, `todo`, `artifact`, `subagent`, and `mode` groups |
| `templates/bob2/rules*/` | Lightweight project and mode rules |
| `templates/traits/bob2/*.append.md` | Native `spawn_subagent` orchestration guidance |
| `templates/dispatch/bob2.md` | Shared dispatch block naming `spawn_subagent`, `explore`, `general`, and `fork_context` |

### How It Works

```
User: /camel-execute
  └── Parent Bob task loads shared camel-execute skill
      ├── camel-kit plan analyze groups independent waves
      ├── spawn_subagent name="general" for implementation/test/fix tasks
      ├── spawn_subagent name="explore" for research and reviews
      └── Parent merges summaries and keeps orchestration state
```

Multiple `spawn_subagent` calls in one parent turn run in parallel. Subagents return summaries and must not spawn subagents; the parent Bob task owns orchestration and follow-up dispatch.

### Unique Capabilities

- **Native isolated subagents:** `explore` for read-only work and `general` for edit/execute work
- **Parallel same-turn dispatch:** independent tasks in the current wave can be spawned together
- **Mode restrictions plus shared skills:** Bob 2 modes constrain tools while shared Camel-Kit skills define behavior
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
  - glob
  - grep_search
  - run_shell_command
  - mcp_camel_*           # all tools from Camel catalog MCP server
  - mcp_citrus_*          # all tools from Citrus MCP server
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

## Qwen -- Dual Dispatch with Fork Model

### Dispatch Model

Qwen uses a **dual dispatch model** via the `Agent` tool:

1. **Named subagents** -- when `subagent_type` is provided, a registered subagent is loaded and executed with its own system prompt, no parent history. The parent **blocks** until completion.
2. **Fork** -- when `subagent_type` is omitted, an implicit fork is created. The fork **inherits the parent's full conversation context** and runs in the background while the parent continues. Fork children cannot create further forks (enforced via `AsyncLocalStorage`).

The fork mechanism shares the parent's exact system prompt and tool declarations to exploit **DashScope prompt caching** -- all forks hit the same cache prefix, saving 80%+ tokens. This is a provider-specific optimization.

Qwen also supports 7 sub-agents with description-based auto-delegation. When a user describes their intent (e.g., "validate my routes"), Qwen automatically routes to the right sub-agent based on keyword matching in the description field.

### Template Files

| File | Purpose |
|------|---------|
| `templates/qwen/qwen-md.md` | `QWEN.md` -- project root (iron laws, Camel version, MCP usage) |
| `templates/qwen/agents/*.md` | 7 sub-agent definitions with tool whitelists and auto-delegation descriptions |
| `templates/qwen/qwenignore` | Excludes build output |

### How It Works

```yaml
# Sub-agent definition
name: camel-designer
description: "MUST BE USED for discovering integration requirements,
              interviewing about data flows, and designing Camel routes"
tools:
  - read_file
  - read_many_files
  - glob
  - grep_search
  - run_shell_command
```

The `MUST BE USED` phrasing forces automatic delegation -- Qwen's documentation explicitly says this pattern encourages automatic selection.

```
User: "I want to design an order processing integration"
  └── Qwen matches "design" + "integration" to camel-designer description
      └── Auto-delegates to camel-designer sub-agent
          └── Sub-agent reads SKILL.md → follows guides
              └── Tool access restricted to whitelist (read-only for brainstorm)
```

### Tool Whitelist Profiles

| Profile | Phases | Tools Included | Tools Excluded |
|---------|--------|----------------|----------------|
| **Read-only** | brainstorm, validate, plan | `read_file`, `read_many_files`, `glob`, `grep_search`, `run_shell_command` | `write_file`, `edit`, `task` |
| **Write + run** | test | All read tools + `write_file`, `edit`, exact Camel/Citrus MCP test tools | `task` |
| **Full access** | implement, migrate, executor | All tools including `task` (executor only) | None |

Qwen's whitelists are binary -- a tool is either available or not. No glob patterns or path-level restrictions (contrast with OpenCode).

### Template Variables

Sub-agent prompts use runtime-resolved template variables:

```markdown
Project: ${project_name}
Working directory: ${current_directory}
```

These are resolved by Qwen at invocation time, providing context-aware behavior without hardcoding project paths.

### Parallel Execution

Qwen implements a **batch-based concurrency model** in the `CoreToolScheduler`. Read, Search, and Fetch tool calls are marked concurrency-safe and batched via `Promise.all()` with a configurable cap (default: max 10). Agent tool invocations are sequential by default.

Multi-agent parallelism is achieved through the **fork model**: the parent omits `subagent_type`, creating a background fork that inherits the full conversation context. The parent continues immediately. This enables parallel research or review tasks — but fork children cannot create further forks.

### Unique Capabilities

- **Dual dispatch (named + fork):** named subagents for clean-context tasks, forks for background tasks with parent context sharing
- **DashScope prompt caching:** fork model shares parent's exact system prompt prefix, saving 80%+ tokens across concurrent forks
- **Description-matching auto-delegation:** user intent is automatically matched to the right sub-agent without explicit command invocation
- **Template variables (`${project_name}`, `${current_directory}`):** resolved at runtime for context-aware prompts
- **Allowlist + blocklist:** `tools` and `disallowedTools` for flexible per-subagent tool control
- **Approval mode per subagent:** `default`, `plan`, `auto-edit`, `yolo`
- **Background flag:** `background: true` in frontmatter for non-blocking execution

---

## OpenCode -- Granular Permission System + Opt-In Delegation

### Dispatch Model

OpenCode dispatches via the `task` tool, which creates a **child session** with `parentID` and derived permissions. 14 permission types support glob patterns with last-match-wins evaluation, enabling path-scoped file edits, command-level bash control, and per-agent execution limits.

**Subagent-to-subagent delegation** was historically blocked (task tool removed from subagent sessions). PR #7756 added opt-in delegation with configurable call budgets and depth limits. Users can also invoke agents via `@agent-name` syntax.

**Permission inheritance:** Parent agent deny rules are forwarded to child sessions via `deriveSubagentSessionPermission()`. Known gap: permissions are not fully transitive — a restricted parent can invoke a subagent with broader permissions.

### Template Files

| File | Purpose |
|------|---------|
| `templates/opencode/agents-md.md` | `AGENTS.md` -- ultra-minimal bootstrap (compressed iron laws + `/camel-start` directive) |
| `templates/opencode/agents/*.md` | 7 agent definitions with per-type glob-pattern permissions |

OpenCode reads `CLAUDE.md` as fallback if no `AGENTS.md` exists, but Camel-Kit generates `AGENTS.md` explicitly to take precedence. The AGENTS.md is now ultra-minimal (~80 tokens): compressed iron laws + a single bootstrap directive pointing to `/camel-start`, which loads the full context via progressive skill loading.

No `.opencodeignore` -- OpenCode uses `.gitignore` for file exclusion (simpler than Qwen/Gemini).

### How It Works

```yaml
# Tester -- path-scoped edits
name: tester
mode: subagent
edit:
  "*": ask               # ask before editing source files
  "src/test/**": allow   # auto-allow test file edits
  "test/**": allow
bash:
  "*": allow
  "rm -rf *": deny       # safety net
task: deny
steps: 40

# Executor -- full access with nested delegation
name: executor
mode: subagent
edit: allow
bash:
  "*": allow
task:
  "*": allow             # can dispatch to other agents
steps: 100
```

### Permission Tiers

| Tier | Agents | Edit | Bash | Task | Steps |
|------|--------|------|------|------|-------|
| **Read-only** | brainstormer, validator, planner | `deny` | `"*": ask`, safe commands allowed | `deny` | 20-30 |
| **Test-write** | tester | path-scoped (test dirs auto-allowed) | `"*": allow`, `"rm -rf *": deny` | `deny` | 40 |
| **Full access** | implementer, migrator | `allow` | `"*": allow`, `"rm -rf *": deny` | `deny` | 50 |
| **Orchestrator** | executor | `allow` | `"*": allow` | `"*": allow` | 100 |

### Steps Limits

Each agent has a `steps` limit. When reached, OpenCode instructs the agent to summarize completed work and list remaining tasks -- graceful degradation rather than hard failure.

| Agent | Steps | Rationale |
|-------|-------|-----------|
| brainstormer | 20 | Focused interview, shouldn't need many iterations |
| planner | 30 | Plan creation with MCP queries and writing |
| validator | 20 | Read-only analysis should complete quickly |
| tester | 40 | TDD cycle (write test, run, fix, run again) needs more iterations |
| implementer | 50 | Route implementation with MCP lookups, file creation, smoke tests |
| migrator | 50 | Similar to implementer but with analysis phase |
| executor | 100 | Orchestrating multiple tasks via sub-agent dispatch |

### Unique Capabilities

- **14 permission types with glob patterns:** most granular tool control of any agent
- **Path-scoped edits:** tester can auto-edit `src/test/**` but must ask before touching source files
- **Command-level bash control:** validator can run `mvn validate` but not `rm -rf`
- **`steps` limit per agent:** prevents runaway execution with graceful summarization
- **`doom_loop` detection:** catches agents stuck in repetitive tool call patterns (inherited default behavior)
- **Last-match-wins evaluation:** glob patterns are order-sensitive, allowing fine-grained overrides
- **Opt-in subagent delegation:** PR #7756 enables subagent-to-subagent dispatch with configurable depth limits and call budgets
- **LLM-level parallel tool calls:** multiple tool calls in a single LLM response execute concurrently
- **Mixed-provider model support:** agents can use different model providers (e.g., `anthropic/claude-opus-4-6`, `openai/gpt-4o`)

---

## Agent Comparison

| Aspect | Claude | Bob 1 legacy | Bob 2 | Gemini | Qwen | OpenCode |
|--------|--------|--------------|-------|--------|------|----------|
| Dispatch model | Parallel subagents | Mode switching | `spawn_subagent` (`explore`, `general`) | `invoke_subagent` unified tool (local/remote/browser) | Dual: named subagent + fork | `task` tool creating child sessions |
| Template files | 3 | 17+ | Bob 2 modes + traits + rules | 12 | 9 | 8 |
| Tool restriction | Instruction-based | Mode tool groups | Mode tool groups + `allowedSubagents` | Allowlist + TOML policy + server-scoped wildcards | Allowlist + blocklist | 3-state permissions + bash glob patterns |
| Path-scoped edits | No | `.md` only (via fileRegex) | Mode-dependent `fileRegex` | Yes (Policy Engine) | No | Yes (glob patterns) |
| MCP auto-approval | No (manual) | No (manual) | No (manual) | Yes (TOML policy) | No (manual) | No (manual) |
| Parallel execution | Yes (graph-based) | No | Yes (same-turn `spawn_subagent`) | Yes (scheduler `Promise.all()`) | Partial (read-only tools concurrent; fork background) | Partial (LLM-level parallel tool calls) |
| Subagent recursion | Yes (no limit) | N/A | No (subagents must not spawn subagents) | No (hardcoded `Kind.Agent` filter) | No (fork-of-fork blocked) | Opt-in configurable depth |
| Execute phase | Subagent with parallel dispatch | Gate file with mode switch | Parent task orchestrates subagents | Main agent (recursion prevention) | Sub-agent with `task` tool | Agent with `task` permission |
| Instruction composition | Single `CLAUDE.md` | Modes + gates + rules | Shared skills + Bob 2 traits + modes | `@file.md` modular imports | Single `QWEN.md` | Ultra-minimal `AGENTS.md` |
