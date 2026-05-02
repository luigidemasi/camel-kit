## Agent Optimization: OpenCode

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

### Plan Auto-Progression

Stage 1 (Plan) auto-proceeds to Stage 2 (Execute). No step budget is consumed waiting for plan approval — execution starts immediately after planning completes.

### Re-Plan Step Budget

If the re-plan loop triggers during Stage 2, the total step budget may exceed the initial `steps: 500`. The executor should not hard-fail when approaching the limit if a re-plan is in progress — report progress and let the re-plan complete its current round before yielding.
