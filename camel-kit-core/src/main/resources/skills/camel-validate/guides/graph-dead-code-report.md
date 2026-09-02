# Dead Code Candidate Report — Validation

> **Prerequisites:** See `shared/graph-availability.md` for availability check and fallback rules.
> **Runs after:** All other validation stages (Stages 4–7, anti-patterns) complete.
> **Output:** dead-code section in the selected validation report + inline summary.

---

## Stage 8: Structural Retirement-Candidate Analysis

### 8.1 — Run Analysis

Apply `shared/graph-availability.md`; use only its install-time fixed command prefix and discrete argv.

Run the command:
```bash
{COMMAND_PREFIX} graph dead-code
```

This returns the existing JSON schema. Treat every entry as a structural candidate within graph coverage, not as proof
that code or configuration is dead or safe to remove:
```json
{
  "unusedArtifacts": [...],
  "orphanedRoutes": [...],
  "unusedProperties": [...]
}
```

If the command exits with code != 0, skip this stage silently.

### 8.2 — Add Findings to the Validation Report

Append the following section to the validation report path selected by
`camel-validate`. Do not create a second report file:

```markdown
# Dead Code Candidate Report

Generated: [timestamp]
Graph: .camel-kit/project-graph.json

## Summary

| Category | Count | Severity |
|----------|-------|----------|
| Unused Maven Dependencies | [N] | WARNING |
| Orphaned Routes | [N] | WARNING |
| Unused Configuration Properties | [N] | INFO |

## Unused Maven Dependencies

These Camel dependencies are declared in pom.xml but no graph-covered route endpoint uses them. Confirm build,
configuration, custom-code, and runtime usage before changing the dependency.

| GroupId | ArtifactId | Version | Action |
|---------|-----------|---------|--------|
| [groupId] | [artifactId] | [version] | Review — remove if unused |

## Orphaned Routes

These routes consume from internal endpoints (`direct:`/`seda:`) but no graph-covered route produces to them. They are
candidates for source-owner review and may instead have a dynamic, external, unsupported, or missing producer.

| Route ID | From Endpoint | Possible Cause |
|----------|--------------|----------------|
| [routeId] | [from-uri] | No producer found |

## Unused Configuration Properties

These `camel.*` properties do not match an endpoint component scheme within graph coverage. Confirm all configuration
and runtime consumers before changing them.

| Property | Value | File |
|----------|-------|------|
| [key] | [value] | [file] |
```

### 8.3 — Show Summary

Display inline after the anti-pattern summary:

```
== STRUCTURAL RETIREMENT-CANDIDATE ANALYSIS ==

Unused Maven Dependencies: [N] ⚠️
Orphaned Routes: [N] ⚠️
Unused Config Properties: [N] ℹ️

Full findings: <selected-validation-report-path>
```

If all counts are 0:

```
== STRUCTURAL RETIREMENT-CANDIDATE ANALYSIS ==

No structural candidates found in available graph coverage.
```
