# Flow Test Data Generation

Generate synthetic I/O pairs from the active pipeline design spec for use in behavioral verification. This is a
flow-level guide and works for all flow types, not just DataMapper transformations.

**Who calls this guide:**

- **Proactive:** `datamapper-canonicalize.md` or `camel-brainstorm` after finalizing a flow design
- **Lazy:** `camel-verify` when pipeline-local `test-data/` does not exist but a design spec is available
- **Manual:** The user can create or edit test data files directly

---

## Input

Read `.camel-kit/pipeline.json` to resolve `<PIPELINE_ID>`, then read
`docs/camel-kit/<PIPELINE_ID>/design-spec.md`. Extract the `### Flow: {flow-name}` section:

| Design Spec Section | What to Extract | Used For |
|---|---|---|
| Source System | Source format (JSON/XML), schema path, component | Input file format and structure |
| Processing Steps | Transformations, routing logic, field mappings, enrichment | Determining what the output should look like |
| Sink System | Target format (JSON/XML), schema path, component | Output file format and structure |
| DataMapper section (if present) | Field mappings table, conditional mappings, collection mappings | Exact field-by-field input-to-output mapping |

---

## Output

Create files in `docs/camel-kit/<PIPELINE_ID>/test-data/{flow-name}/`:

```text
docs/camel-kit/<PIPELINE_ID>/test-data/{flow-name}/
├── 01-happy-path-input.{json|xml}
├── 01-happy-path-expected-output.{json|xml}
├── 02-{description}-input.{json|xml}
├── 02-{description}-expected-output.{json|xml}
├── ...
└── ignore-fields.txt
```

**File naming:** `{NN}-{description}-input.{format}` and `{NN}-{description}-expected-output.{format}`

- `{NN}`: zero-padded two-digit number (01, 02, 03...)
- `{description}`: kebab-case description of what the test exercises
- `{format}`: `json` or `xml` matching the source/target format from the design spec

---

## Generation Rules: Flows with DataMapper

When the flow design has a DataMapper section with field mappings, generate test cases from the mapping table.

### Test Cases

| # | Test Case | What It Exercises | When Generated |
|---|---|---|---|
| 01 | Happy path | All fields present with typical values, end-to-end transformation | Always |
| 02 | Nullable fields | Optional/nested fields absent, safe navigation (`?.`) | When any source field is marked optional or uses `?.` in the mapping |
| 03+ | Conditional (per branch) | Both sides of each conditional mapping | One pair per conditional mapping, per branch |
| N | Collection | Source array with 2-3 items, iteration logic | When the flow design has collection mappings |

### Value Generation Rules

Use realistic but synthetic values that are traceable back to the field name:

| Field Type | Input Value | Notes |
|---|---|---|
| `string` | `"test-{fieldName}"` | Traceable: `orderId` → `"test-orderId"` |
| `number` (integer) | Small integers: 42, 100, 7 | Easy to spot in output |
| `number` (decimal) | `19.99`, `42.50` | |
| `boolean` | `true` for happy path | Test `false` in conditional branches |
| `datetime` | `"2026-01-15T10:30:00Z"` | Fixed value — never use `now()` or dynamic |
| `enum/code` | First valid value from schema or mapping notes | |

**Conditional thresholds:** When a conditional mapping uses a threshold (e.g., `amount > 1000`), generate values on
both sides:

- True branch: `amount: 1500` (clearly above threshold)
- False branch: `amount: 500` (clearly below threshold)

### Building the Expected Output

For each test case, apply the design spec's field mappings to the input to produce the expected output:

1. **Direct copy fields:** Copy the input value unchanged → `orderId: "test-orderId"` → `orderId: "test-orderId"`
2. **Transformation fields:** Apply the documented transformation → `format('##.##')` on `19.99` → `"19.99"`
3. **Conditional fields:** Apply the appropriate branch based on the input value
4. **Collection fields:** Apply the per-item mapping to each item in the input array
5. **Dynamic fields:** Use a placeholder value and add the field path to `ignore-fields.txt`

---

## Generation Rules: Flows without DataMapper

When the flow design has no DataMapper section, read the source format and processing steps to determine the flow type
and generate appropriate test cases.

### By Flow Type

| Flow Type | How to Identify | Input Generation | Output Generation |
|---|---|---|---|
| Content-based routing | Processing steps have routing conditions | One input per routing branch, with the trigger field set to the appropriate value | Expected output at each branch's target endpoint |
| Validation/filter | Processing steps have validation rules or filter conditions | One valid input that passes validation, one invalid input that fails | Valid input passes through unchanged to sink; invalid goes to DLQ or is filtered out |
| Pass-through | No transformation or routing logic | Typical input matching source schema | Same as input |
| Enrichment | Processing steps add fields from external lookups or headers | Typical input matching source schema | Input plus enrichment fields |
| Aggregation | Processing steps aggregate multiple messages | Multiple input files representing the aggregation window | Single aggregated output |

### Content-Based Routing Example

If the flow design says: "Route to `direct:priority-handler` if `priority == 'HIGH'`, else to
`direct:standard-handler`":

```text
03-routing-high-priority-input.json
03-routing-high-priority-expected-output.json
04-routing-standard-input.json
04-routing-standard-expected-output.json
```

---

## `ignore-fields.txt`

One field path per line. Fields listed here are skipped during semantic comparison in `camel-verify`.

```text
processedAt
correlationId
messageId
timestamp
```

**Auto-generate entries when the design spec or processing steps reference:**

- Timestamp generation (e.g., `simple: ${date:now:yyyy-MM-dd}`)
- UUID generation (e.g., `simple: ${exchangeId}`)
- Correlation ID assignment
- Sequence number generation

If no dynamic fields exist, create an empty `ignore-fields.txt` file.

---

## Format Rules

### JSON Input/Output

- Use 2-space indentation
- Use double quotes for all keys and string values
- Include all fields from the source/target schema
- For nullable fields test case: omit the optional fields entirely; do not set them to `null`

### XML Input/Output

- Use 2-space indentation
- Include XML declaration: `<?xml version="1.0" encoding="UTF-8"?>`
- Use namespace prefixes matching the design spec schema
- For nullable fields test case: omit the optional elements entirely

---

## Confirmation

After generating test data, report:

```text
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TEST DATA GENERATED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Flow:           {flow-name}
Location:       docs/camel-kit/<PIPELINE_ID>/test-data/{flow-name}/
Test cases:     {N}
Format:         {JSON|XML} → {JSON|XML}
Dynamic fields: {N} (in ignore-fields.txt)

Files:
  01-happy-path-input.json
  01-happy-path-expected-output.json
  02-nullable-fields-input.json
  02-nullable-fields-expected-output.json
  ...
  ignore-fields.txt
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```
