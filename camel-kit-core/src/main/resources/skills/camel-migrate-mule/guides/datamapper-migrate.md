# DataMapper Migration Guide

This guide is loaded by `camel-migrate-mule` when a DataWeave transformation is detected in a Mule flow.

It converts DataWeave transformation logic into the standard `### DataMapper: kaoto-datamapper-{id}` TDD section that `camel-implement` uses to generate Kaoto-compatible XSLT 3.0.

**Context provided by `camel-migrate-mule`:**
- The DataWeave script(s) for the current flow (already analyzed by `mule-dataweave-conversion.md`)
- The Mule flow name and its Camel equivalent name
- The Mule project directory structure

---

## Step 1: Detect Format Pair

Determine source and target formats from the DataWeave script.

**Target format** — read the `output` directive at the top of the script:
- `output application/json` → `JSON_SCHEMA`
- `output application/xml` → `XML_SCHEMA`
- Anything else (CSV, multipart, etc.) → not supported by DataMapper — flag as TODO, skip this guide

**Source format** — infer in this order:
1. DataWeave `input payload` declaration (if present)
2. Mule HTTP listener `allowed-methods` / accepted MIME type in the flow XML
3. DataWeave `payload` access pattern:
   - `payload.key` / `payload.array map (...)` → JSON
   - `payload.*elementName` / XPath-style access → XML
4. Mule message type declarations or `<set-payload>` MIME type attribute

**If source format cannot be determined:**
```
I need to determine the source data format for the DataWeave transformation in flow '{mule-flow-name}'.

The DataWeave script reads from `payload` but the format isn't explicit in the Mule config.

What format does this flow receive?
a) JSON
b) XML
c) Other (describe) — note: only JSON and XML are supported by DataMapper
```

---

## Step 2: Locate Schemas

Search the Mule project for existing schema files that match the source/target:

**Where to look:**
- XSD files: `src/main/resources/**/*.xsd`, `src/main/mule/**/*.xsd`, project root `*.xsd`
- JSON Schema: `src/main/resources/**/*.json`, `**/*.schema.json`
- RAML: `*.raml` or `src/main/api/**/*.raml` (may embed or reference schemas)
- DataWeave types: `src/main/resources/dwl/**/*.dwl` with `type` declarations

Present findings:

```
Searching Mule project for schemas...

Found:
  XSD:         [list paths, or "none"]
  JSON Schema: [list paths, or "none"]
  RAML:        [list paths, or "none"]

Which schema file corresponds to the source payload?  [path or "none"]
Which schema file corresponds to the target output?   [path or "none"]
```

**If a schema is selected:** record path relative to project root. It will be copied to the Camel project root alongside the `.camel.yaml` files.
**If no schema is available:** set type to `Primitive`. Field names will be taken from Step 3 inference.

---

## Step 3: Infer Field Mappings from DataWeave

Analyze the DataWeave script and extract field mappings using these patterns. Use the analysis already done by `mule-dataweave-conversion.md` — do not re-analyze from scratch.

### Direct Field Copy

```dataweave
{ orderId: payload.orderId }
```
→ `payload.orderId → orderId` | Direct copy | ✅ High confidence

### Renamed Field

```dataweave
{ totalAmount: payload.order.total }
```
→ `payload.order.total → totalAmount` | Direct copy, field rename | ✅ High confidence

### Type Conversion / Expression

```dataweave
{ orderDate: payload.timestamp as String {format: "dd-MM-yyyy"} }
{ fullName: payload.firstName ++ " " ++ payload.lastName }
```
→ `payload.timestamp → orderDate` | `format-dateTime('[D01]-[M01]-[Y0001]')` | ⚠️ Review
→ `payload.firstName + " " + payload.lastName → fullName` | `concat(...)` | ⚠️ Review

### Collection (map)

```dataweave
{ items: payload.items map ((item) -> { sku: item.productId, qty: item.quantity }) }
```
→ Collection: `payload.items[] → items[]` | for-each
→ Within iteration: `item.productId → sku`, `item.quantity → qty`

### Conditional (if/else)

```dataweave
{ priority: if (payload.amount > 1000) "HIGH" else "NORMAL" }
```
→ Conditional: `amount > 1000` → `HIGH` / `NORMAL` | ✅ High confidence

### Pattern Match (when / otherwise)

```dataweave
{ action: payload.status match {
    case "PENDING"  -> "REVIEW"
    case "APPROVED" -> "PROCESS"
    else            -> "HOLD"
  }
}
```
→ Choose-when-otherwise: `status='PENDING'→REVIEW`, `status='APPROVED'→PROCESS`, otherwise `HOLD`

### Camel Variables / Headers (from DataWeave vars / attributes)

```dataweave
{ tenantId: vars.tenantId, userId: attributes.headers."X-User-Id" }
```
→ Source parameters: `vars.tenantId` → Camel Variable `tenantId` (Primitive), `attributes.headers.X-User-Id` → Camel Header `userId` (Primitive)

---

## Step 4: Present Inferred Mappings for Confirmation

Generate a unique 8-character hexadecimal mapping ID. Present all inferred mappings with confidence indicators:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
DATAMAPPER MAPPING — INFERRED FROM DATAWEAVE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Flow (Mule):    {mule-flow-name}
Flow (Camel):   {camel-flow-name}
Mapping ID:     kaoto-datamapper-{8hexchars}
Source format:  {XML_SCHEMA | JSON_SCHEMA | Primitive}
Source schema:  {path or "none"}
Target format:  {XML_SCHEMA | JSON_SCHEMA | Primitive}
Target schema:  {path or "none"}

FIELD MAPPINGS
| Source Field        | Src Type | Target Field  | Tgt Type | Transformation               | Confidence |
|---------------------|----------|---------------|----------|------------------------------|------------|
| payload.orderId     | string   | orderId       | string   | Direct copy                  | ✅ High    |
| payload.order.total | decimal  | totalAmount   | decimal  | Direct copy, rename          | ✅ High    |
| payload.timestamp   | datetime | orderDate     | date     | format-dateTime(dd-MM-yyyy)  | ⚠️ Review  |
| payload.items[]     | array    | items[]       | array    | for-each                     | ✅ High    |
| payload.items[].productId | string | items[].sku | string | Direct copy              | ✅ High    |

CONDITIONAL MAPPINGS
| Target Field | Condition     | True Value | False Value | Confidence |
|--------------|---------------|------------|-------------|------------|
| priority     | amount > 1000 | HIGH       | NORMAL      | ✅ High    |

SOURCE PARAMETERS
| Parameter | Source            | Type      | Schema |
|-----------|-------------------|-----------|--------|
| tenantId  | vars.tenantId     | Primitive | none   |
| userId    | attributes.headers| Primitive | none   |
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Confidence legend:
  ✅ High   — direct DataWeave equivalent, mapping is unambiguous
  ⚠️ Review — inferred from a pattern; please confirm the transformation is correct

Are these mappings correct? (yes / modify)
```

For any ⚠️ Review row: ask the user to confirm or correct the transformation expression before proceeding.

If the user modifies entries: update the table and re-display for final confirmation.

---

## Step 5: Handle Unsupported DataWeave Constructs

If the DataWeave script contains constructs that cannot be mapped to the patterns above:

```
⚠️  The following DataWeave expression(s) could not be automatically mapped:

1. Expression: [{dataweave expression}]
   Target field: {field name}
   Issue: {reason — e.g. custom Java function, complex reduce, dynamic key generation}

   Options:
   a) Describe the intended mapping — I'll record it as a manual expression
   b) Mark as TODO — generate a placeholder comment in the XSLT
   c) Skip this field — handle it separately in a Camel processor bean
```

---

## Step 6: Save DataMapper Section to TDD

Append the following section to `.camel-kit/flows/{flow-name}/{flow-name}.tdd.md` — after the existing Section 3.x content. This section is what `camel-implement` acts on; the existing Section 3.2 Field Mapping Table remains as migration audit trail.

```markdown
### DataMapper: kaoto-datamapper-{8hexchars}

**Mapping ID:** `kaoto-datamapper-{8hexchars}`
**Migrated from DataWeave:** `{path-to-original-dataweave-file-or-inline}`
**Source:** {XML_SCHEMA | JSON_SCHEMA | Primitive} — `{source-schema-path or "none"}`
**Target:** {XML_SCHEMA | JSON_SCHEMA | Primitive} — `{target-schema-path or "none"}`

#### Source Parameters

| Parameter | Type | Schema Path |
|-----------|------|-------------|
| tenantId | Primitive | none |
| userId | Primitive | none |

#### Namespace Map

| Prefix | URI |
|--------|-----|
| xs  | http://www.w3.org/2001/XMLSchema |
| fn  | http://www.w3.org/2005/xpath-functions |
| xsl | http://www.w3.org/1999/XSL/Transform |
| ns0 | {source-or-target-namespace-URI — XML only} |

#### Field Mappings

| Source Field | Src Type | Target Field | Tgt Type | Transformation | How |
|---|---|---|---|---|---|
| payload.orderId | string | orderId | string | Direct copy | Inferred |
| payload.order.total | decimal | totalAmount | decimal | Direct copy | Inferred |
| payload.timestamp | datetime | orderDate | date | format-dateTime('[D01]-[M01]-[Y0001]') | Confirmed |
| payload.items[] | array | items[] | array | for-each | Inferred |
| payload.items[].productId | string | items[].sku | string | Direct copy | Inferred |

#### Conditional Mappings

| Target Field | Condition | True Value | False Value | Notes |
|---|---|---|---|---|
| priority | amount > 1000 | HIGH | NORMAL | Migrated from DataWeave if/else |

#### Collection Mappings

| Source Collection | Target Collection | Iteration |
|---|---|---|
| payload.items[] | items[] | for-each |
```

Omit Conditional Mappings and Collection Mappings sections if there are none.

**CRITICAL — do not save an empty Field Mappings table.** If Step 3 (Infer Field Mappings from DataWeave) produced no rows, do NOT append the DataMapper section to the TDD. Instead, report:

```
⚠️ WARNING: No field mappings could be inferred from the DataWeave script in flow '{mule-flow-name}'.

Possible causes:
- The DataWeave script uses unsupported constructs (see Step 5)
- The script only sets metadata or variables, not payload fields
- The transformation logic is too complex to infer automatically

Action required:
- Review the DataWeave script manually and add field mappings to the TDD
- Then re-run /camel-implement {flow-name}
```

The `camel-implement` guide will generate an empty, non-functional XSLT if given an empty Field Mappings table, which is worse than having no DataMapper section at all.

---

**When done:** return control to `camel-migrate-mule` Step 2.3 to continue producing the TDD for this flow.
