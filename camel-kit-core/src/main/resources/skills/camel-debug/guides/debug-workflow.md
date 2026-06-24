# Debug Workflow

Structured troubleshooting guide for ad-hoc Camel route debugging. Follows a strict 5-step sequence: **STOP → PRESERVE → DIAGNOSE → FIX → GUARD**.

**Always load `camel-verify/guides/error-taxonomy.md` alongside this guide** — it contains the error classification tables used in Step 3.

---

## Step 1: STOP — Understand Before Acting

**Do NOT change any file before completing Step 3 (DIAGNOSE).**

### Gather Context

1. Read `.camel-kit/config.properties` → extract:
   - `project.runtime` (quarkus | springboot | jbang)
   - `camel.version`
2. Ask the user (if not already provided):
   - What is the symptom? (build failure, startup error, runtime exception, unexpected behavior, no error but wrong output)
   - When did it start? (after a change, after upgrade, intermittent, always)
   - What changed recently? (new route, dependency update, config change, nothing)
3. If the user provides an error message or stack trace, capture it verbatim — do NOT paraphrase or truncate

### Environment Check

Check which tools are available (same as verify-loop prerequisites):

```text
ENVIRONMENT CHECK
Runtime:        {runtime from config.properties}
Camel version:  {version from config.properties}
Maven:          {./mvnw (wrapper) | mvn (system) | not found}
Docker:         {docker {version} | not found}
JDK:            {{vendor} {version} | not found}
```

---

## Step 2: PRESERVE — Capture Current State

Before any investigation that might change state, preserve the starting point.

### Steps

1. **Record file state:** run `git status` and `git diff --stat` to capture uncommitted changes
2. **Note the user's working branch:** run `git branch --show-current`
3. If there are uncommitted changes, warn the user:
   ```text
   You have uncommitted changes. I will not modify any files until diagnosis is complete.
   If a fix is needed, I will explain the change and ask for your approval first.
   ```

### What NOT to Do

- Do NOT run the build yet (Step 3 handles that)
- Do NOT modify any files
- Do NOT add dependencies
- Do NOT "try a quick fix"

---

## Step 3: DIAGNOSE — Classify the Error

This is the core diagnostic phase. **Run diagnosis steps as subagents** to keep verbose output out of the main context.

### 3.1 Reproduce the Error

Attempt to reproduce the reported symptom:

| Symptom Type | Reproduction Command |
|---|---|
| Build failure | `{MAVEN_CMD} compile -q` |
| Startup error | `{MAVEN_CMD} compile -q`, then check for `FailedToCreateRouteException` patterns |
| Runtime exception | Ask user to share the failing scenario or trigger |
| Wrong output | Ask user for expected vs actual output |
| Intermittent | Ask user for conditions under which it fails |

If the error cannot be reproduced, report this to the user and ask for more context. Do NOT guess.

### 3.2 Classify the Error

**Dispatch as subagent: Log Analyzer**

Using the error output from 3.1, classify the error against `camel-verify/guides/error-taxonomy.md`:

1. Match the error message or stack trace against the taxonomy patterns
2. Extract:
   - **Phase:** Build | Startup | Runtime
   - **Category:** the error family (e.g., Missing dependency, Route creation failure)
   - **Fix target:** Self-repair | camel-validate | camel-implement | Escalate
   - **Fix action:** what the taxonomy recommends
3. If no pattern matches → classification is **Unclassified**

### 3.3 Verify Components Against MCP Catalog

**Dispatch as subagent: MCP Verifier**

For errors related to components, endpoints, or data formats:

1. Identify all components referenced in the affected route files
2. For each component, call the MCP catalog tool:
   ```
   camel_catalog_component_doc(component="{component}", runtime="{runtime}", platformBom="{bom}")
   ```
3. Check:
   - Does the component exist for this runtime and version?
   - Are the endpoint options used in the route valid?
   - Are there known issues or CVEs?

### 3.4 Inspect Route Structure

**Dispatch as subagent: Route Analyzer**

Read the affected route files and check for:

1. **YAML structure errors** — malformed YAML, wrong indentation, missing required fields
2. **Component URI issues** — wrong URI format, missing required options
3. **Expression errors** — invalid Simple, XPath, or Groovy expressions
4. **Missing error handlers** — routes without error handling for external calls
5. **Missing beans** — references to beans not declared in the project

### 3.5 Present Diagnosis

After all subagents complete, synthesize the results into a diagnosis report:

```text
DIAGNOSIS REPORT
Symptom:        {user-reported symptom}
Reproduced:     {yes | no | partially}
Classification: {category from error-taxonomy.md | Unclassified}
Phase:          {Build | Startup | Runtime | Unknown}
Root cause:     {one-sentence explanation}
Confidence:     {High | Medium | Low}

MCP verification:
  {component}: {exists | MISSING | wrong options: {details}}

Route analysis:
  {finding 1}
  {finding 2}
```

**If confidence is Low:** tell the user what you found and what you're unsure about. Ask if they want to proceed with the suggested fix or provide more context.

---

## Step 4: FIX — Targeted Repair

Only proceed to this step after Step 3 is complete and the diagnosis is presented to the user.

### Fix Routing

Apply the fix target from the error classification:

| Fix Target | Action |
|---|---|
| Self-repair | Edit the file directly (pom.xml, application.properties, route YAML) |
| camel-validate | Load `camel-validate` to re-validate the affected route against MCP catalog |
| camel-implement | Load `camel-implement` to re-generate the affected route from its TDD |
| Escalate | Present the raw error and diagnosis to the user — do NOT attempt a fix |
| Unclassified | Present the raw error and diagnosis to the user — do NOT attempt a fix |

### Fix Protocol

1. **Explain before changing:** describe the proposed fix and why it addresses the root cause
2. **Minimal change:** fix only the identified issue — do NOT refactor, clean up, or improve surrounding code
3. **One fix at a time:** if multiple issues were found, fix them sequentially. Verify each fix before moving to the next
4. **Verify the fix:** after applying each fix, re-run the reproduction command from Step 3.1
   - If the error is resolved → proceed to Step 5
   - If the same error persists → the fix did not work. Report to the user and ask for guidance
   - If a NEW error appears → classify the new error (go back to Step 3.2) and fix it

### Iteration Limit

If 5 fix attempts fail to resolve the issue, stop and escalate:

```text
I've attempted 5 fixes without resolving the issue. Here's what I tried:
  1. {fix 1} — result: {still failing / new error}
  2. {fix 2} — result: {still failing / new error}
  ...

The underlying issue may require architectural changes or manual investigation.
```

---

## Step 5: GUARD — Prevent Recurrence

After the fix is verified, suggest preventive measures. These are recommendations — do NOT apply them without user approval.

### Guard Recommendations

Based on the error category, suggest one or more of:

| Error Category | Guard Suggestion |
|---|---|
| Missing dependency | "Add a build verification step to your CI that checks all component dependencies resolve" |
| Wrong endpoint options | "Run `/camel-validate` after changes to verify endpoint options against the MCP catalog" |
| Route creation failure | "Add a Citrus integration test for this route to catch startup failures early" |
| Expression failure | "Add test cases with edge-case input data to your Citrus tests" |
| External service | "Add a health check or circuit breaker to handle service unavailability" |
| Type conversion | "Add explicit type declarations in the route to catch conversion issues at startup" |
| Unclassified | "Consider adding this error pattern to the project's error handling documentation" |

### Guard Format

```text
GUARD — Recurrence Prevention
Fix applied:    {one-line summary of the fix}
Category:       {error category}

Suggested guard:
  {recommendation from table above}

To apply: {specific command or file change, if applicable}
```

---

## Summary Report

At the end of the workflow, present a complete summary:

```text
DEBUG SUMMARY
Symptom:        {original symptom}
Root cause:     {one-sentence root cause}
Classification: {category}
Fix applied:    {description of fix}
Files changed:  {list of modified files}
Guard:          {suggested preventive measure}
```
