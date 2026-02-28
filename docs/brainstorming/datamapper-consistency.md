# Brainstorming: Improving DataMapper XSLT Generation Consistency

**Date:** 2026-02-28, 13:30–15:00 CET

---

## Problem Statement

The DataMapper XSLT generation produces drastically different output between runs, even with the same input TDD. This is because the TDD captures mappings at a **semantic level** (field names + types), but XSLT generation requires **structural decisions** (exact XPaths, lossless XML element types, approach A vs B). The LLM re-interprets these structural decisions every run.

---

## Approaches Considered

### 1. Rigid XSLT Skeletons per Pattern
Provide exact skeleton templates with `{{placeholder}}` markers. The LLM fills slots rather than authoring code. Dramatically constrains the output space.

### 2. Intermediate Structured Representation
Two-phase process: LLM produces a structured JSON mapping plan, then a second pass (or deterministic code) converts JSON into XSLT. Separates "understanding" from "code generation."

### 3. Per-Field XSLT Snippets
Provide snippet templates per mapping type (direct copy, type conversion, conditional, collection). LLM assembles the file from building blocks.

### 4. Self-Validation Pass (SELECTED)
After generating the XSLT, add a mandatory verification step where the LLM checks each field mapping from the TDD against the generated XSLT and reports mismatches. Doesn't fix non-determinism but catches errors.

### 5. Canonicalize the TDD -> XSLT Contract (SELECTED)
Tighten the TDD format — enrich it with pre-computed XPaths and target elements during `/camel-flow` or `/camel-migrate`, so `/camel-implement` does mechanical translation rather than interpretation.

---

## Selected Approach: 4 + 5

**Approach 5 reduces variance** — the LLM has less to interpret, so outputs converge.
**Approach 4 catches what slips through** — even with pre-computed XPaths, the LLM might deviate; the checklist forces self-correction.
**Together:** 5 makes the validation in 4 actually verifiable — without concrete XPaths in the TDD, the LLM would be validating its own interpretation against its own interpretation.

---

## Key Design Decisions

### Enriched Field Mappings Table

Current (6 columns):
```
| Source Field | Src Type | Target Field | Tgt Type | Transformation | How |
```

Proposed (8 columns):
```
| Source Field | Src Type | Source XPath | Target Field | Tgt Type | Target Element | Transformation | How |
```

Plus header-level metadata:
```
**XSLT Pattern:** B — JSON->JSON
**XSLT Approach:** A (useJsonBody: true)
```

Decision: **Single enriched table** (not split into semantic + structural).

### Schema Requirement for XPath Computation

The XPath derivation depends on `field path + field type + format (JSON/XML/Primitive)`, NOT on whether a schema file exists. By the time canonicalization runs, whatever is known is already in the semantic table rows. No schema files need to be re-read.

Cases:
- **Both schemas present**: XPaths computed fully from field paths + types
- **Source schema present, no target**: Source XPath full, target element from user-described fields
- **No source, target present**: Source XPath from user-described fields, target element full
- **Neither schema**: Both from user-described fields (best effort)
- **Truly primitive** (single value, no structure): XPath is `.` (context node)

### Shared Logic Between camel-flow and camel-migrate

Both `datamapper-interview.md` (camel-flow) and `datamapper-migrate.md` (camel-migrate) produce the same TDD DataMapper section but differ in how they **gather** mappings (interactive questions vs DataWeave extraction).

The **enrichment + TDD writing** is identical, so a new shared guide (`datamapper-canonicalize.md`) will own that logic.

Architecture:
```
datamapper-interview.md (camel-flow)
  Steps 1-6: gather semantic mappings from user
  Step 7: -> load shared/datamapper-canonicalize.md

datamapper-migrate.md (camel-migrate-mule)
  Steps 1-5: extract semantic mappings from DataWeave + confirm
  Step 6: -> load shared/datamapper-canonicalize.md

              shared/datamapper-canonicalize.md
  Step 1: Determine XSLT pattern + approach
  Step 2: Compute Source XPath per field
  Step 3: Compute Target Element per field
  Step 4: Present enriched table for confirmation
  Step 5: Write enriched TDD section

datamapper-implement.md (camel-implement)
  Read enriched TDD (with XPaths + elements pre-computed)
  Generate XSLT mechanically from pre-computed columns
  NEW: Self-validation pass (verify each TDD row matches XSLT)
```

**Migration-specific Step 4 stays** in `datamapper-migrate.md` because confidence indicators (inferred from DataWeave) should be confirmed BEFORE canonicalization.

### Self-Validation Checklist (in datamapper-implement.md)

After generating XSLT, verify:
1. **Completeness** — every TDD row has a matching element in the XSLT
2. **Source XPath match** — `select="..."` matches TDD's Source XPath column
3. **Target Element match** — XSLT element tag/key matches TDD's Target Element column
4. **Type consistency** — `fn:string`/`fn:number`/`fn:boolean` matches field type
5. **Approach purity** — no `xsl:param` + `useJsonBody` mixing

If any check fails: fix the XSLT and re-verify before proceeding.

---

## Files Affected

| File | Action |
|------|--------|
| `skills/shared/datamapper-canonicalize.md` | CREATE |
| `skills/camel-flow/guides/datamapper-interview.md` | EDIT — remove Steps 7-8, add shared guide load |
| `skills/camel-migrate-mule/guides/datamapper-migrate.md` | EDIT — remove Step 6, add shared guide load |
| `skills/camel-implement/guides/datamapper-implement.md` | EDIT — expect enriched table, add validation pass |
| `skills/camel-flow/SKILL.md` | EDIT — update description |
| `skills/camel-migrate-mule/SKILL.md` | EDIT — update description |

All paths relative to `camel-kit-core/src/main/resources/`.

---

## Implementation Outcome

### First Test Result

After implementing approaches 4+5, the first test (Weather Alert migration from MuleSoft) revealed a remaining issue: the generated XSLT was **completely empty** despite the TDD being correctly enriched with canonical XPaths.

**Root cause:** The Pattern B skeleton in `datamapper-implement.md` only showed **Approach B** (with `xsl:param`), but the TDD specified **Approach A** (useJsonBody). The LLM hit a conflict between the skeleton template and the TDD header, and produced an empty output with wrong `method="xml"` instead of `method="text"`.

Specific failures:
- `method="xml"` instead of `method="text"` (Pattern B requires `text`)
- Missing `$mapped-xml` variable — the entire mapping logic was absent
- `xml-to-json($mapped-xml)` referenced a variable that was never declared
- Self-validation step wasn't yet enforced

### Fix: Per-Approach Skeletons

Split Pattern B (and Pattern C) into **two explicit skeleton templates**:

- **Pattern B — Approach A (useJsonBody: true):** No `xsl:param`, navigate from `/fn:map/...` directly. Includes complete weather alert example with all 4 fields.
- **Pattern B — Approach B (manual header param):** With `xsl:param` + `json-to-xml()` variable. Navigate from `$body-x/fn:map/...`.

The LLM now picks the correct skeleton based on the `XSLT Approach` field in the TDD header — no ambiguity.

Also removed the redundant ~200-line "Two correct approaches" section that duplicated information now covered by the per-pattern skeletons, replacing it with concise reference sections.

### After Fix

DataMapper produces correct XSLT transformations consistently.

### Key Takeaway

For LLM-driven code generation, three techniques combine effectively:
1. **Reduce interpretation space** — canonical XPaths pre-computed upstream
2. **Explicit template selection** — per-approach skeletons, not a single skeleton with conditional rules
3. **Self-verification** — mandatory validation pass catches remaining deviations

The biggest lesson: a single skeleton with prose instructions saying "if Approach A, omit the xsl:param" does NOT work reliably. The LLM needs to see the **exact skeleton** for its case, with no conditionals to resolve.
