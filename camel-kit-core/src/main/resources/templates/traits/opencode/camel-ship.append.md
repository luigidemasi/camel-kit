## Agent Optimization: OpenCode

### Step-Limited Pipeline Stages

Apply step limits per pipeline stage to prevent any single stage from consuming the entire session:

- Stage 0 (Brainstorm): `steps: 200` (interview can be lengthy)
- Stage 1 (Plan): `steps: 100` (structured output)
- Stage 2 (Execute): `steps: 500` (most complex, multiple subagents)
- Stage 3 (Verify): `steps: 150` (build + diagnosis loop)

### Agent Type Mapping

Use the appropriate OpenCode agent type for each pipeline stage:

- Stage 0 (Brainstorm): `General` — balanced for conversational design
- Stage 1 (Plan): `Plan` — optimized for structured planning output
- Stage 2 (Execute): `Build` — optimized for code generation
- Stage 3 (Verify): `Build` — needs to read code and run builds
