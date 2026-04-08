# Graph Project Context — Implementation

> **Prerequisites:** See `shared/graph-availability.md` for availability check and fallback rules.
> **Runs as:** Step 0 in orchestrator.md, before Step 1 (DataMapper).
> **Output:** PROJECT_CONTEXT variables consumed by Steps 2, 3, and 5.

---

## Step 0: Project Context from Graph

### 0.1 — Property Naming Conventions

Call `graph_find(type="CONFIG_PROPERTY")` to extract all existing `camel.*` properties.

Group by prefix and extract patterns:
- Component config style: e.g., `camel.component.kafka.brokers` vs `camel.component.kafka.broker-url`
- Custom property style: e.g., `kafka.topic.orders` vs `kafka.topics.orders` (singular vs plural)
- Placeholder style: `{{property.name}}` — extract the naming convention for custom placeholders

Record:
- `PROPERTY_CONVENTIONS` = list of observed prefix → pattern

When generating `application.properties` (Step 3), match these conventions exactly. If the project uses `kafka.topic.input` (singular), do NOT generate `kafka.topics.input` (plural).

### 0.2 — Existing Beans and Classes

Call `graph_find(type="CLASS")` to inventory existing Java classes.

Look for:
- DataSource beans (classes with name containing `DataSource`)
- Custom processors (classes implementing `Processor`)
- Type converters
- RouteBuilder subclasses

Record:
- `EXISTING_BEANS` = list of class FQN → inferred purpose

When generating bean definitions in `application.properties` (Step 3), check `EXISTING_BEANS` first. If a DataSource bean already exists, reference it by name — do NOT generate a duplicate `#class:` bean definition.

### 0.3 — Dependency Version Alignment

Call `graph_find(type="MAVEN_ARTIFACT")` to get all existing dependency versions.

Record:
- `DEPENDENCY_VERSIONS` = map of artifactId → version

When generating `pom.xml` dependencies (Step 5), use versions from `DEPENDENCY_VERSIONS` if the artifact is already in the project. This prevents version mismatches like adding `camel-kafka:4.14.0` when the project already uses `camel-kafka:4.14.4.redhat-00008`.

### 0.4 — Route File Placement

Call `graph_find(type="RESOURCE_FILE")` and filter results to files ending in `.camel.yaml`.

Record:
- `ROUTE_DIRECTORY` = the directory containing the majority of existing route files

When resolving `ROUTE_DIR` in the File Path Table, if `ROUTE_DIRECTORY` is set and differs from the table default, use `ROUTE_DIRECTORY` for consistency. Log the override:

```
Using existing route directory: {ROUTE_DIRECTORY} (matches [N] existing routes)
```
