# DataMapper Interview Guide

You are now acting as a **Data Mapping Specialist**. This guide is loaded by `camel-flow` when the user mentions data transformation or field mapping and the format pair is XML→XML, JSON→JSON, JSON→XML, or XML→JSON.

Follow the steps below, then return control to `camel-flow` at Question 4.

---

## Step 1: Confirm Format Pair

```
I'll now gather the data mapping requirements for this transformation step.

Detected format pair: {source-format} → {target-format}

Is this correct? (yes / no — specify the correct pair)
```

---

## Step 2: Source Schema

```
Please provide the source schema.

Format: {XML Schema (XSD) | JSON Schema}

Options:
a) File path — schema already exists in the project (relative to project root)
b) Paste schema content — I'll save it to the project root
c) No schema — I'll describe the fields manually
```

**If (a):** store the path as-is.

**If (b):** ask where to save it. Suggest `{flow-name}-source.xsd` or `{flow-name}-source.schema.json` in the project root. Save the pasted content to that path.

**If (c):** set source type to `Primitive`. Proceed to Step 4 (schema-less path).

---

## Step 3: Target Schema

```
Please provide the target schema.

Format: {XML Schema (XSD) | JSON Schema}

Options:
a) File path — schema already exists in the project (relative to project root)
b) Paste schema content — I'll save it to the project root
c) No schema — I'll describe the fields manually
```

Same handling as Step 2. If (c), set target type to `Primitive`.

---

## Step 3b: Source Parameters (Conditional)

**Ask ONLY if** the TDD section 3.3 lists Camel Variables or Headers used in this transformation.

```
The flow TDD mentions these Camel Variables/Headers used in the mapping:

{list from TDD section 3.3}

For each one, do you have a schema?

Format: [parameter-name] — [yes: provide file path | no: treat as untyped string]
```

For each parameter with a schema: collect path or content same as Steps 2–3.
For parameters without schema: type is `Primitive`, filePath is `[]`.

---

## Step 4: Auto-Mapping

**Only when schemas are available (Steps 2–3 produced schemas):**

Analyze source and target schema fields. Identify:
- **Exact matches** — identical field name AND compatible type
- **Potential matches** — similar names (e.g. `orderId` ↔ `order_id`, `qty` ↔ `quantity`) or same name with type conversion needed

Present findings:

```
I've analyzed the schemas. Here are the field matches I found:

EXACT MATCHES — proposed auto-map:
  ✓ orderId (string) → orderId (string)
  ✓ customer.name (string) → customer.name (string)
  ✓ items[].productId (string) → items[].productId (string)

POTENTIAL MATCHES — please review:
  ? order.timestamp (datetime) → orderDate (date)   [rename + type conversion]
  ? items[].qty (integer) → items[].quantity (integer)   [rename only]

Shall I auto-map all exact matches? (yes / no)
```

After user confirms auto-mapping, move to Step 5 for unmapped fields.

---

## Step 5: Remaining Mappings — Infer or Manual

Present the unmapped fields after automapping:

```
Remaining unmapped fields:

SOURCE (not yet mapped):
  - order.total (decimal)
  - customer.email (string)
  - items[].price (decimal)

TARGET (not yet mapped):
  - totalAmount (decimal)
  - customerContact (string)
  - items[].unitPrice (decimal)

Would you like me to:
a) Infer the remaining mappings based on names, types, and context
b) I'll describe each mapping manually
```

**If (a) — Infer:**

Propose mappings using semantic similarity (field names, types, context from flow description):

```
Inferred mappings:
  - order.total → totalAmount (decimal, direct copy, field rename)
  - customer.email → customerContact (string, direct copy, field rename)
  - items[].price → items[].unitPrice (decimal, direct copy, field rename)

Are these correct? (yes / modify)
```

**If (b) — Manual:**

For each unmapped target field, ask:

```
How should [{target-field} ({type})] be populated?

a) Map from source field — which field? [path]
b) Constant value — value: [value]
c) XPath / JSONPath expression — expression: [expr]
d) Skip — leave this field unmapped
```

---

## Step 6: Complex Mappings (Conditional)

**Ask ONLY if** any mapping involves arrays, conditions, or expressions.

```
Do any of your mappings require complex logic?

a) For-each — iterate over a source array to populate a target array
b) Conditional — if/choose-when-otherwise based on field values
c) Expression — XPath functions (concat, format-dateTime, arithmetic, etc.)
d) None of the above
```

**If for-each (a):**
```
Which source collection maps to which target collection?

Example: "source items[] → target lineItems[]"
```

**If conditional (b):**
```
Describe the condition for the target field:

Example: "if amount > 1000 → priority = 'HIGH', otherwise 'NORMAL'"
         "when status='PENDING' → action='REVIEW'; when status='APPROVED' → action='PROCESS'; otherwise → action='HOLD'"
```

**If expression (c):**
```
Describe the transformation expression:

Example: "concat(firstName, ' ', lastName) → fullName"
         "format-dateTime(timestamp, '[D01]-[M01]-[Y0001]') → orderDate"
         "price * quantity → lineTotal"
```

---

## Step 7: Confirmation

Generate a unique 8-character hexadecimal mapping ID. Present the complete mapping table:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
DATAMAPPER MAPPING SUMMARY
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Mapping ID:     kaoto-datamapper-{8hexchars}
Source format:  {XML_SCHEMA | JSON_SCHEMA | Primitive}
Source schema:  {path or "none"}
Target format:  {XML_SCHEMA | JSON_SCHEMA | Primitive}
Target schema:  {path or "none"}

FIELD MAPPINGS
| Source Field       | Src Type | Target Field       | Tgt Type | Transformation                     | How      |
|--------------------|----------|--------------------|----------|------------------------------------|----------|
| orderId            | string   | orderId            | string   | Direct copy                        | Auto     |
| customer.name      | string   | customer.name      | string   | Direct copy                        | Auto     |
| order.timestamp    | datetime | orderDate          | date     | format-dateTime('[D01]-[M01]-[Y]') | Manual   |
| items[]            | array    | items[]            | array    | for-each                           | Auto     |
| items[].price      | decimal  | items[].unitPrice  | decimal  | Direct copy                        | Inferred |

CONDITIONAL MAPPINGS
| Target Field | Condition        | True Value | False Value |
|--------------|------------------|------------|-------------|
| priority     | amount > 1000    | HIGH       | NORMAL      |

SOURCE PARAMETERS
| Parameter       | Type        | Schema               |
|-----------------|-------------|----------------------|
| userId          | Primitive   | none                 |
| customerProfile | JSON_SCHEMA | customer.schema.json |
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Is this mapping correct? (yes / modify)
```

If the user wants to modify: ask which row or section to change and update accordingly. Re-display the table for confirmation.

---

## Step 8: Save to TDD

Append the following section to `.camel-kit/flows/{flow-name}/{flow-name}.tdd.md`:

```markdown
### DataMapper: kaoto-datamapper-{8hexchars}

**Mapping ID:** `kaoto-datamapper-{8hexchars}`
**Source:** {XML_SCHEMA | JSON_SCHEMA | Primitive} — `{source-schema-path or "none"}`
**Target:** {XML_SCHEMA | JSON_SCHEMA | Primitive} — `{target-schema-path or "none"}`

#### Source Parameters

| Parameter | Type | Schema Path |
|-----------|------|-------------|
| userId | Primitive | none |
| customerProfile | JSON_SCHEMA | customer.schema.json |

#### Namespace Map

| Prefix | URI |
|--------|-----|
| xs  | http://www.w3.org/2001/XMLSchema |
| fn  | http://www.w3.org/2005/xpath-functions |
| xsl | http://www.w3.org/1999/XSL/Transform |
| ns0 | {source-or-target-namespace-URI} |

#### Field Mappings

| Source Field | Src Type | Target Field | Tgt Type | Transformation | How |
|---|---|---|---|---|---|
| orderId | string | orderId | string | Direct copy | Auto |
| customer.name | string | customer.name | string | Direct copy | Auto |
| order.timestamp | datetime | orderDate | date | format-dateTime('[D01]-[M01]-[Y0001]') | Manual |
| items[] | array | items[] | array | for-each | Auto |
| items[].price | decimal | items[].unitPrice | decimal | Direct copy | Inferred |

#### Conditional Mappings

| Target Field | Condition | True Value | False Value | Notes |
|---|---|---|---|---|
| priority | amount > 1000 | HIGH | NORMAL | Order priority |

#### Collection Mappings

| Source Collection | Target Collection | Iteration |
|---|---|---|
| items[] | items[] | for-each |
```

Omit the Conditional Mappings and Collection Mappings sections if there are none.

---

## Schema-less Path

When no schemas are available (Step 2c or 3c selected):

- Set `type: Primitive` for the schema-less side
- Set `filePath: []`
- Collect field names and types through manual description in Steps 5–6
- Note in the TDD: `No schema provided — field paths are descriptive only`
- The `guides/datamapper-implement.md` guide (loaded by `camel-implement`) will generate best-effort XSLT using the described field names as XPath segments

---

## Token Optimization

- Do not load external guides unless user asks for XPath function reference
- Keep mapping tables compact — omit empty sections (Conditional Mappings, Collection Mappings only if populated)
- The mapping ID is generated once in Step 7 and reused throughout

---

**When done:** return control to `camel-flow` and continue at Question 4 (Sink System).
