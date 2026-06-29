# DataMapper Migration Guide

This guide is loaded by `camel-migrate-mule` when a DataWeave transformation is detected in a Mule flow.

It converts DataWeave transformation logic into the standard `### DataMapper: kaoto-datamapper-{id}` design spec
section that `camel-execute` uses to generate Kaoto-compatible transformations.

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
- Anything else (CSV, multipart, etc.) → not supported by DataMapper — record an explicit migration design issue and
  skip this guide

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
**If no schema is available:** keep the format detected in Step 1 (`JSON_SCHEMA` or `XML_SCHEMA`) — the schema path is `"none"` but the type reflects the actual data format. Field names will be taken from Step 3 inference. The canonicalize guide computes XPaths from field names without needing schema files.

**`Primitive` is only correct when the data is a truly scalar value** (a single string, number, or boolean — not a JSON object or XML document). If the DataWeave script accesses `payload.field` or `payload.obj.field`, the format is structured and the type must be `JSON_SCHEMA` or `XML_SCHEMA`, even without a schema file.

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

Generate the mapping ID: take the first 8 hex characters of `SHA-256(flow-name + "-" + source-format + "-" + target-format)`. If a flow has multiple DataMapper steps, append a sequential suffix to the hash input (e.g., `-1`, `-2`). Present all inferred mappings with confidence indicators:

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
   b) Record as a required custom mapping action in the design spec
   c) Skip this field — handle it separately in a Camel processor bean
```

---

## Step 6: Canonicalize and Save

Use the mapping ID generated in Step 4 (or generate one using the SHA-256 algorithm if not already generated).

Load `skills/shared/datamapper-canonicalize.md` and follow all steps, passing:
- The confirmed field mappings from Step 4 (source field, src type, target field, tgt type, transformation, how)
- Conditional and collection mappings from Step 3 (if any)
- Source/target types and schema paths from Steps 1–2
- Source parameters from Step 3 (Camel Variables/Headers)
- Namespace map (constructed from schema namespaces — include `xs`, `fn`, `xsl` base entries plus `ns0` for XML namespaces)
- The generated mapping ID
- The flow name

The shared guide will:
1. Choose the transformation engine (Groovy for < 20 fields or no schemas, XSLT otherwise)
2. If XSLT: compute Source XPaths and Target Elements for each field; if Groovy: prepare simplified semantic table
3. Present the enriched mapping table for user confirmation
4. Write the canonical `### DataMapper:` section to the design spec (with empty mapping guard)

**When done:** return control to `camel-migrate-mule` Step 2.3 to continue updating the design spec for this flow.
