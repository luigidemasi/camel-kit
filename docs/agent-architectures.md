# Agent Architectures

> Per-agent deep dive into dispatch models, tool restriction systems, template files, and unique capabilities. For the shared equalization layer (skills, iron laws, MCP), see [Architecture](architecture.md#5-agent-templates).

---

## Design Philosophy

Each agent uses a different architecture designed to **maximize that agent's native capabilities** -- not lowest-common-denominator parity. The equalization layer ensures identical pipeline behavior (same skills, same output), while the template layer exploits each agent's strongest features for dispatch, tool restriction, and configuration.

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

---

## Claude Code -- Parallel Subagent Dispatch

### Dispatch Model

Claude's `Agent` tool dispatches subagents with isolated context windows. Each subagent receives the skill content and executes independently. During `/camel-execute`, independent tasks are dispatched to multiple subagents simultaneously, using route graph topology to determine which flows can be implemented in parallel.

### Template Files

| File | Purpose |
|------|---------|
| `templates/claude/claude-md.md` | `CLAUDE.md` -- project rules (iron laws, Camel version, command prefix, MCP setup) |
| `templates/claude/dispatch-parallel.md` | Instructions for parallel subagent dispatch during `/camel-execute` |
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
- **Simplest configuration:** 3 template files total (fewest of any agent)

---

## IBM Project Bob -- B+A Hybrid with Custom Modes

### Dispatch Model

Bob uses a "Behavior + Advanced" hybrid. Skills load in **Advanced mode** (unrestricted), then the first step in each gate file switches to a **restricted custom mode**. This two-phase approach gives initial access to load all skill files, then constrains behavior for the actual work.

### Template Files

| File | Purpose |
|------|---------|
| `templates/bob/custom_modes.yaml` | 5 custom modes with scoped tool groups |
| `templates/bob/gates/*.md` | 7 monolithic gate files (one per pipeline phase) |
| `templates/bob/rules/iron-laws.md` | Shared iron laws loaded across all modes |
| `templates/bob/rules-camel-{phase}/*.md` | Per-mode custom rules |

Bob has the most template files (17+) because it cannot chain skill references -- each gate file must be self-contained, inlining the complete orchestration logic for its phase (6-10 KB each).

### How It Works

```
User: /camel-design
  └── Bob loads gate file (Advanced mode -- can read all files)
      └── Step 1: Switch to camel-design mode
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

## Gemini CLI -- Policy Engine + Modular Imports

### Dispatch Model

Gemini uses subagents for 6 pipeline phases, but `/camel-execute` runs in the **main agent context** because Gemini subagents cannot invoke other subagents (recursion prevention). The main agent can dispatch to all 6 subagents for orchestration.

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
max_turns: 20
timeout_mins: 20
```

`mcp_camel_*` automatically includes new tools when the MCP server adds them -- future-proof. Different subagents can have different MCP server access (e.g., validator gets catalog but not knowledge).

### Unique Capabilities

- **`@file.md` modular imports:** only agent that supports composing instructions from multiple files -- each concern is independently editable
- **Server-scoped MCP wildcards:** `mcp_camel_*` grants all tools from a specific server, auto-discovers new tools
- **TOML policy engine with priority tiers:** workspace policies can be overridden by user policies
- **Execution limits per subagent:** `max_turns` and `timeout_mins` prevent runaway execution
- **Execute in main agent:** the only agent where `/camel-execute` runs outside a subagent (platform constraint turned into a feature -- main agent has full delegation ability)

---

## Qwen -- Auto-Delegation with Sub-Agents

### Dispatch Model

Qwen uses 7 sub-agents with description-based auto-delegation. When a user describes their intent (e.g., "validate my routes"), Qwen automatically routes to the right sub-agent based on keyword matching in the description field.

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
| **Write + run** | test | All read tools + `write_file`, `edit` | `task` |
| **Full access** | implement, migrate, executor | All tools including `task` (executor only) | None |

Qwen's whitelists are binary -- a tool is either available or not. No glob patterns or path-level restrictions (contrast with OpenCode).

### Template Variables

Sub-agent prompts use runtime-resolved template variables:

```markdown
Project: ${project_name}
Working directory: ${current_directory}
```

These are resolved by Qwen at invocation time, providing context-aware behavior without hardcoding project paths.

### Unique Capabilities

- **Description-matching auto-delegation:** user intent is automatically matched to the right sub-agent without explicit command invocation
- **Template variables (`${project_name}`, `${current_directory}`):** resolved at runtime for context-aware prompts
- **Binary tool whitelists:** simple and explicit -- tools are either available or not
- **7 sub-agents (most of any agent):** every phase gets auto-delegation, even full-access phases benefit from context isolation

---

## OpenCode -- Granular Permission System

### Dispatch Model

OpenCode uses 7 agents with the most granular permission system of any supported agent. 14 permission types support glob patterns with last-match-wins evaluation. This enables path-scoped file edits, command-level bash control, and per-agent execution limits.

### Template Files

| File | Purpose |
|------|---------|
| `templates/opencode/agents-md.md` | `AGENTS.md` -- project root (iron laws, Camel version, MCP usage) |
| `templates/opencode/agents/*.md` | 7 agent definitions with per-type glob-pattern permissions |

OpenCode reads `CLAUDE.md` as fallback if no `AGENTS.md` exists, but Camel-Kit generates `AGENTS.md` explicitly to take precedence and include OpenCode-specific guidance.

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

---

## Agent Comparison

| Aspect | Claude | Bob | Gemini | Qwen | OpenCode |
|--------|--------|-----|--------|------|----------|
| Dispatch model | Parallel subagents | Mode switching | 6 subagents + main-agent execute | 7 auto-delegating sub-agents | 7 permission-scoped agents |
| Template files | 3 | 17+ | 12 | 9 | 8 |
| Tool restriction | Instruction-based | Mode tool groups | Subagent tools + TOML policy | Binary tool whitelists | 14-type glob permissions |
| Path-scoped edits | No | `.md` only (via fileRegex) | No | No | Yes (glob patterns) |
| MCP auto-approval | No (manual) | No (manual) | Yes (TOML policy) | No (manual) | No (manual) |
| Parallel execution | Yes (graph-based) | No | No | No | No |
| Execute phase | Subagent with parallel dispatch | Gate file with mode switch | Main agent (recursion prevention) | Sub-agent with `task` tool | Agent with `task` permission |
| Agent-specific ignore | No | No | `.geminiignore` | `.qwenignore` | No (uses `.gitignore`) |
| Instruction composition | Single `CLAUDE.md` | Modes + gates + rules | `@file.md` modular imports | Single `QWEN.md` | Single `AGENTS.md` |
