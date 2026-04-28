# Implementer Context Guide

> **Context:** Used by `camel-execute` to build the prompt for implementer subagents.
> **Purpose:** Defines how to construct the implementer subagent's context for maximum effectiveness.

---

## Prompt Construction

The implementer subagent receives a carefully constructed prompt. Never make the subagent discover context — provide it directly.

### Required Sections

#### 1. Role Assignment

Load the agent persona from `agents/[persona].md`. Include the full persona text at the top of the prompt.

#### 2. Task Description

Include the FULL task text from the plan. Do not summarize. Do not paraphrase.

```
## Your Task

[full task text from plan, including all steps and review criteria]
```

#### 3. Design Spec Context

Read the relevant design spec section and include it directly:

```
## Design Spec — Flow: [flow-name]

[full text of the flow design from docs/design-spec.md Section 3]
```

#### 4. Project Configuration

```
## Project Configuration

- Camel Version: [full version]
- Runtime: [main / spring-boot / quarkus]
- Platform BOM: [from versions.properties]
- Module Path: [path]
- Route Directory: [ROUTE_DIR from orchestrator path table]
- Properties Directory: [PROPS_DIR]
```

#### 5. Guide Instructions

```
## Guides to Load

You MUST read and follow these guides in order:

1. `[guide-path]` — [purpose]
2. `[guide-path]` — [purpose]
...

"Load" means READ the file and FOLLOW its instructions.
Do NOT skip a guide. Do NOT summarize what you read — execute it.
```

#### 6. MCP Tool Parameters

```
## MCP Configuration

For all MCP catalog calls, use these parameters:
- runtime: [main / spring-boot / quarkus]
- platformBom: [full GAV]

Example call:
camel_catalog_component(name="kafka", runtime="spring-boot", platformBom="org.apache.camel.springboot:camel-catalog-provider-springboot:4.14.0")
```

#### 7. Iron Laws Reminder

```
## Iron Laws (non-negotiable)

1. VERIFY every component/EIP/dataformat via MCP catalog BEFORE writing YAML
2. EVERY route MUST pass all 7 constitution rules
4. Generate ONLY what the task specifies — no extras

Read `shared/iron-laws.md` for full details and rationalization defense.
```

#### 8. Completion Status

```
## When Done

Report one of:
- DONE — all files generated, self-validated
- DONE_WITH_CONCERNS — files generated but concerns noted (list them)
- NEEDS_CONTEXT — missing information (specify what)
- BLOCKED — cannot proceed (explain why)
```

---

## Model Selection Guide

| Task Type | Model | Rationale |
|-----------|-------|-----------|
| Single route YAML (clear spec, 1-2 components) | Standard | Mechanical, well-constrained |
| Properties generation | Standard | Template-driven |
| Docker Compose | Standard | Declarative, pattern-based |
| DataMapper XSLT | Most capable | Complex transformation logic |
| Multi-route coordination | Most capable | Cross-file consistency |
| Migration (component mapping + API adaptation) | Most capable | Requires judgment |
| Maven POM setup | Standard | Declarative |
| Run script | Standard | Simple template |

---

## Anti-Patterns

- **Context dump:** Don't include the entire design spec — only the relevant section
- **Guide overload:** Don't list guides not relevant to this specific task
- **Missing MCP params:** Always include runtime and platformBom — subagent shouldn't guess
- **Vague instructions:** "Generate the route" is useless — include which components, which options, which error handling
- **Plan reference:** Don't say "see the plan" — include the task text directly
