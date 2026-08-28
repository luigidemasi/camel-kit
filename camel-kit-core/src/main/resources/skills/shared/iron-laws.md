# Camel-Kit Iron Laws

> Six non-negotiable laws enforced across ALL pipeline phases. No exceptions. No workarounds. No "just this once."

**Violating the letter of these rules is violating the spirit of these rules.**

---

## Iron Law 1: MCP Catalog Verification

```
EVERY COMPONENT, EIP, DATAFORMAT, AND LANGUAGE MUST BE VERIFIED VIA MCP CATALOG
BEFORE BEING WRITTEN TO ANY DESIGN SPEC OR YAML FILE.
```

You do NOT know what components exist. You do NOT know their options. The MCP catalog is the single source of truth.

**Gate function:**
1. IDENTIFY the component/EIP/dataformat/language name
2. CALL the appropriate MCP tool (`camel_catalog_component_doc`, `camel_catalog_eip_doc`, `camel_catalog_dataformat_doc`, `camel_catalog_language_doc`), ALWAYS passing `runtime` and the full `platformBom` GAV derived from the versions in `.camel-kit/config.properties` per the mapping table in `shared/mcp-setup.md` — the config file stores bare versions, never pass those alone. A call without version parameters is answered from the catalog bundled in the MCP server jar — a silently wrong version.
3. READ the response — confirm the artifact exists, note exact option names, and CHECK the `camelVersion` echoed in the response: it must match the project's resolved Camel version. On mismatch, treat the response as invalid and re-call with an explicit `platformBom`.
4. USE only verified names and options in your output
5. If the artifact does NOT exist, STOP and find an alternative

**Evidence requirement:** the tool call and its result must be visible in your work. A claim of "verified via MCP" with no visible call is a violation of this law.

**Forage carve-out:** `forage.*` property keys are NOT Camel catalog artifacts. Verify them against the cached
Forage catalog (`.camel-kit/.cache/forage/{FORAGE_VERSION}/`) per `shared/forage.md` — same rigor, different catalog.

---

## Iron Law 2: Constitution Compliance

```
EVERY GENERATED ROUTE MUST PASS ALL 8 CONSTITUTION RULES.
NO ROUTE PASSES QUALITY REVIEW WITHOUT CONSTITUTION GATE CHECK.
```

The 8 rules are absolute. They apply to every route, every time, regardless of complexity or context.

**The 8 Rules:**
1. **Route Structure** — every route has a source (`from:`) and a sink (final `to:`). `direct:`/`seda:` sub-routes exempt from external sink.
2. **Single Responsibility** — one route = one purpose, one sentence. >7 processing steps = WARNING.
3. **Separation of Concerns** — Ingestion → Processing → Delivery. Business logic in beans, integration logic in routes.
4. **Naming Conventions** — Route IDs: `<domain>-<action>[-<qualifier>]`. Endpoints: `direct:<route-id>`. Custom headers: `kebab-case`.
5. **Observability** — every route declares `routeId` and `description`. Correlation IDs for cross-route tracing.
6. **External Configuration** — never hardcode connection strings, credentials, or environment values. Use `{{PLACEHOLDER}}` syntax.
7. **Component Catalog Verification** — every component verified to exist in the Apache Camel catalog (see Iron Law 1).
8. **Infrastructure via Forage** — infrastructure beans are declared with `forage.*` properties when Forage covers them; configuration follows the ladder in `shared/forage.md` (Forage → component properties → hand-rolled bean with stated reason).

---

## Iron Law 3: No Code Without Design Approval and an Existing Plan

```
NEVER GENERATE IMPLEMENTATION ARTIFACTS BEFORE THE USER HAS APPROVED THE DESIGN SPEC
AND A TASK-BASED IMPLEMENTATION PLAN HAS BEEN GENERATED.
```

The pipeline is: Brainstorm (Design) → Plan → Execute → Validate.
1. **Understand:** Brainstorm produces a design spec. The user reviews and approves it.
2. **Decompose:** Planning breaks the approved design into discrete, atomic tasks.
3. **Implement:** Only during the execution phase are implementation artifacts (YAML, Java, etc.) generated.
4. **Assess:** Validate performs a terminal, report-only quality assessment after execution and internal verification.

**Gate function:**
1. BRAINSTORM produces business requirements and a design spec.
2. USER reviews and explicitly approves ("approved", "looks good", "go ahead", etc.).
3. ONLY THEN invoke camel-plan to produce an implementation plan.
4. EXECUTE phase (camel-execute) runs the actual code generation per task.
5. VALIDATE phase (camel-validate) diagnoses and reports final quality findings without changing implementation artifacts.

**NO "HELPFUL" CODE GEN:** Skills like `camel-migrate` or `camel-brainstorm` MUST NOT generate final routes or
application code. They generate business requirements and design specs. Implementation is reserved for the execution
phase.

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
CRITIC LANES RUN AFTER IMPLEMENTATION AND BEFORE STAGE 1 REVIEW.
USE PARALLEL FRESH CONTEXTS WHEN SUPPORTED; OTHERWISE RUN THE SAME LENSES INLINE
AND RECORD THAT FRESH-CONTEXT ISOLATION IS UNAVAILABLE.
```

Adversarial Code Review (ACR) is the "Adversarial Gate" that runs after implementation but BEFORE spec compliance review. A Moderator dynamically selects specialized Critic Lanes (Route Architecture, Security, Performance, Boundary Compliance, Behavioral Equivalence). Use fresh-context subagents where supported; single-conversation targets apply the same lanes sequentially and record the missing isolation.

**Gate function:**
1. Implementer completes code generation.
2. Run the **ACR Moderator** role with the code and the relevant design spec section.
3. Moderator selects Critic Lanes based on design spec content.
4. Each Critic runs adversarially in its own fresh context when supported, or sequentially inline otherwise.
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

Every implementation task must be surgical. Your goal is to fulfill the approved design spec and implementation plan
with the minimum required change to the existing codebase. Refactoring, cleanup, or "fixing" unrelated code creates
hidden regressions and wastes review time.

---

## Enforcement

These laws are referenced by every pipeline skill and every agent persona. They are non-negotiable requirements. The cost of following the process is minutes. The cost of skipping it is hours of rework and lost trust.
