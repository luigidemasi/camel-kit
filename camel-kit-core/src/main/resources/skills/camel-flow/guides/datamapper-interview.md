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

**If (c):** ask the user what format the source data is in (JSON or XML). Set the source type to `JSON_SCHEMA` or `XML_SCHEMA` accordingly — the schema path is `"none"` but the type reflects the actual data format. `Primitive` is only correct for truly scalar values (a single string, number, or boolean — not a JSON object or XML document). Proceed to Step 4 (schema-less path).

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

Same handling as Step 2. If (c), ask format and set type to `JSON_SCHEMA` or `XML_SCHEMA` accordingly. Only use `Primitive` for truly scalar values.

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

## Step 7: Canonicalize and Save

Generate the mapping ID: take the first 8 hex characters of `SHA-256(flow-name + "-" + source-format + "-" + target-format)`. Example: flow `order-to-warehouse` with JSON→XML produces `SHA-256("order-to-warehouse-json-xml")` → first 8 hex chars → `kaoto-datamapper-a3f1b2c4`. If a flow has multiple DataMapper steps, append a sequential suffix to the hash input (e.g., `-1`, `-2`).

Load `skills/shared/datamapper-canonicalize.md` and follow all steps, passing:
- The semantic field mappings collected from Steps 4–6 (source field, src type, target field, tgt type, transformation, how)
- Conditional and collection mappings from Step 6 (if any)
- Source/target types and schema paths from Steps 1–3
- Source parameters from Step 3b (if any)
- Namespace map (constructed from schema namespaces — include `xs`, `fn`, `xsl` base entries plus `ns0` for XML namespaces)
- The generated mapping ID
- The flow name

The shared guide will:
1. Determine the XSLT pattern and approach
2. Compute XSLT-ready Source XPaths and Target Elements for each field
3. Present the enriched mapping table for user confirmation
4. Write the canonical `### DataMapper:` section to the TDD

**Schema-less path:** If source or target has no schema file (Step 2c or 3c selected), the type is still `JSON_SCHEMA` or `XML_SCHEMA` (based on the data format) — the schema path is `"none"`. Pass the manually described field names and types. The shared guide will compute XPaths from the field paths without needing schema files. Only use `Primitive` if the data is a truly scalar value (single string, number, or boolean).

**When done:** return control to `camel-flow` and continue at Question 4 (Sink System).
