# Camel-Kit Iron Laws

> Six non-negotiable laws enforced across ALL pipeline phases. No exceptions. No workarounds. No "just this once."

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

---

## Iron Law 3: No Code Without Plan & Design Approval

```
NEVER GENERATE IMPLEMENTATION ARTIFACTS BEFORE THE USER HAS APPROVED THE DESIGN SPEC
AND A TASK-BASED IMPLEMENTATION PLAN HAS BEEN GENERATED.
```

The pipeline is: Brainstorm (Design) → Plan → Execute.
1. **Understand:** Brainstorm produces a design spec. The user reviews and approves it.
2. **Decompose:** Planning breaks the approved design into discrete, atomic tasks.
3. **Implement:** Only during the execution phase are implementation artifacts (YAML, Java, etc.) generated.

**Gate function:**
1. BRAINSTORM produces design spec (BRD + TDDs).
2. USER reviews and explicitly approves ("approved", "looks good", "go ahead", etc.).
3. ONLY THEN invoke camel-plan to produce an implementation plan.
4. EXECUTE phase (camel-execute) runs the actual code generation per task.

**NO "HELPFUL" CODE GEN:** Skills like `camel-migrate` or `camel-brainstorm` MUST NOT generate final routes or application code. They generate TDDs and Specs. Implementation is reserved for the execution phase.

---

## Iron Law 4: Spec Compliance Before Quality

```
ALWAYS RUN SPEC COMPLIANCE REVIEW BEFORE CODE QUALITY REVIEW.
WRONG ORDER WASTES EFFORT.
```

During execution, every task goes through two-stage review:
1. **Stage 1: Spec Compliance** — does the output match the design spec exactly?
2. **Stage 2: Code Quality** — constitution compliance, security (MCP checks), anti-patterns, YAML rules.

If spec compliance fails, the output is wrong regardless of quality.

---

## Iron Law 5: Adversarial Code Review

```
EVERY GENERATED CODE ARTIFACT MUST PASS AN ADVERSARIAL CODE REVIEW.
PARALLEL CRITIC LANES RUN AFTER IMPLEMENTATION AND BEFORE STAGE 1 REVIEW.
EACH CRITIC OPERATES IN A FRESH CONTEXT WITH NO ACCUMULATED SESSION STATE.
```

Adversarial Code Review (ACR) is the "Adversarial Gate" that runs after implementation but BEFORE spec compliance review. It dispatches a Moderator that dynamically selects specialized Critic Lanes (Route Architecture, Security, Performance, Boundary Compliance, Behavioral Equivalence), each running in a fresh-context subagent.

**Gate function:**
1. Implementer completes code generation.
2. Dispatch the **ACR Moderator** subagent with the code and the TDD.
3. Moderator selects Critic Lanes based on TDD content.
4. Each Critic runs adversarially in its own fresh context — no accumulated session state.
5. Moderator synthesizes findings: deduplicate, prioritize, produce verdict.
6. If FAIL (actionable findings) → return to implementer for fix. Max 3 cycles.
7. If PASS or PASS_WITH_TRADEOFFS → proceed to Stage 1 Review (Iron Law 4).

---

## Iron Law 6: Surgical Changes

```
TOUCH ONLY WHAT YOU’RE ASKED TO TOUCH. 
DON’T REFACTOR ADJACENT SYSTEMS. 
DON’T REMOVE CODE YOU DON’T FULLY UNDERSTAND. 
DON’T BRUSH AGAINST A TODO AND DECIDE TO REWRITE THE FILE.
```

Every implementation task must be surgical. Your goal is to fulfill the TDD with the minimum required change to the existing codebase. Refactoring, cleanup, or "fixing" unrelated code creates hidden regressions and wastes review time.

---

## Enforcement

These laws are referenced by every pipeline skill and every agent persona. They are non-negotiable requirements. The cost of following the process is minutes. The cost of skipping it is hours of rework and lost trust.
