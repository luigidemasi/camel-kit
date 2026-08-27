# DataMapper Validation and Metadata Guide

You are acting as a **DataMapper Code Generator**. This guide handles pre-generation validation, post-generation verification, metadata creation, and confirmation. It is the shared guide loaded alongside the engine-specific guide — either an XSLT approach guide (`datamapper-approach-a.md` or `datamapper-approach-b.md`) or the Groovy guide (`datamapper-groovy.md`).

The transformation engine is determined by the flow design's `**Transformation Engine:**` header (set during
canonicalization). If the header says `Groovy (inline)` → load `datamapper-groovy.md`. If the header is absent or says
`XSLT` → load the XSLT approach guide.

**For XSLT engine**, you **MUST** complete ALL steps and generate ALL 3 artifacts:

| Artifact | Step | File | Location |
|----------|------|------|----------|
| XSLT stylesheet | Step 3 (approach guide) | `kaoto-datamapper-{id}.xsl` | Runtime-aware `ROUTE_DIR` from the orchestrator |
| YAML step injection | Step 4 (approach guide) | `{flow-name}.camel.yaml` (step block added) | |
| Kaoto metadata | Step 5 (this guide) | **`.kaoto`** (project root, exactly this name) | Project root |

**For Groovy engine**, generate only 1 artifact (the inline script is part of the YAML):

| Artifact | Step | File | Location |
|----------|------|------|----------|
| YAML step with inline Groovy | Step 3 + 4 (groovy guide) | `{flow-name}.camel.yaml` (transform block added) | |

---

## Step 1: Read Mapping Data from the Design Spec

Read `.camel-kit/pipeline.json` to resolve `<PIPELINE_ID>`, then read
`docs/camel-kit/<PIPELINE_ID>/design-spec.md`. Extract the `### DataMapper: kaoto-datamapper-{id}` section from the
relevant `### Flow: {flow-name}` design:

| Field | Description |
|-------|-------------|
| `mapping-id` | Full ID: `kaoto-datamapper-{8hexchars}` |
| `transformation-engine` | `Groovy (inline)` if present, otherwise XSLT (default) |
| `source-type` | `XML_SCHEMA`, `JSON_SCHEMA`, or `Primitive` |
| `source-schema` | File path relative to project root, or `none` |
| `target-type` | `XML_SCHEMA`, `JSON_SCHEMA`, or `Primitive` |
| `target-schema` | File path relative to project root, or `none` |
| `format-pair` | *(Groovy only)* Format pair string, e.g., `JSON → JSON` |
| `xslt-pattern` | *(XSLT only)* Pre-determined: `A` (XML→XML), `B` (JSON→JSON), `C` (JSON→XML), or `D` (XML→JSON) |
| `xslt-approach` | *(XSLT only)* Pre-determined: `A` (useJsonBody), `B` (header param), or `N/A` |
| `source-parameters` | Table of name → type + schema path |
| `namespace-map` | Table of prefix → URI |
| `field-mappings` | Enriched field mapping table with **Source XPath** and **Target Element** columns |
| `conditional-mappings` | Conditional mapping table (may be absent) |
| `collection-mappings` | Collection mapping table (may be absent) |

The enriched Field Mappings table has 8 columns:

```
| Source Field | Src Type | Source XPath | Target Field | Tgt Type | Target Element | Transformation | How |
```

The **Source XPath** and **Target Element** columns are pre-computed by `skills/shared/datamapper-canonicalize.md` during the flow design or migration phase. Use them directly when generating the XSLT — do not re-derive them.

---

## Step 1.5: Validate Mapping Data — MANDATORY before proceeding

After reading the DataMapper section, perform the applicable checks below. Fix any issues **before** proceeding to
Step 2.

**For Groovy engine:** perform only checks 1.5a and 1.5b. Skip 1.5c, 1.5d, and 1.5e (these validate XSLT-specific columns that don't exist in the Groovy mapping format).

### 1.5a — Field Mappings table must not be empty

Check that the `#### Field Mappings` table contains **at least one data row**.

**If the Field Mappings table is empty or missing:**

```
❌ ERROR: DataMapper section 'kaoto-datamapper-{id}' has no field mappings defined.

The transformation cannot be generated from an empty mapping table.

Action required:
1. If this is a migration: run the DataWeave conversion analysis to extract
   field mappings from the source DataWeave script, then update the flow design.
2. If this is a greenfield flow: return to `camel-brainstorm` and complete
   the data transformation interview to define the field mappings.
3. Then re-run the affected `camel-execute` task.
```

**Stop here — do not generate the transformation.**

### 1.5b — Source/Target types must not be Primitive for structured data

Check the **Source** and **Target** type fields. If either is `Primitive` but the Field Mappings table contains dotted paths (e.g., `payload.main.temp`) or array access (e.g., `payload.items[]`), the type is wrong:
- If data is JSON: override to `JSON_SCHEMA`
- If data is XML: override to `XML_SCHEMA`
- Log: `⚠️ Corrected {source|target} type from Primitive to {JSON_SCHEMA|XML_SCHEMA} — field mappings indicate structured data.`

**XSLT only — skip this sub-check when engine = Groovy.** Also check the **XSLT Approach** field. If it says `N/A` but source-type or target-type is `JSON_SCHEMA`, override:
- No source parameters → Approach A
- Source parameters exist → Approach B
- Log: `⚠️ Corrected XSLT Approach from N/A to {A|B} — JSON_SCHEMA requires JSON handling.`

Then recompute XSLT Pattern from the corrected types using the table below.

### 1.5c — XSLT Pattern must match source/target types

Verify the **XSLT Pattern** field is consistent with the (possibly corrected) **Source** and **Target** types:

| Source type | Target type | Correct Pattern |
|---|---|---|
| XML_SCHEMA | XML_SCHEMA | A |
| JSON_SCHEMA | JSON_SCHEMA | **B** |
| JSON_SCHEMA | XML_SCHEMA | C |
| XML_SCHEMA | JSON_SCHEMA | D |

**If mismatch:** override and log a warning.

### 1.5d — Source XPaths must use the correct format for the source type

Check the **Source XPath** column in the Field Mappings table:

- **JSON_SCHEMA source, Approach A:** Every absolute Source XPath MUST start with `/fn:map/` and use `fn:string[@key='...']`, `fn:number[@key='...']`, `fn:boolean[@key='...']`, `fn:map[@key='...']`, or `fn:array[@key='...']` segments.

- **JSON_SCHEMA source, Approach B:** Every absolute Source XPath MUST start with `$body-x/fn:map/` (or `${param-name}-x/fn:map/`).

- **XML_SCHEMA source:** XPaths should use namespace prefixes (e.g., `/ns0:Root/ns0:field`).

**If Source XPaths are wrong:** Recompute them using the rules from `skills/shared/datamapper-canonicalize.md` Step 2.

### 1.5e — Target Elements must use the correct format for the target type

Check the **Target Element** column:

- **JSON_SCHEMA target:** Each Target Element MUST use the lossless XML format: `<string key="{field}">`, `<number key="{field}">`, `<boolean key="{field}">`, `<map key="{field}">`, or `<array key="{field}">`.

- **XML_SCHEMA target:** Each Target Element should be an XML element name, optionally with namespace.

**If Target Elements are wrong:** Recompute them using the rules from `skills/shared/datamapper-canonicalize.md` Step 3.

---

## Step 2: Route to Engine-Specific Guide

Read the **Transformation Engine** from the flow design header (set during canonicalization in
`datamapper-canonicalize.md`).

### If Transformation Engine = Groovy (inline)

Read the **Format Pair** from the flow design header (e.g., `JSON → JSON`). Load `datamapper-groovy.md` (Steps 3, 4).
Return here after completing Steps 3 and 4.

### If Transformation Engine = XSLT (or absent — default)

Read the **XSLT Pattern** and **XSLT Approach** from the flow design header — these were pre-determined by
`datamapper-canonicalize.md`. Do not re-compute them.

| Pattern | Format pair | `xsl:output method` |
|---------|-------------|---------------------|
| A | XML→XML | `xml` |
| B | JSON→JSON | `text` |
| C | JSON→XML | `xml` |
| D | XML→JSON | `text` |

| Approach | Meaning |
|----------|---------|
| A | `useJsonBody: true` — Camel converts JSON body to lossless XML before Saxon starts |
| B | Manual header param — JSON string in Exchange header, body set to `<root/>` |
| N/A | Source is XML — no JSON handling needed |

**IMPORTANT — `N/A` is only valid when source-type is `XML_SCHEMA`.** If the flow design says `XSLT Approach: N/A`
but source-type or target-type is `JSON_SCHEMA`, override accordingly.

**CRITICAL:** Patterns B and D use `method="text"` (NOT `method="xml"`). If source or target is `JSON_SCHEMA`, the template body must use `xml-to-json($mapped-xml)` — never produce an empty `<xsl:template match="/">`.

> **Now load the approach-specific guide:**
> - Approach A or N/A → `datamapper-approach-a.md` (Steps 3, 4)
> - Approach B → `datamapper-approach-b.md` (Steps 3, 4)
>
> **Return here after completing Steps 3 and 4.**

---

## Step 3.5: Verify Generated Code Against the Design Spec — MANDATORY

After generating the transformation code (Step 3 in engine-specific guide), walk through **every row** in the design
spec Field Mappings table and verify the generated code contains a matching element.

### XSLT Verification

**For each field mapping row, check:**

| Check | What to verify |
|-------|----------------|
| Completeness | The design spec row has a corresponding element in the XSLT |
| Source XPath | The `select="..."` attribute matches the design spec **Source XPath** column |
| Target Element | The XSLT element tag/key matches the design spec **Target Element** column |
| Type consistency | `fn:string`/`fn:number`/`fn:boolean` matches the source field type |
| Approach purity | No `xsl:param` when Approach is A; no `useJsonBody` when Approach is B |
| No `json-to-xml()` in Approach A | When Approach = A, the XSLT MUST NOT contain any call to `json-to-xml()` |

**Present the verification result:**

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
XSLT VERIFICATION AGAINST DESIGN SPEC
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
| Design Spec Row | Source XPath Match | Target Element Match | Status |
|---|---|---|---|
| orderId → orderId | ✅ | ✅ | OK |
| main.temp → temperature | ✅ | ✅ | OK |
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**If any row shows ❌:** fix the XSLT and re-verify before proceeding.

### Groovy Verification

**For each field mapping row, check:**

| Check | What to verify |
|-------|----------------|
| Completeness | The design spec row has a corresponding line in the Groovy script |
| Source navigation | The Groovy dot-notation path matches the design spec **Source Field** (e.g., `src.main?.temp` for `payload.main.temp`) |
| Target key/element | The Groovy map key or XML element name matches the design spec **Target Field** |
| Parser | JSON source → `JsonSlurper`, XML source → `XmlSlurper` |
| Serializer | JSON target → `JsonOutput.toJson(result)`, XML target → `writer.toString()` |
| Collections | Each design spec collection mapping uses `collect` (JSON) or iteration (XML) |
| Conditionals | Each design spec conditional mapping uses ternary or switch |

**Present the verification result:**

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
GROOVY VERIFICATION AGAINST DESIGN SPEC
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
| Design Spec Row | Source Navigation | Target Key/Element | Status |
|---|---|---|---|
| orderId → orderId | ✅ src.orderId | ✅ orderId | OK |
| main.temp → temperature | ✅ src.main?.temp | ✅ temperature | OK |
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**If any row shows ❌:** fix the Groovy script and re-verify before proceeding.

### Step 3.5b: Verify Route YAML After Step 4 — MANDATORY

After injecting the Camel YAML step (Step 4), verify the route YAML matches the engine and approach.

**For XSLT engine:**

| Approach | Route YAML must contain | Route YAML must NOT contain |
|---|---|---|
| A (useJsonBody) | `useJsonBody: true` in the `parameters:` block | `setHeader`/`setBody` before the step |
| B (header param) | `setHeader` + `setBody` before the step | `useJsonBody: true` |
| N/A | (no special params) | `useJsonBody: true` |

**Missing `useJsonBody: true` for Approach A is a fatal error** — Saxon receives raw JSON and fails with `Content is not allowed in prolog`.

**For Groovy engine:**

| Check | What to verify |
|---|---|
| Step type | Uses `transform:` with `groovy:` expression — NOT `to: xslt-saxon:` |
| No pre-steps | No `setHeader`/`setBody` before the transform (Groovy reads body directly) |
| Block scalar | Groovy script uses `\|` (literal block) for multi-line YAML |
| ID | `id: kaoto-datamapper-{id}` matches the design spec mapping ID |

---

## Step 5: Create or Update `.kaoto` Metadata File — XSLT Only

**If Transformation Engine = Groovy:** skip this step entirely. Kaoto IDE's DataMapper visual editor works with XSLT files — it has no understanding of inline Groovy scripts. The `.kaoto` metadata references an `xsltPath` which doesn't exist for Groovy mappings. Proceed directly to Step 6.

**If Transformation Engine = XSLT (or absent):** this step is MANDATORY.

**CRITICAL — Filename and format rules:**

| Rule | Correct | WRONG (do NOT do this) |
|------|---------|------------------------|
| Filename | `.kaoto` | ~~`kaoto-datamapper-{id}.kaoto`~~, ~~`{flow-name}.kaoto`~~ |
| Location | Project root | NOT in `.camel-kit/` or a target module |
| Format | Kaoto's internal JSON format (see template below) | NOT a custom JSON schema |
| One file | Single `.kaoto` file for ALL DataMapper mappings in the project | NOT one file per mapping |

**If `.kaoto` does not exist:** create it. **If it exists:** read it, add the new key, write back.

**CRITICAL — Kaoto `type` values are display strings, NOT enum keys:**

| Design spec type | `.kaoto` type value |
|----------|---------------------|
| `XML_SCHEMA` | `"XML Schema"` |
| `JSON_SCHEMA` | `"JSON Schema"` |
| `Primitive` | `"Primitive"` |

**Use this EXACT structure:**

```json
{
	"kaoto-datamapper-{id}": {
		"xsltPath": "kaoto-datamapper-{id}.xsl",
		"sourceBody": {
			"type": "{XML Schema | JSON Schema | Primitive}",
			"filePath": ["{source-schema-path}"],
			"fieldTypeOverrides": [],
			"choiceSelections": []
		},
		"targetBody": {
			"type": "{XML Schema | JSON Schema | Primitive}",
			"filePath": ["{target-schema-path}"],
			"fieldTypeOverrides": [],
			"choiceSelections": []
		},
		"sourceParameters": {
			"{param-name}": {
				"type": "{XML Schema | JSON Schema | Primitive}",
				"filePath": ["{param-schema-path}"],
				"fieldTypeOverrides": [],
				"choiceSelections": []
			}
		},
		"namespaceMap": {
			"xs": "http://www.w3.org/2001/XMLSchema",
			"fn": "http://www.w3.org/2005/xpath-functions",
			"xsl": "http://www.w3.org/1999/XSL/Transform",
			"ns0": "{source-or-target-namespace-URI}"
		}
	}
}
```

**The ONLY keys allowed in a mapping entry are:** `xsltPath`, `sourceBody`, `targetBody`, `sourceParameters`, `namespaceMap`.

**Rules:**
- `filePath` is `[]` when no schema file exists
- `sourceParameters` is `{}` if no parameters are defined in the design spec
- Always include the three base namespace entries: `xs`, `fn`, `xsl`
- Add `ns0` (and further prefixes) for each XML namespace from the design spec namespace map
- All file paths are relative to the project root
- Write with tab indentation (matching Kaoto's format)
- **Never overwrite existing keys** — only append the new key

---

## Step 6: Runtime Dependency

### XSLT Engine

Declare the XSLT Saxon dependency in the selected runtime's dependency file:

| Runtime | Coordinate | Declared In |
|---------|------------|-------------|
| Spring Boot | `org.apache.camel.springboot:camel-xslt-saxon-starter` | module `pom.xml` |
| Quarkus | `org.apache.camel.quarkus:camel-quarkus-xslt-saxon` | module `pom.xml` |
| Main | `org.apache.camel:camel-xslt-saxon` | module-root `application.properties` as `camel.jbang.dependencies` |

Main never creates or checks a POM and does not rely on URI auto-discovery for this contract.

### Groovy Engine

Check the appropriate location for the Groovy language dependency:

| Runtime | GroupId | ArtifactId | Declared In |
|---------|---------|------------|-------------|
| Spring Boot | `org.apache.camel.springboot` | `camel-groovy-starter` | `pom.xml` `<dependencies>` |
| Quarkus | `org.apache.camel.quarkus` | `camel-quarkus-groovy` | `pom.xml` `<dependencies>` |
| JBang | `org.apache.camel` | `camel-groovy` | `application.properties` as `camel.jbang.dependencies` |

**Spring Boot example:**
```xml
<dependency>
  <groupId>org.apache.camel.springboot</groupId>
  <artifactId>camel-groovy-starter</artifactId>
</dependency>
```

**Quarkus example:**
```xml
<dependency>
  <groupId>org.apache.camel.quarkus</groupId>
  <artifactId>camel-quarkus-groovy</artifactId>
</dependency>
```

**JBang example** (in `application.properties`):
```properties
camel.jbang.dependencies=org.apache.camel:camel-groovy
```

**Why JBang needs explicit declaration:** JBang auto-discovers Camel components from `to:` URIs in the YAML route (e.g., `to: xslt-saxon:file.xsl` triggers `camel-xslt-saxon`). However, inline Groovy in a `transform:` expression block uses the Groovy *language*, not a Camel *component URI*. JBang's URI auto-discovery does not detect this, so an explicit `camel.jbang.dependencies` entry is needed.

**No version tags** for Spring Boot/Quarkus — BOMs manage versions.

---

## Step 7: Confirm and Return

### XSLT Confirmation

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
DATAMAPPER ARTIFACTS GENERATED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ kaoto-datamapper-{id}.xsl    ({output-dir})
✅ {flow-name}.camel.yaml       (step injected)
✅ .kaoto                       (key added: kaoto-datamapper-{id})
✅ XSLT verified against design spec    ({N}/{N} fields matched)

Engine:         XSLT (camel-xslt-saxon)
Pattern:        {A | B | C | D} ({source-format} → {target-format})
Approach:       {A (useJsonBody) | B (header param) | N/A}
Fields mapped:  {N}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### Groovy Confirmation

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
DATAMAPPER ARTIFACTS GENERATED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ {flow-name}.camel.yaml       (inline Groovy transform injected)
✅ Groovy verified against design spec  ({N}/{N} fields matched)

Engine:         Groovy (inline)
Format Pair:    {source-format} → {target-format}
Fields mapped:  {N}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**Return control to `camel-implement`** to continue generating the rest of the route.

---

## Error Handling

**Missing DataMapper section in design spec:**
```
❌ ERROR: No DataMapper section found in the design spec for flow '{flow-name}'.

Return to `camel-brainstorm` and complete the mapping interview for this flow first.
```

**Schema file not found at declared path:**
```
⚠️ WARNING: Schema file '{path}' not found at project root.

The .kaoto metadata will reference the expected path.
Place the schema file at the project root before opening in Kaoto IDE.
Generating XSLT with best-effort field paths based on field names from the design spec.
```

**Existing key conflict in `.kaoto`:**
```
⚠️ WARNING: Key 'kaoto-datamapper-{id}' already exists in .kaoto.
Overwriting with updated mapping definition.
```
