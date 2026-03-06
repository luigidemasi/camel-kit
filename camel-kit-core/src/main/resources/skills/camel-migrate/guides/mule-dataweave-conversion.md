# Mule DataWeave Conversion Guide

This guide helps convert MuleSoft DataWeave transformation scripts into the TDD Section 3 field mapping tables understood by `/camel-implement`. The guide is used by the `camel-migrate-mule` sub-skill during Phase 2 (Integration Architect).

---

## DataWeave Versions

### DataWeave 1.0 (Mule 3.x)

- Used in Mule 3.x runtime
- MIME type: `application/dw`
- Embedded in XML as `<dw:transform-message>` elements
- Expression syntax: `%dw 1.0`, `%output application/json`, `%input payload application/xml`
- Field access: `payload.field`, `flowVars.myVar`, `message.inboundProperties.'Content-Type'`
- Conditional: `payload.status when payload.status != null otherwise "UNKNOWN"`
- Array iteration: `payload.items map { name: $.productName, qty: $.quantity }`
- String functions: `upper payload.name`, `lower payload.name`, `trim payload.name`
- Date formatting: `payload.date as :string {format: "yyyy-MM-dd"}`
- Type coercion: `payload.amount as :number`, `payload.id as :string`

### DataWeave 2.0 (Mule 4.x)

- Used in Mule 4.x runtime
- MIME type: `application/java`, embedded in `<ee:transform>`
- Syntax: `%dw 2.0`, `output application/json` (no `%` prefix)
- Field access: `payload.field`, `vars.myVar`, `attributes.headers.'Content-Type'`
- Conditional: `if (payload.status != null) payload.status else "UNKNOWN"`
- Array iteration: `payload.items map (item, index) -> { name: item.productName, qty: item.quantity }`
- String functions: `upper(payload.name)`, `lower(payload.name)`, `trim(payload.name)`
- Date formatting: `payload.date as String {format: "yyyy-MM-dd"}`
- Type coercion: `payload.amount as Number`, `payload.id as String`
- Null handling: `payload.field default "fallback"`

---

## Decision Matrix: Which Camel Technology to Use

When replacing a DataWeave transformation, choose the approach based on complexity:

| Transformation Complexity | Recommended Approach | Camel Component | Notes |
|--------------------------|---------------------|-----------------|-------|
| Simple field rename / direct copy | `setBody` + Simple language | built-in | No external file needed. Express in TDD Section 3.2 as Direct Copy rows. |
| Single-field type coercion | Simple language expression | built-in | `${body.field}` with type conversion. |
| Set a fixed value | `setHeader` or `setBody(constant(...))` | built-in | |
| Complex JSON→JSON transformation | XSLT via Kaoto DataMapper | `camel-xslt-saxon` | Describe field mappings in TDD Section 3.2 table; `/camel-implement` generates XSLT. |
| Complex XML→JSON or JSON→XML | XSLT | `camel-xslt-saxon` | |
| Conditional field selection | `choice` EIP + `setBody` | built-in | Express as routing in Section 3.3. |
| Array/collection iteration | `split` EIP + per-item processing | built-in | Express in Section 3.5. |
| Lookup / enrichment | `enrich` or `pollEnrich` EIP | built-in | |
| Multi-step complex script | XSLT or Groovy script | `camel-xslt-saxon` / `camel-groovy` | Use Groovy only for logic that cannot be expressed in XSLT. Document in Section 3.2. |

**Rule of thumb:** If you can express it as rows in the TDD Section 3.2–3.6 tables, do so. `/camel-implement` will generate the XSLT automatically from the tables.

---

## Common DataWeave Patterns → TDD Table Equivalents

### Pattern 1: Direct Field Copy

**DataWeave 1.0:**
```dataweave
%dw 1.0
%output application/json
---
{
  orderId: payload.order_id,
  customerName: payload.customer.name
}
```

**DataWeave 2.0:**
```dataweave
%dw 2.0
output application/json
---
{
  orderId: payload.order_id,
  customerName: payload.customer.name
}
```

**TDD Section 3.2 Row:**

| Source Field | Target Field | Transformation | Type | Mule Origin |
|---|---|---|---|---|
| `order_id` | `orderId` | Direct Copy | String | `payload.order_id` |
| `customer.name` | `customerName` | Direct Copy | String | `payload.customer.name` |

---

### Pattern 2: Type Coercion

**DataWeave 2.0:**
```dataweave
amount: payload.amount as Number,
createdAt: payload.created_ts as String {format: "yyyy-MM-dd"}
```

**TDD Section 3.2 Row:**

| Source Field | Target Field | Transformation | Type | Mule Origin |
|---|---|---|---|---|
| `amount` | `amount` | `as Number` | Number | `payload.amount as Number` |
| `created_ts` | `createdAt` | Format `yyyy-MM-dd` | String (Date) | `payload.created_ts as String {format: "yyyy-MM-dd"}` |

---

### Pattern 3: Conditional Mapping (`if/else` or `when/otherwise`)

**DataWeave 1.0:**
```dataweave
status: payload.status when payload.status != null otherwise "PENDING"
```

**DataWeave 2.0:**
```dataweave
status: if (payload.status != null) payload.status else "PENDING"
```

**TDD Section 3.4 Row:**

| Condition | Source Field | Target Field | True Value | False Value | Mule Origin |
|---|---|---|---|---|---|
| `status != null` | `status` | `status` | `${body.status}` | `"PENDING"` | `if (payload.status != null) payload.status else "PENDING"` |

---

### Pattern 4: Array / Collection Iteration (`map`)

**DataWeave 2.0:**
```dataweave
lineItems: payload.items map (item, index) -> {
  seq: index + 1,
  sku: item.product_code,
  qty: item.quantity as Number,
  price: item.unit_price as Number
}
```

**TDD Section 3.5 Row:**

| Collection Field | Item Field | Target Field | Transformation | Mule Origin |
|---|---|---|---|---|
| `items` | `product_code` | `lineItems[].sku` | Direct Copy | `item.product_code` |
| `items` | `quantity` | `lineItems[].qty` | `as Number` | `item.quantity as Number` |
| `items` | `unit_price` | `lineItems[].price` | `as Number` | `item.unit_price as Number` |
| `items` | `$_index + 1` | `lineItems[].seq` | Index (1-based) | `index + 1` |

---

### Pattern 5: String Functions

**DataWeave 2.0:**
```dataweave
country: upper(payload.country_code),
email: lower(trim(payload.email)),
fullName: payload.firstName ++ " " ++ payload.lastName
```

**TDD Section 3.6 Row:**

| Operation | Source | Function | Result | Mule Origin |
|---|---|---|---|---|
| Uppercase | `country_code` | `upper()` | `country` | `upper(payload.country_code)` |
| Lowercase + Trim | `email` | `lower(trim())` | `email` | `lower(trim(payload.email))` |
| Concatenation | `firstName`, `lastName` | `concat(" ")` | `fullName` | `payload.firstName ++ " " ++ payload.lastName` |

---

### Pattern 6: Null / Default Handling

**DataWeave 2.0:**
```dataweave
description: payload.description default "N/A",
quantity: payload.qty default 1
```

**TDD Section 3.2 Row:**

| Source Field | Target Field | Transformation | Type | Mule Origin |
|---|---|---|---|---|
| `description` | `description` | Default `"N/A"` if null | String | `payload.description default "N/A"` |
| `qty` | `quantity` | Default `1` if null | Integer | `payload.qty default 1` |

---

### Pattern 7: Nested Object Construction

**DataWeave 2.0:**
```dataweave
address: {
  street: payload.addr.line1,
  city: payload.addr.city,
  zip: payload.addr.postal_code
}
```

**TDD Section 3.2 Rows (nested expressed with dot notation):**

| Source Field | Target Field | Transformation | Type | Mule Origin |
|---|---|---|---|---|
| `addr.line1` | `address.street` | Direct Copy | String | `payload.addr.line1` |
| `addr.city` | `address.city` | Direct Copy | String | `payload.addr.city` |
| `addr.postal_code` | `address.zip` | Direct Copy | String | `payload.addr.postal_code` |

---

### Pattern 8: Accessing Mule Variables / Attributes in DataWeave

**DataWeave 1.0 (flowVars):**
```dataweave
correlationId: flowVars.correlationId
```

**DataWeave 2.0 (vars):**
```dataweave
correlationId: vars.correlationId
```

**TDD Section 3.2 Row:**

| Source Field | Target Field | Transformation | Type | Mule Origin |
|---|---|---|---|---|
| `header.correlationId` | `correlationId` | Direct Copy (from Camel Header) | String | `vars.correlationId` → maps to Camel `${header.correlationId}` |

**Note:** Mule flow variables (`vars.*`) map to Camel exchange headers (`${header.*}`). When documenting in TDD Section 3.2, use the Camel header notation in the Source Field column.

---

### Pattern 9: Input from HTTP Attributes (Mule 4.x)

**DataWeave 2.0:**
```dataweave
contentType: attributes.headers.'Content-Type',
queryParam: attributes.queryParams.filter
```

**TDD Section 3.2 Row:**

| Source Field | Target Field | Transformation | Type | Mule Origin |
|---|---|---|---|---|
| `header.Content-Type` | `contentType` | Direct Copy | String | `attributes.headers.'Content-Type'` |
| `header.filter` (query param) | `queryParam` | Direct Copy | String | `attributes.queryParams.filter` |

**Note:** Mule `attributes.queryParams.*` are available in Camel as `${header.*}` when using `platform-http` consumer (query params are promoted to headers).

---

## How to Extract Field Mappings from a DataWeave Script

When you encounter a DataWeave script during migration analysis, follow this process:

1. **Identify the output structure** — the top-level keys define target fields.
2. **Trace each value expression** to its source:
   - `payload.x` → source field `x` from message body
   - `vars.x` → source from Camel header `x`
   - `attributes.headers.X` → source from Camel header `X`
   - `attributes.queryParams.x` → source from Camel header `x`
3. **Classify the transformation type:**
   - Literal value → use `constant(...)` in Camel
   - Function call (`upper`, `lower`, `trim`) → Section 3.6
   - Conditional (`if/else`, `when/otherwise`) → Section 3.4
   - Array `map` → Section 3.5
   - Type coercion (`as String`, `as Number`) → Section 3.2 Transformation column
   - Direct copy → Section 3.2 with "Direct Copy"
4. **For scripts too complex to decompose:** Flag them with a comment in the TDD and recommend generating a Groovy or XSLT script manually. Document the intended transformation in plain English so the developer can implement it.

---

## XSLT Generation Note

When the TDD Section 3.2–3.6 tables are complete, `/camel-implement` will read them and generate:
- An XSLT stylesheet (for XML→XML transformations)
- Or a Groovy script skeleton (for JSON transformations not expressible in XSLT)
- Or simple Camel DSL `setBody`/`setHeader` calls (for simple mappings)

The richer and more complete the TDD mapping tables, the more accurate the generated implementation will be.

---

## DataWeave Features Without a Simple Camel Equivalent

| DataWeave Feature | Complexity | Recommended Approach |
|------------------|------------|---------------------|
| `groupBy` | High | Implement as Camel `aggregate` EIP + custom `AggregationStrategy`, or Groovy |
| `reduce` / `fold` | High | Groovy script or custom Camel Processor |
| `distinctBy` | Medium | Groovy or custom Processor |
| `orderBy` | Medium | Groovy or custom Processor |
| `dw::core::Strings` functions | Low–Medium | XSLT string functions or Groovy |
| `dw::core::Arrays` (zip, flatten) | Medium | Groovy or custom Processor |
| `read` / `write` (inline format conversion) | High | Camel `marshal`/`unmarshal` + intermediate route |
| Custom DataWeave modules | High | Must be re-implemented; discuss with development team |

When these patterns are found, add a TODO note in the relevant TDD section and flag for development team attention.
