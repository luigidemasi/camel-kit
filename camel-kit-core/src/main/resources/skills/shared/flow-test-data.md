# Flow Test Data Generation

Generate synthetic I/O pairs from a TDD (Technical Design Document) for use in behavioral verification. This is a **flow-level** guide — it works for all flow types, not just DataMapper transformations.

**Who calls this guide:**
- **Proactive:** `datamapper-canonicalize.md` (Step 5/3G) or `camel-flow` after writing the TDD
- **Lazy:** `camel-verify` Phase 4 if `test-data/` directory doesn't exist but a TDD is available
- **Manual:** The user can create/edit test data files directly

---

## Input

Read the full TDD at `docs/flows/{flow-name}/{flow-name}.tdd.md`. Extract:

| TDD Section | What to Extract | Used For |
|---|---|---|
| Section 2 (Source System) | Source format (JSON/XML), schema path, component | Input file format and structure |
| Section 3 (Processing Steps) | Transformations, routing logic, field mappings, enrichment | Determining what the output should look like |
| Section 4 (Sink System) | Target format (JSON/XML), schema path, component | Output file format and structure |
| DataMapper section (if present) | Field mappings table, conditional mappings, collection mappings | Exact field-by-field input→output mapping |

---

## Output

Create files in `docs/flows/{flow-name}/test-data/`:

```
docs/flows/{flow-name}/
├── {flow-name}.tdd.md
└── test-data/
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
- `{format}`: `json` or `xml` matching the source/target format from the TDD

---

## Generation Rules: Flows with DataMapper

When the TDD has a DataMapper section with field mappings, generate test cases from the mapping table.

### Test Cases

| # | Test Case | What It Exercises | When Generated |
|---|---|---|---|
| 01 | Happy path | All fields present with typical values, end-to-end transformation | Always |
| 02 | Nullable fields | Optional/nested fields absent, safe navigation (`?.`) | When any source field is marked optional or uses `?.` in the mapping |
| 03+ | Conditional (per branch) | Both sides of each conditional mapping | One pair per conditional mapping, per branch |
| N | Collection | Source array with 2-3 items, iteration logic | When the TDD has collection mappings |

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

**Conditional thresholds:** When a conditional mapping uses a threshold (e.g., `amount > 1000`), generate values on both sides:
- True branch: `amount: 1500` (clearly above threshold)
- False branch: `amount: 500` (clearly below threshold)

### Building the Expected Output

For each test case, apply the TDD's field mappings to the input to produce the expected output:

1. **Direct copy fields:** Copy the input value unchanged → `orderId: "test-orderId"` → `orderId: "test-orderId"`
2. **Transformation fields:** Apply the documented transformation → `format('##.##')` on `19.99` → `"19.99"`
3. **Conditional fields:** Apply the appropriate branch based on the input value
4. **Collection fields:** Apply the per-item mapping to each item in the input array
5. **Dynamic fields:** Use a placeholder value and add the field path to `ignore-fields.txt`

---

## Generation Rules: Flows without DataMapper

When the TDD has no DataMapper section, read TDD Section 2 (source format) and Section 3 (processing steps) to determine the flow type and generate appropriate test cases.

### By Flow Type

| Flow Type | How to Identify | Input Generation | Output Generation |
|---|---|---|---|
| Content-based routing | TDD Section 3 has routing conditions (e.g., "route to A if X, else to B") | One input per routing branch, with the field that triggers each branch set to the appropriate value | Expected output at each branch's target endpoint |
| Validation/filter | TDD Section 3 has validation rules or filter conditions | One valid input that passes validation, one invalid input that fails | Valid input passes through unchanged to sink; invalid goes to DLQ or is filtered out |
| Pass-through | TDD Section 3 has no transformation or routing logic | Typical input matching source schema | Same as input (output should match input) |
| Enrichment | TDD Section 3 adds fields from external lookups or headers | Typical input matching source schema | Input plus the enrichment fields added by processing steps |
| Aggregation | TDD Section 3 aggregates multiple messages | Multiple input files representing the aggregation window | Single aggregated output |

### Content-Based Routing Example

If TDD Section 3 says: "Route to `direct:priority-handler` if `priority == 'HIGH'`, else to `direct:standard-handler`":

```
03-routing-high-priority-input.json    → input with priority: "HIGH"
03-routing-high-priority-expected-output.json → output at priority-handler sink

04-routing-standard-input.json         → input with priority: "NORMAL"
04-routing-standard-expected-output.json → output at standard-handler sink
```

---

## `ignore-fields.txt`

One field path per line. Fields listed here are skipped during semantic comparison in `camel-verify` Phase 4.

```
processedAt
correlationId
messageId
timestamp
```

**Auto-generate entries when the TDD or processing steps reference:**
- Timestamp generation (e.g., `simple: ${date:now:yyyy-MM-dd}`)
- UUID generation (e.g., `simple: ${exchangeId}`)
- Correlation ID assignment
- Sequence number generation

If no dynamic fields exist, create an empty `ignore-fields.txt` file (the file must exist even if empty).

---

## Format Rules

### JSON Input/Output

- Use 2-space indentation
- Use double quotes for all keys and string values
- Include all fields from the source/target schema
- For nullable fields test case: omit the optional fields entirely (don't set them to `null`)

### XML Input/Output

- Use 2-space indentation
- Include XML declaration: `<?xml version="1.0" encoding="UTF-8"?>`
- Use namespace prefixes matching the TDD schema
- For nullable fields test case: omit the optional elements entirely

---

## Confirmation

After generating test data, report:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TEST DATA GENERATED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Flow:           {flow-name}
Location:       docs/flows/{flow-name}/test-data/
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
