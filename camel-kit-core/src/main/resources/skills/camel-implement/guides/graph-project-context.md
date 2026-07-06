# Graph Project Context — Implementation

> **Prerequisites:** See `shared/graph-availability.md` for availability check and fallback rules.
> **Runs as:** Step 0 in orchestrator.md, before Step 1 (DataMapper).
> **Output:** PROJECT_CONTEXT variables consumed by Steps 2, 3, and 5.

---

## Step 0: Project Context from Graph

### 0.0 — Run Composite Command

Read `.camel-kit/config.properties` to get the `project.command-prefix` property (default: `camel-kit`).

Run the composite command:
```bash
{COMMAND_PREFIX} graph project-context
```

This returns a JSON object with all project context in one call:
```json
{
  "propertyConventions": {...},
  "existingBeans": [...],
  "dependencyVersions": {...},
  "routeDirectory": "..."
}
```

If the command exits with code != 0, skip all graph-enhanced implementation steps and proceed without project context (use defaults from orchestrator.md).

### 0.1 — Property Naming Conventions

Extract from JSON response:
- `propertyConventions` = map of prefix → pattern

Record:
- `PROPERTY_CONVENTIONS` = response.propertyConventions

When generating `application.properties` (Step 3), match these conventions exactly. If the project uses `kafka.topic.input` (singular), do NOT generate `kafka.topics.input` (plural).

### 0.2 — Existing Beans and Classes

Extract from JSON response:
- `existingBeans` = list of class FQN → inferred purpose

Record:
- `EXISTING_BEANS` = response.existingBeans

When generating bean definitions in `application.properties` (Step 3), check `EXISTING_BEANS` first. If a DataSource bean already exists, reference it by name — do NOT generate a duplicate `#class:` bean definition.
Existing `forage.<name>.*` property blocks in the project are reusable beans too — reference them as `#<name>` instead of creating new ones.

### 0.3 — Dependency Version Alignment

Extract from JSON response:
- `dependencyVersions` = map of artifactId → version

Record:
- `DEPENDENCY_VERSIONS` = response.dependencyVersions

When generating `pom.xml` dependencies (Step 5), use versions from `DEPENDENCY_VERSIONS` if the artifact is already in the project. This prevents version mismatches like adding `camel-kafka:4.14.0` when the project already uses a different version.

### 0.4 — Route File Placement

Extract from JSON response:
- `routeDirectory` = the directory containing the majority of existing route files

Record:
- `ROUTE_DIRECTORY` = response.routeDirectory

When resolving `ROUTE_DIR` in the File Path Table, if `ROUTE_DIRECTORY` is set and differs from the table default, use `ROUTE_DIRECTORY` for consistency. Log the override:

```
Using existing route directory: {ROUTE_DIRECTORY} (matches [N] existing routes)
```
