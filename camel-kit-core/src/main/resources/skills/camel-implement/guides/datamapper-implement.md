# DataMapper Implement Guide

You are now acting as a **DataMapper Code Generator**. This guide is loaded by `camel-implement` for each `### DataMapper: kaoto-datamapper-{id}` section found in the TDD.

For each DataMapper section you **MUST** complete ALL 7 steps and generate ALL 3 artifacts before returning control to `camel-implement`:

| Artifact | Step | File | WRONG names (do NOT use) |
|----------|------|------|--------------------------|
| XSLT stylesheet | Step 3 | `kaoto-datamapper-{id}.xsl` (project root) | |
| YAML step injection | Step 4 | `{flow-name}.camel.yaml` (step block added) | |
| Kaoto metadata | Step 5 | **`.kaoto`** (project root, exactly this name) | ~~`kaoto-datamapper-{id}.kaoto`~~, ~~`{name}.kaoto`~~ |

**Do NOT return to `camel-implement` until all 3 artifacts are generated and Step 7 confirmation is displayed.**

---

## Step 1: Read Mapping Data from TDD

Read `.camel-kit/flows/{flow-name}/{flow-name}.tdd.md` and extract from the `### DataMapper: kaoto-datamapper-{id}` section:

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

Check that the `#### Field Mappings` table contains **at least one data row** (a row with actual source and target fields, not just the header row).

**If the Field Mappings table is empty or missing:**

```
❌ ERROR: DataMapper section 'kaoto-datamapper-{id}' has no field mappings defined.

The XSLT cannot be generated from an empty mapping table — an empty skeleton
would be functionally useless and cannot be fixed by Kaoto IDE alone.

Action required:
1. If this is a migration: load 'guides/datamapper-migrate.md' and run
   the DataWeave conversion analysis to extract field mappings from the
   source DataWeave script, then update the TDD.
2. If this is a greenfield flow: run /camel-flow {flow-name} and complete
   the data transformation interview to define the field mappings.
3. Then re-run /camel-implement {flow-name}.
```

**Stop here — do not generate the XSLT file.**

**If Conditional Mappings and Collection Mappings tables are also empty:**
That is acceptable — they are optional. Only Field Mappings is required.

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

Verify the **XSLT Pattern** field is consistent with the (possibly corrected) **Source** and **Target** types. The correct mapping is:

| Source type | Target type | Correct Pattern |
|---|---|---|
| XML_SCHEMA | XML_SCHEMA | A |
| JSON_SCHEMA | JSON_SCHEMA | **B** |
| JSON_SCHEMA | XML_SCHEMA | C |
| XML_SCHEMA | JSON_SCHEMA | D |

**If the TDD Pattern does not match the table above** (e.g., TDD says `D` but source and target are both `JSON_SCHEMA`): override the TDD value and use the correct pattern from the table. Log a warning:

```
⚠️ TDD Pattern override: TDD says '{wrong}' but source={source-type}, target={target-type} → using Pattern {correct}.
```

### 1.5d — Source XPaths must use the correct format for the source type

Check the **Source XPath** column in the Field Mappings table:

- **JSON_SCHEMA source, Approach A:** Every absolute Source XPath MUST start with `/fn:map/` and use `fn:string[@key='...']`, `fn:number[@key='...']`, `fn:boolean[@key='...']`, `fn:map[@key='...']`, or `fn:array[@key='...']` segments. If the XPaths use plain paths (e.g., `/name`, `/main/temp`) instead of the lossless XML format, they are wrong.

- **JSON_SCHEMA source, Approach B:** Every absolute Source XPath MUST start with `$body-x/fn:map/` (or `${param-name}-x/fn:map/`).

- **XML_SCHEMA source:** XPaths should use namespace prefixes (e.g., `/ns0:Root/ns0:field`).

**If Source XPaths are wrong:** Recompute them using the rules from `skills/shared/datamapper-canonicalize.md` Step 2. Use the `Source Field` and `Src Type` columns to derive the correct XPaths.

### 1.5e — Target Elements must use the correct format for the target type

Check the **Target Element** column in the Field Mappings table:

- **JSON_SCHEMA target:** Each Target Element MUST use the lossless XML format: `<string key="{field}">`, `<number key="{field}">`, `<boolean key="{field}">`, `<map key="{field}">`, or `<array key="{field}">`. If they show plain names (e.g., `city`, `temperature`), they are wrong.

- **XML_SCHEMA target:** Each Target Element should be an XML element name, optionally with namespace.

**If Target Elements are wrong:** Recompute them using the rules from `skills/shared/datamapper-canonicalize.md` Step 3. Use the `Target Field` and `Tgt Type` columns.

---

## Step 2: Read Pre-Determined XSLT Pattern and Approach

Read the **XSLT Pattern** and **XSLT Approach** from the TDD header — these were pre-determined by `datamapper-canonicalize.md` during the flow design or migration phase. Do not re-compute them.

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

**IMPORTANT — `N/A` is only valid when source-type is `XML_SCHEMA`.** If the TDD says `XSLT Approach: N/A` but source-type or target-type is `JSON_SCHEMA`, this is a TDD error. Override: use Approach A if there are no source parameters, or Approach B if source parameters exist. Log a warning:

```
⚠️ TDD Approach override: TDD says 'N/A' but source/target is JSON_SCHEMA → using Approach {A|B}.
```

**CRITICAL:** Patterns B and D use `method="text"` (NOT `method="xml"`). If source or target is `JSON_SCHEMA`, the template body must use `xml-to-json($mapped-xml)` — never produce an empty `<xsl:template match="/">`. An empty template is always wrong.

---

## Step 3: Generate XSLT File

Create `kaoto-datamapper-{id}.xsl` in the **project root**.

**Always start with the Kaoto header:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- This file is generated by Kaoto DataMapper. Do not edit. -->
```

**Always declare namespace prefixes from the TDD namespace map on the `xsl:stylesheet` root element.**

**IMPORTANT — use pre-computed values from the TDD:** For each field mapping row, use the **Source XPath** column directly in `select="..."` attributes and the **Target Element** column directly as the wrapping element. Do not re-derive XPaths from semantic field paths — the canonical values are already computed.

---

### Pattern A: XML → XML

Use when source-type = `XML_SCHEMA` and target-type = `XML_SCHEMA`.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- This file is generated by Kaoto DataMapper. Do not edit. -->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:ns0="{source-namespace-URI}">
  <xsl:output method="xml" indent="yes"/>
  <xsl:template match="/">
    <{TargetRootElement} xmlns="{target-namespace-URI}">

      <!-- Attribute mappings -->
      <xsl:attribute name="{attr-name}">
        <xsl:value-of select="/ns0:{SourceRoot}/@{attr-name}"/>
      </xsl:attribute>

      <!-- Direct field mappings -->
      <{TargetField}>
        <xsl:value-of select="/ns0:{SourceRoot}/ns0:{SourceField}"/>
      </{TargetField}>

      <!-- Container (copy-of) mappings -->
      <{TargetContainer} xmlns="">
        <xsl:copy-of select="/ns0:{SourceRoot}/{SourceContainer}"/>
      </{TargetContainer}>

      <!-- for-each collection mappings -->
      <xsl:for-each select="/ns0:{SourceRoot}/{SourceCollection}">
        <{TargetItem} xmlns="">
          <{TargetField}><xsl:value-of select="{SourceField}"/></{TargetField}>
        </{TargetItem}>
      </xsl:for-each>

      <!-- xsl:if conditional mappings -->
      <xsl:if test="{condition}">
        <{TargetField}><xsl:value-of select="{expression}"/></{TargetField}>
      </xsl:if>

      <!-- xsl:choose conditional mappings -->
      <xsl:choose>
        <xsl:when test="{condition}">
          <{TargetField}><xsl:value-of select="{trueExpression}"/></{TargetField}>
        </xsl:when>
        <xsl:otherwise>
          <{TargetField}><xsl:value-of select="{falseExpression}"/></{TargetField}>
        </xsl:otherwise>
      </xsl:choose>

    </{TargetRootElement}>
  </xsl:template>
</xsl:stylesheet>
```

**Rules for Pattern A:**
- Use `xsl:value-of` for scalar fields
- Use `xsl:copy-of` for container/object fields mapped wholesale
- Use `xsl:attribute` before any child elements
- Fields inside `xsl:for-each` use relative paths (no `/ns0:{Root}/` prefix)
- Namespace-less child elements inside a namespaced root: add `xmlns=""` to reset

---

### Pattern B: JSON → JSON

Use when XSLT Pattern = `B` in the TDD. **Check the XSLT Approach field in the TDD to select the correct skeleton below.**

---

#### Pattern B — Approach A (useJsonBody: true)

**Use when TDD says `XSLT Approach: A (useJsonBody)`.**

Camel converts the JSON body to lossless XML before Saxon starts. The XSLT receives lossless XML as its input document. **Do NOT declare any `xsl:param` for the body.** Navigate using absolute XPath from `/fn:map/...`.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- This file is generated by Kaoto DataMapper. Do not edit. -->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fn="http://www.w3.org/2005/xpath-functions">
  <xsl:output method="text" indent="yes"/>

  <!-- NO xsl:param — Camel already converted the body to lossless XML -->

  <!-- Build target in lossless XML format -->
  <xsl:variable name="mapped-xml">
    <map xmlns="http://www.w3.org/2005/xpath-functions">

      <!-- For each TDD row: use Target Element as wrapper, Source XPath in select -->
      <{Target Element}>
        <xsl:value-of select="{Source XPath}"/>
      </{Target Element}>

    </map>
  </xsl:variable>

  <xsl:template match="/">
    <xsl:value-of select="xml-to-json($mapped-xml)"/>
  </xsl:template>
</xsl:stylesheet>
```

**Concrete example — Approach A with pre-computed TDD values:**

Given TDD rows:

| Source Field | Src Type | Source XPath | Target Field | Tgt Type | Target Element | Transformation | How |
|---|---|---|---|---|---|---|---|
| payload.name | string | `/fn:map/fn:string[@key='name']` | city | string | `<string key="city">` | Direct copy | Auto |
| payload.weather[0].main | string | `/fn:map/fn:array[@key='weather']/fn:map[1]/fn:string[@key='main']` | condition | string | `<string key="condition">` | Direct copy | Auto |
| payload.main.temp | number | `/fn:map/fn:map[@key='main']/fn:number[@key='temp']` | temperature | number | `<number key="temperature">` | `format-number(_, '##.##')` | Manual |
| (computed) | boolean | (conditional) | alert | boolean | `<boolean key="alert">` | Conditional expression | Manual |

Produces:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- This file is generated by Kaoto DataMapper. Do not edit. -->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fn="http://www.w3.org/2005/xpath-functions">
  <xsl:output method="text" indent="yes"/>

  <xsl:variable name="mapped-xml">
    <map xmlns="http://www.w3.org/2005/xpath-functions">
      <string key="city">
        <xsl:value-of select="/fn:map/fn:string[@key='name']"/>
      </string>
      <string key="condition">
        <xsl:value-of select="/fn:map/fn:array[@key='weather']/fn:map[1]/fn:string[@key='main']"/>
      </string>
      <number key="temperature">
        <xsl:value-of select="format-number(
          /fn:map/fn:map[@key='main']/fn:number[@key='temp'], '##.##')"/>
      </number>
      <boolean key="alert">
        <xsl:variable name="cond"
          select="upper-case(/fn:map/fn:array[@key='weather']/fn:map[1]/fn:string[@key='main'])"/>
        <xsl:variable name="temp"
          select="/fn:map/fn:map[@key='main']/fn:number[@key='temp']"/>
        <xsl:choose>
          <xsl:when test="$cond = 'RAIN' or $temp > 35.0
              or ($cond = 'CLEAR' and $temp > 37.0)">true</xsl:when>
          <xsl:otherwise>false</xsl:otherwise>
        </xsl:choose>
      </boolean>
    </map>
  </xsl:variable>

  <xsl:template match="/">
    <xsl:value-of select="xml-to-json($mapped-xml)"/>
  </xsl:template>
</xsl:stylesheet>
```

---

#### Pattern B — Approach B (manual header param)

**Use when TDD says `XSLT Approach: B (header param)`.**

The body is set to `<root/>`. The JSON string is stored in a Camel Exchange header whose name matches the XSLT `xsl:param` name. Each JSON source gets its own `xsl:param` + `json-to-xml()` variable.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- This file is generated by Kaoto DataMapper. Do not edit. -->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fn="http://www.w3.org/2005/xpath-functions">
  <xsl:output method="text" indent="yes"/>

  <!-- Declare xsl:param for each JSON source — name must match the Camel Exchange header -->
  <xsl:param name="{source-param-name}"/>
  <xsl:variable name="{source-param-name}-x" select="json-to-xml(${source-param-name})"/>

  <!-- Additional source parameters (from TDD Source Parameters table) -->
  <xsl:param name="{extra-param}"/>
  <xsl:variable name="{extra-param}-x" select="json-to-xml(${extra-param})"/>

  <!-- Build target in lossless XML format -->
  <xsl:variable name="mapped-xml">
    <map xmlns="http://www.w3.org/2005/xpath-functions">

      <!-- For each TDD row: use Target Element as wrapper, Source XPath in select -->
      <{Target Element}>
        <xsl:value-of select="{Source XPath}"/>
      </{Target Element}>

    </map>
  </xsl:variable>

  <xsl:template match="/">
    <xsl:value-of select="xml-to-json($mapped-xml)"/>
  </xsl:template>
</xsl:stylesheet>
```

---

**Rules for Pattern B (both approaches):**
- Output method is **always `text`** (NEVER `xml`)
- The `$mapped-xml` variable is built **before** the `<xsl:template>` — the template body only calls `xml-to-json($mapped-xml)`
- For each field mapping row: use the **Target Element** from the TDD as the wrapping element, and the **Source XPath** from the TDD in the `select` attribute
- Array iteration: use `xsl:for-each` with the Source XPath of the array row, then fields inside the loop use **relative** Source XPaths (already pre-computed as relative in the TDD)
- For `format-number()`, wrap the Source XPath: `format-number({Source XPath}, 'pattern')`
- For `upper-case()`, wrap the Source XPath: `upper-case({Source XPath})`
- Conditional (`xsl:choose`) inside `$mapped-xml` for boolean computed fields
- The target `<boolean>` element must contain exactly `true` or `false` (lowercase)
- **Do NOT mix approaches.** The bug that causes `An empty string is not valid JSON` is always a mix: `useJsonBody: true` in the route (Approach A) with `xsl:param name="body"` + `json-to-xml($body)` in the XSLT (Approach B). The self-validation pass (Step 3.5) checks for this.
- **NEVER call `json-to-xml()` in Approach A.** With `useJsonBody: true`, Camel already converts the JSON body to lossless XML before Saxon starts. The XSLT input is XML, not JSON. Calling `json-to-xml()` on XML causes a fatal Saxon error: `Invalid JSON input: Invalid numeric literal: multiple points`. Navigate directly from `/fn:map/...` — no conversion needed.

---

### Pattern C: JSON → XML

Use when XSLT Pattern = `C` in the TDD. **Check the XSLT Approach field to select the correct skeleton.**

#### Pattern C — Approach A (useJsonBody: true)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- This file is generated by Kaoto DataMapper. Do not edit. -->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fn="http://www.w3.org/2005/xpath-functions">
  <xsl:output method="xml" indent="yes"/>

  <!-- NO xsl:param — Camel already converted the body to lossless XML -->

  <xsl:template match="/">
    <{TargetRoot} xmlns="{target-namespace-URI}">
      <!-- For each TDD row: use Target Element as wrapper, Source XPath in select -->
      <{TargetField}>
        <xsl:value-of select="{Source XPath from TDD — starts with /fn:map/...}"/>
      </{TargetField}>
    </{TargetRoot}>
  </xsl:template>
</xsl:stylesheet>
```

#### Pattern C — Approach B (manual header param)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- This file is generated by Kaoto DataMapper. Do not edit. -->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fn="http://www.w3.org/2005/xpath-functions">
  <xsl:output method="xml" indent="yes"/>

  <xsl:param name="{source-param-name}"/>
  <xsl:variable name="{source-param-name}-x" select="json-to-xml(${source-param-name})"/>

  <xsl:template match="/">
    <{TargetRoot} xmlns="{target-namespace-URI}">
      <{TargetField}>
        <xsl:value-of select="{Source XPath from TDD — starts with $body-x/fn:map/...}"/>
      </{TargetField}>
    </{TargetRoot}>
  </xsl:template>
</xsl:stylesheet>
```

**Rules for Pattern C (both approaches):**
- Output method is `xml`
- JSON source is navigated using the pre-computed **Source XPath** from the TDD
- XML target construction uses normal element creation (same as Pattern A)
- Same approach selection rules as Pattern B — do NOT mix approaches

---

### Pattern D: XML → JSON

Use when source-type = `XML_SCHEMA` and target-type = `JSON_SCHEMA`.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- This file is generated by Kaoto DataMapper. Do not edit. -->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fn="http://www.w3.org/2005/xpath-functions"
    xmlns:ns0="{source-namespace-URI}">
  <xsl:output method="text" indent="yes"/>

  <xsl:variable name="mapped-xml">
    <map xmlns="http://www.w3.org/2005/xpath-functions">
      <string key="{target-field}">
        <xsl:value-of select="/ns0:{SourceRoot}/ns0:{SourceField}"/>
      </string>
      <number key="{target-field}">
        <xsl:value-of select="/ns0:{SourceRoot}/ns0:{SourceField}"/>
      </number>
      <array key="{target-array}">
        <xsl:for-each select="/ns0:{SourceRoot}/{SourceCollection}">
          <map>
            <string key="{target-field}"><xsl:value-of select="{SourceField}"/></string>
          </map>
        </xsl:for-each>
      </array>
    </map>
  </xsl:variable>

  <xsl:template match="/">
    <xsl:value-of select="xml-to-json($mapped-xml)"/>
  </xsl:template>
</xsl:stylesheet>
```

**Rules for Pattern D:**
- Output method is `text`
- XML source is navigated using the **Source XPath** from the TDD (with `ns0:` namespace prefixes)
- Target is built in lossless XML format using the **Target Element** from the TDD, and converted with `xml-to-json()`
- JSON type (`string`, `number`, `boolean`) for the target is already specified in the TDD Target Element column

---

## Step 3.5: Verify Generated XSLT Against TDD — MANDATORY

After generating the XSLT file, walk through **every row** in the TDD Field Mappings table and verify the generated XSLT contains a matching element.

**For each field mapping row, check:**

| Check | What to verify |
|-------|----------------|
| Completeness | The TDD row has a corresponding element in the XSLT |
| Source XPath | The `select="..."` attribute matches the TDD **Source XPath** column |
| Target Element | The XSLT element tag/key matches the TDD **Target Element** column |
| Type consistency | `fn:string`/`fn:number`/`fn:boolean` matches the source field type |
| Approach purity | No `xsl:param` when Approach is A; no `useJsonBody` when Approach is B |
| No `json-to-xml()` in Approach A | When Approach = A, the XSLT MUST NOT contain any call to `json-to-xml()` — the input is already lossless XML |

**Present the verification result:**

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
XSLT VERIFICATION AGAINST TDD
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
| TDD Row | Source XPath Match | Target Element Match | Status |
|---|---|---|---|
| orderId → orderId | ✅ | ✅ | OK |
| main.temp → temperature | ✅ | ✅ | OK |
| items[] → items[] | ✅ | ✅ | OK |
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**If any row shows ❌:** fix the XSLT to match the TDD values and re-verify before proceeding.

**Only proceed to Step 4 when all rows show ✅.**

### Step 3.5b: Verify Route YAML After Step 4 — MANDATORY

After injecting the Camel YAML step (Step 4), verify the route YAML matches the XSLT Approach:

| Approach | Route YAML must contain | Route YAML must NOT contain |
|---|---|---|
| A (useJsonBody) | `useJsonBody: true` in the `parameters:` block | `setHeader`/`setBody` before the step |
| B (header param) | `setHeader` + `setBody` before the step | `useJsonBody: true` |
| N/A | (no special params) | `useJsonBody: true` |

**Missing `useJsonBody: true` for Approach A is a fatal error** — Saxon receives raw JSON and fails with `Content is not allowed in prolog`.

---

## Step 4: Inject Camel YAML Step

In `{flow-name}.camel.yaml`, locate the position for this datamapper step and inject the correct YAML block based on the XSLT Approach.

**CRITICAL — select the correct block. Omitting `useJsonBody: true` when Approach = A causes a fatal Saxon error: `Content is not allowed in prolog` (Saxon tries to parse raw JSON as XML).**

### Step 4 — Approach A (useJsonBody)

**When TDD says `XSLT Approach: A (useJsonBody)`**, inject this block — the `useJsonBody: true` parameter is MANDATORY:

```yaml
- step:
    id: kaoto-datamapper-{id}
    steps:
      - to:
          id: kaoto-datamapper-xslt-{4hexchars}
          uri: xslt-saxon:kaoto-datamapper-{id}.xsl
          parameters:
            useJsonBody: true
```

### Step 4 — Approach B (header param)

**When TDD says `XSLT Approach: B (header param)`**, inject this block — do NOT include `useJsonBody`:

```yaml
- setHeader:
    name: "{xsl-param-name}"
    expression:
      simple: "${bodyAs(String)}"
- setBody:
    expression:
      constant: "<root/>"
- step:
    id: kaoto-datamapper-{id}
    steps:
      - to:
          id: kaoto-datamapper-xslt-{4hexchars}
          uri: xslt-saxon:kaoto-datamapper-{id}.xsl
```

### Step 4 — Approach N/A (XML source)

**When TDD says `XSLT Approach: N/A`**, inject without `useJsonBody`:

```yaml
- step:
    id: kaoto-datamapper-{id}
    steps:
      - to:
          id: kaoto-datamapper-xslt-{4hexchars}
          uri: xslt-saxon:kaoto-datamapper-{id}.xsl
```

---

### Reference: How `useJsonBody: true` works at runtime

When `useJsonBody: true` is set on the `xslt-saxon` component, Camel does the following **before the XSLT template runs**:

1. Converts the Exchange body to a `String` (`convertTo(String.class, body)`)
2. Runs Saxon's `json-to-xml(.)` on that string → lossless XML document
3. Returns that lossless XML as a `DOMSource` — this becomes the primary input document

**Consequence for Approach A:** The XSLT's context node is already the lossless XML. No `xsl:param` needed — navigate directly from `/fn:map/...`.

**Consequence for Approach B:** If `useJsonBody: true` is set but the XSLT declares `<xsl:param name="body"/>`, the param is empty → `json-to-xml("")` → Saxon error: `An empty string is not valid JSON`. **Never mix approaches.**

**What must NOT happen before the DataMapper step:** `unmarshal: json:` — this converts the body to a `java.util.LinkedHashMap`, and `json-to-xml()` cannot work with that. Unmarshal **after** the DataMapper if needed.

---

### Reference: Approach B route setup

When the TDD specifies Approach B, the Camel route must include `setHeader` + `setBody` steps **before** the DataMapper step:

```yaml
- setHeader:
    name: "{xsl-param-name}"     # must exactly match xsl:param name in the XSLT
    expression:
      simple: "${bodyAs(String)}"
- setBody:
    expression:
      constant: "<root/>"
- step:
    id: kaoto-datamapper-{id}
    steps:
      - to:
          id: kaoto-datamapper-xslt-{4hexchars}
          uri: xslt-saxon:kaoto-datamapper-{id}.xsl
          parameters:
            failOnNullBody: false
            # DO NOT add useJsonBody: true here
```

**`setHeader` must be called while the body is still a String or InputStream** — before any `unmarshal: json:` step.

---

### Reference: Mandatory correctness check for Approach B — `json-to-xml($paramName)` not `json-to-xml(.)`

If Approach B is used, `json-to-xml()` must receive the **JSON string from the named param** (`$paramName`), not the context node (`.`). The context node in Approach B is the `<root/>` placeholder, not the JSON string.

```xml
<!-- CORRECT -->
<xsl:param name="body"/>
<xsl:variable name="body-x" select="json-to-xml($body)"/>

<!-- WRONG — json-to-xml receives <root/>, not JSON -->
<xsl:variable name="body-x" select="json-to-xml(.)"/>
```

The `kaoto-datamapper-xslt-{4hexchars}` inner ID is a fresh random 4-character hex string, different from the outer mapping ID.

---

## Step 5: Create or Update `.kaoto` Metadata File — MANDATORY

**This step is NOT optional.** Without the `.kaoto` file, the Kaoto visual editor cannot open the DataMapper node — the user sees an empty editor instead of the mappings.

**CRITICAL — Filename and format rules:**

| Rule | Correct | WRONG (do NOT do this) |
|------|---------|------------------------|
| Filename | `.kaoto` | ~~`kaoto-datamapper-{id}.kaoto`~~, ~~`{flow-name}.kaoto`~~ |
| Location | Project root (same directory as `.camel.yaml`) | NOT in `.camel-kit/` |
| Format | Kaoto's internal JSON format (see template below) | NOT a custom JSON schema |
| One file | Single `.kaoto` file for ALL DataMapper mappings in the project | NOT one file per mapping |

The `.kaoto` file is a **single JSON object** where each key is a mapping ID (`kaoto-datamapper-{id}`). Multiple DataMapper sections add multiple keys to the same file.

**If `.kaoto` does not exist:** create it. **If it exists:** read it, add the new key, write back.

**CRITICAL — Kaoto `type` values are display strings, NOT enum keys:**

| TDD type | `.kaoto` type value |
|----------|---------------------|
| `XML_SCHEMA` | `"XML Schema"` |
| `JSON_SCHEMA` | `"JSON Schema"` |
| `Primitive` | `"Primitive"` |

Using `"JSON_SCHEMA"` or `"XML_SCHEMA"` (with underscores) will cause Kaoto to fail silently.

**Use this EXACT structure — do NOT invent your own JSON format:**

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

**The ONLY keys allowed in a mapping entry are:** `xsltPath`, `sourceBody`, `targetBody`, `sourceParameters`, `namespaceMap`. Do NOT add `mappingId`, `flowName`, `migratedFrom`, `xsltPattern`, `xsltApproach`, `fieldMappings`, `conditionalMappings`, or any other invented keys.

**Rules:**
- `filePath` is `[]` when no schema file exists (schema path is `"none"` in TDD) — this applies to ANY type (`Primitive`, `JSON_SCHEMA`, or `XML_SCHEMA`). A `JSON_SCHEMA` type with `filePath: []` is valid and means schemaless JSON.
- `sourceParameters` is `{}` if no parameters defined in TDD
- Always include the three base namespace entries: `xs`, `fn`, `xsl`
- Add `ns0` (and further prefixes e.g. `ns1`) for each XML namespace from the TDD namespace map
- All file paths are relative to the project root (same directory as `.kaoto`)
- Write with tab indentation (matching Kaoto's format: `JSON.stringify(content, null, '\t')`)
- **Never overwrite existing keys** — only append the new `kaoto-datamapper-{id}` key

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
✅ kaoto-datamapper-{id}.xsl    (project root)
✅ {flow-name}.camel.yaml       (step injected)
✅ .kaoto                       (key added: kaoto-datamapper-{id})
✅ XSLT verified against TDD    ({N}/{N} fields matched)

Pattern:        {A | B | C | D} ({source-format} → {target-format})
Approach:       {A (useJsonBody) | B (header param) | N/A}
Fields mapped:  {N} ({auto} auto, {inferred} inferred, {manual} manual)
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
