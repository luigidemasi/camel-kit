# Task Template — Migration Projects

> **Context:** Loaded by `camel-plan` for migration projects.
> **Purpose:** Template for generating migration-specific implementation tasks.

---

## Migration Task Sequence

Migration projects follow the same basic sequence as greenfield, but add migration-specific tasks. Use the migration ordering from the design spec (leaf routes first, entry-point routes last).

The plan still MUST emit the `yaml plan-metadata` block described in `task-template-greenfield.md` and
`task-decomposition.md`. Migration tasks should use metadata to expose dependencies that are otherwise hidden:
- Java adaptation tasks provide `beans` or processor class names consumed by route YAML tasks.
- DataWeave/XSLT conversion tasks provide `schemas` or `routeContracts` consumed by route YAML tasks.
- Component mapping tasks can provide `routeContracts` consumed by route generation and validation tasks.
- Platform setup tasks provide `properties`, `externalServices`, and dependency readiness consumed by later tasks.

### Additional Migration Tasks

These tasks are added to the standard greenfield sequence:

### Task Template: Copy/Adapt Java Sources

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
- [ ] Verify: `javac` compilation check (if build tool available)

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

### Task Template: Convert DataWeave to XSLT

```markdown
### Task N: Convert DataWeave — [flow-name]

**Agent:** migration-specialist

**Files:**
- Create: `[ROUTE_DIR]kaoto-datamapper-[id].xsl`

**Guides to Load:**
- `camel-migrate/guides/mule-dataweave-conversion.md`
- `camel-migrate/guides/datamapper-migrate.md`
- `shared/datamapper-canonicalize.md`
- `camel-implement/guides/datamapper-approach-[a|b].md`
- `camel-implement/guides/datamapper-validation.md`

**Design Spec Section:** Section 3, Flow: [flow-name], DataMapper subsection

- [ ] Read DataWeave source from design spec
- [ ] Map DataWeave expressions to XPath/XSLT equivalents
- [ ] Pre-compute source XPaths and target elements using canonicalize guide
- [ ] Select XSLT approach (A or B) per design spec
- [ ] Generate XSLT using the appropriate approach guide skeleton
- [ ] Self-validate: XSLT covers all field mappings from spec
- [ ] Verify: `test -f [ROUTE_DIR]kaoto-datamapper-[id].xsl`

**Review:**
- [ ] Spec compliance: all DataWeave mappings covered in XSLT
- [ ] Code quality: valid XSLT, correct XPath expressions
```

### Task Template: Platform Migration Artifacts

```markdown
### Task N: Platform Migration Setup

**Agent:** migration-specialist

**Files:**
- Create/Modify: `pom.xml` (new BOM, NO parent POM, updated dependencies)
- Create: platform-specific config files

**Guides to Load:**
- `camel-migrate/guides/camel2-platform-changes.md`
- `camel-implement/guides/maven-dependencies.md`
- `camel-implement/guides/pom-spring-boot.md` (if Spring Boot target)
- `camel-implement/guides/pom-quarkus.md` (if Quarkus target)

**POM Template Files (MUST READ AND COPY):**
- If Quarkus: `templates/pom-quarkus.xml` — copy verbatim, replace only `[PLACEHOLDER]` values
- If Spring Boot: `templates/pom-spring-boot.xml` — copy verbatim, replace only `[PLACEHOLDER]` values

<HARD-RULE>
Do NOT generate the POM from scratch. COPY the template file and replace ONLY the bracketed placeholders. The template already has the correct groupIds, artifactIds, repositories, and plugins.
</HARD-RULE>

**Design Spec Section:** Section 7, Platform Changes

- [ ] Create new `pom.xml` using the TEMPLATE-COPY approach:
  - If Quarkus: Read the file `templates/pom-quarkus.xml`, copy it verbatim to `pom.xml`
  - If Spring Boot: Read the file `templates/pom-spring-boot.xml`, copy it verbatim to `pom.xml`
  - Replace ONLY these placeholders: `[PROJECT_GROUP_ID]`, `[PROJECT_ARTIFACT_ID]`, `[PROJECT_VERSION]`, `[PROJECT_NAME]`, `[PLATFORM_BOM_VERSION]` (and `[SPRING_BOOT_VERSION]` for Spring Boot)
  - Get `[PLATFORM_BOM_VERSION]` from the design spec header `platformBomVersion` field
  - Do NOT modify any other values in the template (groupIds, artifactIds, repositories, plugins)
  - Add project-specific dependencies in the DEPENDENCIES section
- [ ] Convert platform-specific configuration:
  - [OSGi features → Maven dependencies]
  - [Spring XML context → application.properties]
  - [Blueprint beans → CDI/Spring beans]
- [ ] Verify: `mvn dependency:tree` shows correct dependencies

**Review:**
- [ ] Spec compliance: platform changes match spec Section 7
- [ ] Code quality: valid POM structure, correct BOM usage
```

---

## Migration Task Ordering

Follow the migration ordering from the design spec:

1. **Scaffold** — project structure, POM with target BOM
2. **Platform migration setup** — BOM changes, platform config
3. **Component mapping verification** — all target components MCP-verified
4. **Per route (leaf routes first):**
   a. DataWeave → XSLT conversion (if applicable)
   b. Java source adaptation (if applicable)
   c. Route YAML generation
   d. Properties generation
5. **Docker Compose** — consolidated for all flows
6. **Validation** — all routes
7. **Smoke test**
8. **Testing** — integration tests for migrated routes
