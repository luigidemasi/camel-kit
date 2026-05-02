# Camel-Kit Iron Laws

> Four non-negotiable laws enforced across ALL pipeline phases. No exceptions. No workarounds. No "just this once."

**Violating the letter of these rules is violating the spirit of these rules.**

---

## Iron Law 1: MCP Catalog Verification

```
EVERY COMPONENT, EIP, DATAFORMAT, AND LANGUAGE MUST BE VERIFIED VIA MCP CATALOG
BEFORE BEING WRITTEN TO ANY SPEC, TDD, OR YAML FILE.
```

You do NOT know what components exist. You do NOT know their options. The MCP catalog is the single source of truth.

**Gate function:**
1. IDENTIFY the component/EIP/dataformat/language name
2. CALL the appropriate MCP tool (`camel_catalog_component`, `camel_catalog_eip`, `camel_catalog_dataformat`, `camel_catalog_language`)
3. READ the response — confirm the artifact exists and note exact option names
4. USE only verified names and options in your output
5. If the artifact does NOT exist, STOP and find an alternative

**MCP tool parameters:** Always pass `runtime` and `platformBom` matching the project. See `shared/mcp-setup.md` for the version mapping table.

### Rationalization Table

| Excuse | Reality |
|--------|---------|
| "I know this component exists" | You know training data. Catalogs change between versions. Verify. |
| "I'll verify later" | Later never comes. Verify NOW, before writing. |
| "It's a common component, everyone uses it" | Common ≠ exists in this version. Verify. |
| "I'll just use the option name from memory" | Option names differ between versions. `uri` vs `path` vs `resourceUri`. Verify. |
| "The MCP server is slow" | Slow ≠ optional. Wait for the response. |
| "I verified a similar component" | Similar ≠ same. Each component gets its own verification. |
| "This is just the design spec, not code" | Wrong specs → wrong code. Verify at design time. |

### Red Flags — STOP If You Think:

- "I'm pretty sure this component is called..."
- "The options are probably..."
- "This should work based on what I know..."
- "I'll check the catalog after I finish the spec..."
- "I used this component in another project..."

---

## Iron Law 2: Constitution Compliance

```
EVERY GENERATED ROUTE MUST PASS ALL 7 CONSTITUTION RULES.
NO ROUTE PASSES QUALITY REVIEW WITHOUT CONSTITUTION GATE CHECK.
```

The 7 rules are absolute. They apply to every route, every time, regardless of complexity or context.

**The 7 Rules:**
1. **Route Structure** — every route has a source (`from:`) and a sink (final `to:`). `direct:`/`seda:` sub-routes exempt from external sink.
2. **Single Responsibility** — one route = one purpose, one sentence. >7 processing steps = WARNING.
3. **Separation of Concerns** — Ingestion → Processing → Delivery. Business logic in beans, integration logic in routes.
4. **Naming Conventions** — Route IDs: `<domain>-<action>[-<qualifier>]`. Endpoints: `direct:<route-id>`. Custom headers: `kebab-case`.
5. **Observability** — every route declares `routeId` and `description`. Correlation IDs for cross-route tracing.
6. **External Configuration** — never hardcode connection strings, credentials, or environment values. Use `{{PLACEHOLDER}}` syntax.
7. **Component Catalog Verification** — every component verified to exist in the Apache Camel catalog (see Iron Law 1).

**Gate function:**
1. For EACH generated route:
2. CHECK Rule 1: has `from:` and terminal `to:`?
3. CHECK Rule 2: one clear purpose? ≤7 steps?
4. CHECK Rule 3: concerns separated? Business logic in beans?
5. CHECK Rule 4: route ID follows `<domain>-<action>` pattern?
6. CHECK Rule 5: has `routeId` AND `description`?
7. CHECK Rule 6: no hardcoded values? All configurable via properties?
8. CHECK Rule 7: all components MCP-verified to exist in the catalog?
9. ALL rules pass → route approved
10. ANY rule fails → fix before proceeding

### Rationalization Table

| Excuse | Reality |
|--------|---------|
| "It's a simple route, naming doesn't matter" | Simple routes become complex. Naming matters always. |
| "I'll add the routeId later" | Every route, every time. Not later. NOW. |
| "Hardcoding is fine for the demo" | We don't generate demos. We generate production code. |
| "This route needs more than 7 steps" | Split it. Single responsibility is non-negotiable. |
| "The description is obvious from the route" | Obvious to you. Not to the next person reading it. Add it. |

### Red Flags — STOP If You Think:

- "This route is too simple for all 7 rules..."
- "I'll add observability at the end..."
- "Just this one hardcoded value..."
- "The route ID can be auto-generated..."

---

## Iron Law 3: No Code Without Design Approval

```
NEVER GENERATE IMPLEMENTATION ARTIFACTS BEFORE THE USER HAS APPROVED THE DESIGN SPEC.
```

The pipeline is: Brainstorm → Plan → Execute. Brainstorm produces a design spec. The user reviews and approves it. ONLY THEN does planning and execution begin. Planning flows directly into execution — there is no separate plan approval gate. The user's approval of the design is the single gate that authorizes all downstream work.

**Gate function:**
1. BRAINSTORM produces design spec (BRD + TDDs)
2. USER reviews and explicitly approves ("approved", "looks good", "go ahead", etc.)
3. ONLY THEN invoke camel-plan
4. PLAN produces implementation plan and auto-invokes camel-execute
5. EXECUTE runs environment probe, implementation, and verification

Skipping design approval = generating code the user didn't ask for = wasted effort.

**Why no plan approval gate:** The plan is a deterministic decomposition of approved TDDs into implementation tasks. If the design is approved, the plan is implementation detail. The environment probe (first step of execute) catches feasibility issues that a plan review never could. If architectural failures are found, the re-plan loop modifies affected TDDs and re-executes automatically (max 3 rounds).

### Rationalization Table

| Excuse | Reality |
|--------|---------|
| "The user seems happy with the direction" | Seems ≠ approved. Ask explicitly. |
| "I'll generate code and they can review it" | Review ≠ approve. Get approval BEFORE generating. |
| "The spec is obvious, no need to wait" | Obvious to you. The user may have different priorities. |
| "I'll save time by starting implementation early" | Rework costs more than waiting. Always. |
| "The user said 'go' for the brainstorm, that covers execution too" | Brainstorm approval covers the design. It does authorize planning and execution. |
| "I should wait for the user to approve the plan before executing" | Plan approval was removed. Execution auto-proceeds after planning. |

### Red Flags — STOP If You Think:

- "I know what they want, let me just start coding..."
- "The spec is basically done, I can start the plan in parallel..."
- "They approved the previous flow, this one is similar..."
- "I'll generate a draft and they can adjust..."

---

## Iron Law 4: Spec Compliance Before Quality

```
ALWAYS RUN SPEC COMPLIANCE REVIEW BEFORE CODE QUALITY REVIEW.
WRONG ORDER WASTES EFFORT.
```

During execution, every task goes through two-stage review:
1. **Stage 1: Spec Compliance** — does the output match the design spec exactly? All components present? Structure correct? TDD sections covered?
2. **Stage 2: Code Quality** — constitution compliance, security (MCP checks), anti-patterns, YAML rules

If spec compliance fails, the output is wrong regardless of quality. Reviewing quality before compliance wastes the quality reviewer's time on code that will be rewritten.

**Gate function:**
1. Implementer completes task
2. FIRST: dispatch spec-compliance-reviewer
3. WAIT for spec review result
4. If spec review FAILS → return to implementer with feedback. DO NOT proceed to quality review.
5. If spec review PASSES → THEN dispatch code-quality-reviewer
6. If quality review FAILS → return to implementer with feedback
7. If BOTH pass → mark task complete

### Rationalization Table

| Excuse | Reality |
|--------|---------|
| "I can run both reviews in parallel to save time" | Parallel = wasted effort when spec fails. Sequential. Always. |
| "The implementation clearly matches the spec" | Clearly ≠ verified. Run the spec review. |
| "Quality issues are more important than spec issues" | Wrong code with good quality is still wrong code. Spec first. |
| "I'll combine both reviews into one pass" | Combined reviews miss things. Two stages, two focuses. |

### Red Flags — STOP If You Think:

- "Let me just do a quick quality check first..."
- "Both reviews can happen at the same time..."
- "The spec is simple, I can skip the compliance check..."
- "I'll review quality while waiting for spec review..."

---

## Enforcement

These laws are referenced by every pipeline skill (`camel-brainstorm`, `camel-plan`, `camel-execute`) and every agent persona. They are not guidelines. They are not best practices. They are non-negotiable requirements.

**When in doubt:** re-read the Iron Law. Follow the gate function. Check the rationalization table. If your thought matches a red flag, STOP and follow the process.

**The cost of following the process is minutes. The cost of skipping it is hours of rework, broken integrations, and lost trust.**
