## Agent Optimization: OpenCode

### LLM-Level Parallel Reviews at Stamp Gate

At the Stamp Gate, leverage LLM-level parallel tool calls to dispatch multiple reviews. While true async background delegation is not yet available, multiple `task` tool calls in a single response can express intent for concurrent review:

- Dispatch spec consistency review as `General` subagent (`steps: 50`)
- Dispatch code quality review as `General` subagent (`steps: 50`)
- Dispatch security scan as `Explore` subagent (`steps: 30`, read-only MCP CVE checks)

**Note:** Until true async (Issue #5887) is implemented, these may execute sequentially despite being in the same response. The trait is structured for forward compatibility — when async lands, no trait changes are needed.

### Step-Limited Pipeline Stages

Apply step limits per pipeline stage to prevent any single stage from consuming the entire session:

- Stage 0 (Brainstorm): `steps: 200` (interview can be lengthy)
- Stage 1 (Plan): `steps: 100` (structured output)
- Stage 2 (Execute): `steps: 500` (most complex, multiple subagents)
- Stage 3 (Verify): `steps: 200` (build + Citrus test execution + diagnosis loop)

### Agent Type Mapping

Use the appropriate OpenCode agent type for each pipeline stage:

- Stage 0 (Brainstorm): `General` — balanced for conversational design
- Stage 1 (Plan): `Plan` — optimized for structured planning output
- Stage 2 (Execute): `Build` — optimized for code generation
- Stage 3 (Verify): `Build` — needs to read code, run `camel test run`, and diagnose test failures

### Catalog and Knowledge Research

During Stage 2 (Execute), dispatch research-isolation subagents:

- Catalog research: `Explore` agent with `steps: 30` for batched MCP verification
- Knowledge queries: `Explore` agent with `steps: 20` for documentation lookup

### Plan Auto-Progression

Stage 1 (Plan) auto-proceeds to Stage 2 (Execute). No step budget is consumed waiting for plan approval — execution starts immediately after planning completes.

### Re-Plan Step Budget

If the re-plan loop triggers during Stage 2, the total step budget may exceed the initial `steps: 500`. The executor should not hard-fail when approaching the limit if a re-plan is in progress — report progress and let the re-plan complete its current round before yielding.

### Opt-In Delegation for Cascading Tasks

With subagent-to-subagent delegation (PR #7756), the executor can dispatch implementation subagents that themselves dispatch exploration subagents for codebase analysis. Configure depth limit = 2 (executor → implementer → explorer) with call budgets per level.
