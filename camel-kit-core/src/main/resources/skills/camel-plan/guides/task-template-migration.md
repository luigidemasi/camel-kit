# Task Template — Migration Projects

> **Context:** Loaded by `camel-plan` for migration projects.
> **Purpose:** Template for generating migration-specific implementation tasks.

---

## Migration Task Sequence

Migration projects follow the same basic sequence as greenfield, but add migration-specific tasks. Use the migration ordering from the design spec (leaf routes first, entry-point routes last).

The plan still MUST emit the `yaml plan-metadata` block described in `task-template-greenfield.md` and
`task-decomposition.md`. Migration tasks should use metadata to expose dependencies that are otherwise hidden:
- Java adaptation tasks (Spring Boot/Quarkus only) provide `beans` or processor class names consumed by route YAML
  tasks.
- The standard route task owns the selected inline Groovy or XSLT DataMapper implementation and its metadata; do not
  emit a second migration-specific mapping task.
- Component mapping tasks can provide `routeContracts` consumed by route generation and validation tasks.
- Platform setup tasks provide `properties`, `externalServices`, and dependency readiness consumed by later tasks.

### Additional Migration Tasks

These tasks are added to the standard greenfield sequence:

### Task Template: Copy/Adapt Java Sources (Spring Boot/Quarkus only)

Do not emit this task for Camel Main. If Java processors, beans, or configuration must remain after migration, the
design is not eligible for Camel Main and must return to runtime selection before planning.

```markdown
### Task N: Adapt Java Sources — [flow-name]

**Agent:** migration-specialist

**Files:**
- Create: `[MODULE_DIR]src/main/java/[package]/[ClassName].java`

**Guides to Load:**
- `camel-migrate/guides/camel2-platform-changes.md` (Camel 2.x migrations)
- `camel-implement/guides/orchestrator.md` Step 5.5

**Design Spec Section:** Section 7, Java Sources to Adapt

- [ ] Read source Java file from design spec
- [ ] Apply API changes for Camel 4.x:
  - `javax.*` → `jakarta.*` package changes
  - Deprecated API replacements
  - Updated Processor/Exchange interface methods
- [ ] Adapt package structure to target project
- [ ] Verify: `./mvnw -f [MODULE_DIR]pom.xml -DskipTests compile` exits 0 with `BUILD SUCCESS`; omit the entire
  `[MODULE_DIR]` prefix when the POM is at the project root

**Review:**
- [ ] Spec compliance: all Java sources from spec adapted
- [ ] Code quality: no deprecated API usage, proper package structure
```

### Task Template: Map Vendor Components

```markdown
### Task N: Verify Component Mappings — [flow-name]

**Agent:** migration-specialist

**Files:**
- Reference: design spec Section 7, Component Mapping table

**Guides to Load:**
- `camel-migrate/guides/mule-component-mapping.md` (MuleSoft)
- `camel-migrate/guides/camel2-component-mapping.md` (Camel 2.x)
- `camel-migrate/guides/camel2-eip-mapping.md` (Camel 2.x)
- `camel-migrate/guides/camel2-dataformat-mapping.md` (Camel 2.x)

**MCP Tools:**
- `camel_catalog_component_doc(component="[target-component]", runtime="[runtime]", platformBom="[bom]")`

- [ ] For each component in the mapping table:
  - [ ] Verify target component exists via `camel_catalog_component_doc`
  - [ ] Note exact option names from catalog (may differ from source)
- [ ] If any mapping fails, flag and suggest alternative

**Review:**
- [ ] Spec compliance: all mappings verified
- [ ] All target components verified in catalog
```

### Task Template: Platform Migration Artifacts

```markdown
### Task N: Platform Migration Setup

**Agent:** migration-specialist

**Files:**
- Create/Modify: `[MODULE_DIR]application.properties` (Main only; `camel.jbang.dependencies`)
- Modify: `[MODULE_DIR]pom.xml` (Spring Boot/Quarkus only; scaffold-owned POM, update dependencies/plugins/config)
- Create: platform-specific config files

**Guides to Load:**
- `camel-migrate/guides/camel2-platform-changes.md`
- `camel-implement/guides/properties-generation.md` (Main)
- `camel-implement/guides/maven-dependencies.md` (Spring Boot/Quarkus)

<HARD-RULE>
For Main, do NOT create a POM; write resolved dependencies to `camel.jbang.dependencies` in the module-root
`application.properties`. For Spring Boot/Quarkus, the scaffold task is the sole POM creator. Modify that existing POM
only for design-required dependencies, plugins, and configuration; never create or replace it here.
</HARD-RULE>

**Design Spec Section:** Section 7, Platform Changes

- [ ] For Main, create/update module-root `application.properties` with catalog-verified
  `camel.jbang.dependencies`; do not create `pom.xml`
- [ ] For Spring Boot/Quarkus, read the scaffold-owned `[MODULE_DIR]pom.xml` and add only catalog-verified project
  dependencies plus design-required migration plugins/configuration
- [ ] Convert platform-specific configuration:
  - [OSGi features → `camel.jbang.dependencies` for Main or Maven dependencies for Spring Boot/Quarkus]
  - [Spring XML context → application.properties]
  - [Blueprint beans → supported YAML/inline Groovy for Main or CDI/Spring beans for Spring Boot/Quarkus]
- [ ] For Main, run
  `grep -F 'camel.jbang.dependencies=' [MODULE_DIR]application.properties`; require exit 0 and confirm the matched value
  contains every catalog-verified coordinate
- [ ] For Spring Boot/Quarkus, run `./mvnw -f [MODULE_DIR]pom.xml dependency:tree` (omit the module prefix when the POM
  is at the project root); require exit 0 and confirm the output contains every catalog-verified dependency coordinate

**Review:**
- [ ] Spec compliance: platform changes match spec Section 7
- [ ] Code quality: no POM for Main; valid POM structure and correct BOM usage for Spring Boot/Quarkus
```

---

## Migration Task Ordering

Follow the migration ordering from the design spec:

1. **Scaffold** — project structure; POM with target BOM for Spring Boot/Quarkus only
2. **Platform migration setup** — runtime-specific dependencies and platform config
3. **Component mapping verification** — all target components MCP-verified
4. **Per route (leaf routes first):**
   a. Java source adaptation (if applicable; Spring Boot/Quarkus only)
   b. Route YAML generation, including the approved DataMapper engine and artifacts when applicable
   c. Properties generation
5. **Main run script** — one module-wide task from `task-template-greenfield.md`, Main only, after every route/XSL file
   is known
6. **Docker Compose** — one consolidated task only when the design lists external services
7. **Testing** — one Citrus YAML integration-test task per migrated route

After these tasks, `camel-execute` performs the cross-cutting review and its
internal build-or-smoke plus Citrus verification once. Chained execution then
continues to the final report-only `/camel-validate` phase; neither verification
nor static validation is a separate implementation-plan task.
