# DataMapper Validation and Metadata Guide

You are acting as a **DataMapper Code Generator**. This guide handles pre-generation validation, post-generation verification, metadata creation, and confirmation. It is the shared guide loaded alongside the approach-specific guide (`datamapper-approach-a.md` or `datamapper-approach-b.md`).

For each DataMapper section you **MUST** complete ALL steps and generate ALL 3 artifacts:

| Artifact | Step | File | Location |
|----------|------|------|----------|
| XSLT stylesheet | Step 3 (approach guide) | `kaoto-datamapper-{id}.xsl` | Project root (JBang) or `src/main/resources/camel/` (Spring Boot/Quarkus) |
| YAML step injection | Step 4 (approach guide) | `{flow-name}.camel.yaml` (step block added) | |
| Kaoto metadata | Step 5 (this guide) | **`.kaoto`** (project root, exactly this name) | Project root |

---

## Step 1: Read Mapping Data from TDD

Read `docs/flows/{flow-name}/{flow-name}.tdd.md` and extract from the `### DataMapper: kaoto-datamapper-{id}` section:

| Field | Description |
|-------|-------------|
| `mapping-id` | Full ID: `kaoto-datamapper-{8hexchars}` |
| `source-type` | `XML_SCHEMA`, `JSON_SCHEMA`, or `Primitive` |
| `source-schema` | File path relative to project root, or `none` |
| `target-type` | `XML_SCHEMA`, `JSON_SCHEMA`, or `Primitive` |
| `target-schema` | File path relative to project root, or `none` |
| `xslt-pattern` | Pre-determined: `A` (XML→XML), `B` (JSON→JSON), `C` (JSON→XML), or `D` (XML→JSON) |
| `xslt-approach` | Pre-determined: `A` (useJsonBody), `B` (header param), or `N/A` |
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

After reading the TDD DataMapper section, perform ALL of the following checks. Fix any issues **before** proceeding to Step 2.

### 1.5a — Field Mappings table must not be empty

Check that the `#### Field Mappings` table contains **at least one data row**.

**If the Field Mappings table is empty or missing:**

```
❌ ERROR: DataMapper section 'kaoto-datamapper-{id}' has no field mappings defined.

The XSLT cannot be generated from an empty mapping table.

Action required:
1. If this is a migration: run the DataWeave conversion analysis to extract
   field mappings from the source DataWeave script, then update the TDD.
2. If this is a greenfield flow: run /camel-flow {flow-name} and complete
   the data transformation interview to define the field mappings.
3. Then re-run /camel-implement {flow-name}.
```

**Stop here — do not generate the XSLT file.**

### 1.5b — Source/Target types must not be Primitive for structured data

Check the **Source** and **Target** type fields. If either is `Primitive` but the Field Mappings table contains dotted paths (e.g., `payload.main.temp`) or array access (e.g., `payload.items[]`), the type is wrong:
- If data is JSON: override to `JSON_SCHEMA`
- If data is XML: override to `XML_SCHEMA`
- Log: `⚠️ Corrected {source|target} type from Primitive to {JSON_SCHEMA|XML_SCHEMA} — field mappings indicate structured data.`

Also check the **XSLT Approach** field. If it says `N/A` but source-type or target-type is `JSON_SCHEMA`, override:
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

## Step 2: Read Pre-Determined XSLT Pattern and Approach

Read the **XSLT Pattern** and **XSLT Approach** from the TDD header — these were pre-determined by `datamapper-canonicalize.md`. Do not re-compute them.

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

**IMPORTANT — `N/A` is only valid when source-type is `XML_SCHEMA`.** If the TDD says `XSLT Approach: N/A` but source-type or target-type is `JSON_SCHEMA`, override accordingly.

**CRITICAL:** Patterns B and D use `method="text"` (NOT `method="xml"`). If source or target is `JSON_SCHEMA`, the template body must use `xml-to-json($mapped-xml)` — never produce an empty `<xsl:template match="/">`.

> **Now load the approach-specific guide:**
> - Approach A or N/A → `datamapper-approach-a.md` (Steps 3, 4)
> - Approach B → `datamapper-approach-b.md` (Steps 3, 4)
>
> **Return here after completing Steps 3 and 4.**

---

## Step 3.5: Verify Generated XSLT Against TDD — MANDATORY

After generating the XSLT file (Step 3 in approach guide), walk through **every row** in the TDD Field Mappings table and verify the generated XSLT contains a matching element.

**For each field mapping row, check:**

| Check | What to verify |
|-------|----------------|
| Completeness | The TDD row has a corresponding element in the XSLT |
| Source XPath | The `select="..."` attribute matches the TDD **Source XPath** column |
| Target Element | The XSLT element tag/key matches the TDD **Target Element** column |
| Type consistency | `fn:string`/`fn:number`/`fn:boolean` matches the source field type |
| Approach purity | No `xsl:param` when Approach is A; no `useJsonBody` when Approach is B |
| No `json-to-xml()` in Approach A | When Approach = A, the XSLT MUST NOT contain any call to `json-to-xml()` |

**Present the verification result:**

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
XSLT VERIFICATION AGAINST TDD
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
| TDD Row | Source XPath Match | Target Element Match | Status |
|---|---|---|---|
| orderId → orderId | ✅ | ✅ | OK |
| main.temp → temperature | ✅ | ✅ | OK |
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**If any row shows ❌:** fix the XSLT and re-verify before proceeding.

### Step 3.5b: Verify Route YAML After Step 4 — MANDATORY

After injecting the Camel YAML step (Step 4), verify the route YAML matches the XSLT Approach:

| Approach | Route YAML must contain | Route YAML must NOT contain |
|---|---|---|
| A (useJsonBody) | `useJsonBody: true` in the `parameters:` block | `setHeader`/`setBody` before the step |
| B (header param) | `setHeader` + `setBody` before the step | `useJsonBody: true` |
| N/A | (no special params) | `useJsonBody: true` |

**Missing `useJsonBody: true` for Approach A is a fatal error** — Saxon receives raw JSON and fails with `Content is not allowed in prolog`.

---

## Step 5: Create or Update `.kaoto` Metadata File — MANDATORY

**CRITICAL — Filename and format rules:**

| Rule | Correct | WRONG (do NOT do this) |
|------|---------|------------------------|
| Filename | `.kaoto` | ~~`kaoto-datamapper-{id}.kaoto`~~, ~~`{flow-name}.kaoto`~~ |
| Location | Project root (same directory as `.camel.yaml`) | NOT in `.camel-kit/` |
| Format | Kaoto's internal JSON format (see template below) | NOT a custom JSON schema |
| One file | Single `.kaoto` file for ALL DataMapper mappings in the project | NOT one file per mapping |

**If `.kaoto` does not exist:** create it. **If it exists:** read it, add the new key, write back.

**CRITICAL — Kaoto `type` values are display strings, NOT enum keys:**

| TDD type | `.kaoto` type value |
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
- `sourceParameters` is `{}` if no parameters defined in TDD
- Always include the three base namespace entries: `xs`, `fn`, `xsl`
- Add `ns0` (and further prefixes) for each XML namespace from the TDD namespace map
- All file paths are relative to the project root
- Write with tab indentation (matching Kaoto's format)
- **Never overwrite existing keys** — only append the new key

---

## Step 6: Maven Dependency

Check `pom.xml`. If `camel-xslt-saxon` is not already declared, add it:

```xml
<dependency>
  <groupId>org.apache.camel</groupId>
  <artifactId>camel-xslt-saxon</artifactId>
</dependency>
```

---

## Step 7: Confirm and Return

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
DATAMAPPER ARTIFACTS GENERATED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ kaoto-datamapper-{id}.xsl    ({output-dir})
✅ {flow-name}.camel.yaml       (step injected)
✅ .kaoto                       (key added: kaoto-datamapper-{id})
✅ XSLT verified against TDD    ({N}/{N} fields matched)

Pattern:        {A | B | C | D} ({source-format} → {target-format})
Approach:       {A (useJsonBody) | B (header param) | N/A}
Fields mapped:  {N}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**Return control to `camel-implement`** to continue generating the rest of the route.

---

## Error Handling

**Missing DataMapper section in TDD:**
```
❌ ERROR: No DataMapper section found in TDD for flow '{flow-name}'.

Run /camel-flow {flow-name} to complete the mapping interview first.
```

**Schema file not found at declared path:**
```
⚠️ WARNING: Schema file '{path}' not found at project root.

The .kaoto metadata will reference the expected path.
Place the schema file at the project root before opening in Kaoto IDE.
Generating XSLT with best-effort field paths based on field names from TDD.
```

**Existing key conflict in `.kaoto`:**
```
⚠️ WARNING: Key 'kaoto-datamapper-{id}' already exists in .kaoto.
Overwriting with updated mapping definition.
```
