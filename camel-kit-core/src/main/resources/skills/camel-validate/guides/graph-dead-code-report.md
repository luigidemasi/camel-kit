# Dead Code Report — Validation

> **Prerequisites:** See `shared/graph-availability.md` for availability check and fallback rules.
> **Runs after:** All other validation stages (Stages 4–7, anti-patterns) complete.
> **Output:** `.camel-kit/dead-code-report.md` persistent report file + inline summary.

---

## Stage 8: Dead Code Analysis

### 8.1 — Run Analysis

Call `graph_dead_code()` to get the full dead code report.

If the tool returns `{"available":false,...}`, skip this stage silently.

### 8.2 — Write Report

Write the report to `.camel-kit/dead-code-report.md`:

```markdown
# Dead Code Report

Generated: [timestamp]
Graph: .camel-kit/project-graph.json

## Summary

| Category | Count | Severity |
|----------|-------|----------|
| Unused Maven Dependencies | [N] | WARNING |
| Orphaned Routes | [N] | WARNING |
| Unused Configuration Properties | [N] | INFO |

## Unused Maven Dependencies

These Camel dependencies are declared in pom.xml but no route endpoint uses them. They may be safe to remove.

| GroupId | ArtifactId | Version | Action |
|---------|-----------|---------|--------|
| [groupId] | [artifactId] | [version] | Review — remove if unused |

## Orphaned Routes

These routes consume from internal endpoints (direct:/seda:) but no other route produces to them. They may be dead code or missing a producer.

| Route ID | From Endpoint | Possible Cause |
|----------|--------------|----------------|
| [routeId] | [from-uri] | No producer found |

## Unused Configuration Properties

These camel.* properties don't match any endpoint's component scheme. They may be leftover from removed routes.

| Property | Value | File |
|----------|-------|------|
| [key] | [value] | [file] |
```

### 8.3 — Show Summary

Display inline after the anti-pattern summary:

```
== DEAD CODE ANALYSIS ==

Unused Maven Dependencies: [N] ⚠️
Orphaned Routes: [N] ⚠️
Unused Config Properties: [N] ℹ️

Full report: .camel-kit/dead-code-report.md
```

If all counts are 0:

```
== DEAD CODE ANALYSIS ==

✅ No dead code detected.
```
