# BizTalk Map Conversion Guide

This guide helps convert BizTalk maps (`.btm` files) into flow-design field mapping tables consumed by
`camel-execute`. It is used by the `camel-migrate` skill during Phase 2 (Integration Architect).

> **"Load" means READ and FOLLOW.** When this guide says "Load `path/to/file.md`", read that file relative to the skill directory and execute its instructions. The files are always present — do NOT report them as missing.

---

## BizTalk Map Overview

BizTalk maps are XSLT-based transformations with a visual designer that uses **functoids** (pre-built transformation functions). A BizTalk map consists of:

- **Source schema** — input message structure (XSD)
- **Target schema** — output message structure (XSD)
- **Functoids** — transformation logic (string ops, math, conditionals, loops, database lookups, scripting)
- **Links** — direct field mappings from source to target

---

## Semantic Extraction Before Engine Selection

Do not select an implementation engine per functoid. First extract one complete semantic mapping from the `.btm`:

- Direct links, string/math/value/default/nil/count functoids become typed field-mapping expressions.
- Looping and iteration become collection mappings unless they control the route itself.
- Logical functoids become conditional mappings; only route-level branching becomes a Camel `choice` EIP.
- Database lookups and other external calls become explicit route enrichment steps, not DataMapper expressions.
- Scripting functoids retain their source code and intended input/output semantics for review.

After all fields, types, collection relationships, schema paths, and ambiguities are recorded, load
`shared/datamapper-canonicalize.md` exactly once for the complete map. Preserve its decision: inline Groovy when both
schemas are absent OR there are fewer than 20 leaf fields; XSLT only when there are at least 20 leaf fields AND at least
one schema. Existing custom XSLT remains XSLT and is reviewed/adapted rather than reselected.

---

## Common Functoid Patterns → design spec table Equivalents

### Pattern 1: Direct Link (Field Copy)

**BizTalk Map:** Direct link from `SourceSchema/Order/OrderID` to `TargetSchema/PurchaseOrder/ID`.

**Design Spec Mapping Row:**

| Source Field | Target Field | Transformation | Type | BizTalk Origin |
|---|---|---|---|---|
| `Order/OrderID` | `PurchaseOrder/ID` | Direct Copy | String | Direct link |

---

### Pattern 2: String Concatenate Functoid

**BizTalk Map:** Concatenate functoid linking `FirstName` + `LastName` → `FullName`.

**design spec section 3.4 Row:**

| Operation | Source | Function | Result | BizTalk Origin |
|---|---|---|---|---|
| Concatenation | `FirstName`, `LastName` | `concat(" ")` | `FullName` | String Concatenate functoid |

---

### Pattern 3: String Upper/Lower/Trim Functoids

**BizTalk Map:** Uppercase functoid on `CountryCode` → `Country`.

**design spec section 3.4 Row:**

| Operation | Source | Function | Result | BizTalk Origin |
|---|---|---|---|---|
| Uppercase | `CountryCode` | `upper()` | `Country` | Uppercase functoid |

---

### Pattern 4: Math Functoids (Add, Multiply, etc.)

**BizTalk Map:** Multiply functoid on `Quantity` and `UnitPrice` → `LineTotal`.

**design spec section 3.2 Row:**

| Source Field | Target Field | Transformation | Type | BizTalk Origin |
|---|---|---|---|---|
| `Quantity`, `UnitPrice` | `LineTotal` | `Quantity * UnitPrice` | Number | Multiply functoid |

**Note:** Express arithmetic in the Transformation column. The selected canonical engine implements that semantic
expression.

---

### Pattern 5: Logical Functoids (AND, OR, comparisons) + Conditional Link

**BizTalk Map:** Logical Equal functoid comparing `Status` to `"ACTIVE"`, linked to a conditional link that sets `IsActive` to `true` or `false`.

**design spec section 3.3 Row:**

| Condition | Route | Camel EIP | BizTalk Origin |
|---|---|---|---|
| `Status == "ACTIVE"` | Set `IsActive = true` | choice/when | Logical Equal functoid + conditional link |

Alternatively, express as a conditional mapping in Section 3.2:

| Source Field | Target Field | Transformation | Type | BizTalk Origin |
|---|---|---|---|---|
| `Status` | `IsActive` | `if Status == "ACTIVE" then "true" else "false"` | Boolean | Logical Equal functoid |

---

### Pattern 6: Looping Functoid (Iterate Over Collection)

**BizTalk Map:** Looping functoid on `Items/Item` collection → `LineItems/LineItem`.

**design spec section 3.5 Row:**

| Collection Field | Item Field | Target Field | Transformation | BizTalk Origin |
|---|---|---|---|---|
| `Items/Item` | `ProductCode` | `LineItems/LineItem/SKU` | Direct Copy | Looping functoid + direct link |
| `Items/Item` | `Quantity` | `LineItems/LineItem/Qty` | Direct Copy | Looping functoid + direct link |

---

### Pattern 7: Database Lookup Functoid

**BizTalk Map:** Database Lookup functoid querying `SELECT ProductName FROM Products WHERE ProductCode = ?` with input `ProductCode`.

**design spec section 3.2 Row:**

| Source Field | Target Field | Transformation | Type | BizTalk Origin |
|---|---|---|---|---|
| `ProductCode` | `ProductName` | Database Lookup (`SELECT ProductName FROM Products WHERE ProductCode = ?`) | String | Database Lookup functoid |

**Implementation Note:** Use `enrich` EIP + `sql` component in Camel. Document the SQL query in the Transformation column and flag for implementation in design spec section 3.5.

---

### Pattern 8: Scripting Functoid (C#/VB Code)

**BizTalk Map:** Scripting functoid with custom C# code:

```csharp
public string FormatPhoneNumber(string input) {
    return input.Replace("-", "").Replace(" ", "");
}
```

**design spec section 3.2 Row:**

| Source Field | Target Field | Transformation | Type | BizTalk Origin |
|---|---|---|---|---|
| `PhoneNumber` | `FormattedPhone` | **Manual Review Required** — Custom C# logic: remove `-` and spaces | String | Scripting functoid |

**Flag for Manual Review:** Preserve the semantic input/output behavior and original code in the design spec:

```markdown
> **Manual Review Required:** The BizTalk map contains a scripting functoid with custom C# code. Canonicalize its
> mapping semantics with the complete map. If logic outside the selected DataMapper engine must remain as a custom
> processor, Camel Main is ineligible and the runtime must be Spring Boot or Quarkus. Original C# code:
> ```csharp
> public string FormatPhoneNumber(string input) {
>     return input.Replace("-", "").Replace(" ", "");
> }
> ```
```

**Illustrative replacement only when the canonical engine is Groovy:**

```groovy
exchange.in.setBody(exchange.in.getBody().replaceAll("[-\\s]", ""))
```

---

### Pattern 9: Value Mapping Functoid (Lookup Table)

**BizTalk Map:** Value Mapping functoid mapping `StatusCode` values:

| Source Value | Target Value |
|---|---|
| `A` | `ACTIVE` |
| `I` | `INACTIVE` |
| `P` | `PENDING` |

**design spec section 3.3 Row:**

| Condition | Source Field | Target Field | True Value | False Value | BizTalk Origin |
|---|---|---|---|---|---|
| `StatusCode == "A"` | `StatusCode` | `Status` | `"ACTIVE"` | — | Value Mapping functoid |
| `StatusCode == "I"` | `StatusCode` | `Status` | `"INACTIVE"` | — | Value Mapping functoid |
| `StatusCode == "P"` | `StatusCode` | `Status` | `"PENDING"` | — | Value Mapping functoid |

Only when the approved design selects XSLT (or preserves existing custom XSLT), it may express this semantic as:

```xml
<xsl:choose>
  <xsl:when test="StatusCode = 'A'">ACTIVE</xsl:when>
  <xsl:when test="StatusCode = 'I'">INACTIVE</xsl:when>
  <xsl:when test="StatusCode = 'P'">PENDING</xsl:when>
  <xsl:otherwise>UNKNOWN</xsl:otherwise>
</xsl:choose>
```

---

### Pattern 10: Mass Copy Functoid

**BizTalk Map:** Mass Copy functoid copying entire `Address` subtree to `ShippingAddress`.

**design spec section 3.2 Row:**

| Source Field | Target Field | Transformation | Type | BizTalk Origin |
|---|---|---|---|---|
| `Address/*` | `ShippingAddress/*` | Mass Copy (entire subtree) | Complex | Mass Copy functoid |

**Implementation Note:** Record the subtree-copy semantic for the canonical engine, or document a direct body copy in
Camel if the entire message is forwarded.

---

### Pattern 11: Nil Value Functoid

**BizTalk Map:** Nil Value functoid setting `OptionalField` to `xsi:nil="true"` when input is missing.

**design spec section 3.2 Row:**

| Source Field | Target Field | Transformation | Type | BizTalk Origin |
|---|---|---|---|---|
| `OptionalField` | `OptionalField` | Set `xsi:nil="true"` if missing | String (nullable) | Nil Value functoid |

**Only when XSLT is selected or preserved:**

```xml
<OptionalField xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/>
```

---

### Pattern 12: Record Count Functoid

**BizTalk Map:** Record Count functoid counting `Items/Item` elements → `ItemCount`.

**design spec section 3.2 Row:**

| Source Field | Target Field | Transformation | Type | BizTalk Origin |
|---|---|---|---|---|
| `Items/Item` | `ItemCount` | `count(Items/Item)` | Integer | Record Count functoid |

---

## How to Extract Field Mappings from a BizTalk Map

When you encounter a BizTalk map (`.btm` file) during migration analysis, follow this process:

1. **Read the `.btm` file as XML** — BizTalk maps are stored as XML with `<mapsource>` root element.
2. **Extract source and target schemas** — listed in `<SrcSchemaReference>` and `<TgtSchemaReference>`.
3. **Parse functoids** — located in `<Functoid>` elements. Read the actual `FunctoidType`, `FID`, `Name`, and related attributes from the map; do not infer function identity from a hardcoded numeric table alone.
4. **Trace links** — `<Link>` elements connect source fields, functoids, and target fields via `<SourceID>` and `<TargetID>`.
5. **Classify each functoid semantically** using the rules above; do not select Groovy or XSLT yet.
6. **For scripting functoids:** Extract the inline script code from the `<Script>` element, flag for manual review, and suggest Groovy as a replacement.
7. **Document in design spec section 3.2–3.6 tables** using the pattern examples above.
8. **Count all source and target leaf fields**, record whether each schema is present, then canonicalize the complete
   map once with `shared/datamapper-canonicalize.md` and insert its exact selected DataMapper section.

---

## Functoid Types Quick Reference

| BizTalk Functoid Pattern | Semantic Target Before Canonicalization |
|---|---|
| String Concatenate | Typed concatenation mapping |
| Uppercase / Lowercase | Typed string-function mapping |
| Logical comparison / AND / OR / NOT | `choice` EIP + Simple predicate |
| Looping / Iteration | `split` EIP |
| Database Lookup | `enrich` EIP + `sql` |
| Scripting | Preserved code plus reviewed input/output semantics |
| Value Mapping | Conditional/value-table mapping |
| Mass Copy | Subtree-copy mapping |
| Record Count | Collection-count mapping |
| Nil Value | Nullable/default mapping |

See Microsoft BizTalk Server documentation and the map's own metadata for authoritative functoid identifiers.

---

## Conditional Engine Generation

When the flow-design mapping tables are complete, `shared/datamapper-canonicalize.md` selects the engine and
`camel-execute` generates that exact inline Groovy or XSLT implementation. The engine decision is based on schemas and
leaf-field count, not on individual functoid names.

The richer and more complete the design spec mapping tables, the more accurate the generated implementation will be.

---

## BizTalk Map Features Requiring Manual Review

| BizTalk Feature | Complexity | Recommended Approach |
|---|---|---|
| **Scripting functoid** (C#/VB code) | High | Preserve semantics/code, canonicalize with the complete map, and flag any remaining custom processor for runtime-safe review. |
| **Custom XSLT** (inline or external) | Medium | Review and adapt to Camel XSLT component. |
| **Database Lookup functoid** | Medium | Implement as `enrich` EIP + `sql` component. Document SQL query in the design spec. |
| **External Assembly call** | High | Must be re-implemented; discuss with development team. |
| **Cumulative functoids** (running totals) | High | Preserve the running-total semantic for canonicalization; flag any remaining custom processor for runtime-safe review. |
| **Index functoid** | Low | Record the collection-index semantic for the selected engine. |
| **Iteration functoid** | Medium | Use `split` EIP with per-item processing. |

When these patterns are found, record a required custom implementation action in the relevant design spec section and
flag it for development team attention.

---

## Notes

- Always verify component names in the MCP catalog before writing design spec entries (using `camel_catalog_component_doc`). Pass `runtime` and the full `platformBom` GAV (derived from `.camel-kit/config.properties` per `shared/mcp-setup.md` — the file stores bare versions, not the GAV) on every call, and check the echoed `camelVersion` matches the project version (Iron Law 1).
- BizTalk maps are XSLT-based source artifacts, but new implementations follow the canonical Groovy-or-XSLT rule.
- Scripting functoids MUST be flagged for manual review; do not select Groovy before complete-map canonicalization.
- Database Lookup functoids require `enrich` EIP + `sql` component in Camel.
- Complex multi-functoid chains should be expressed as semantic field-mapping rows; `camel-execute` generates the
  canonical engine selected for the complete map.
