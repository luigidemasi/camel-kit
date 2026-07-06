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

## Decision Matrix: Which Camel Technology to Use

Choose the Camel approach based on the functoid pattern complexity:

| Functoid Pattern | Camel Technology | Camel Component | Notes |
|---|---|---|---|
| **Direct link** (simple field copy) | `setBody` + Simple language | built-in | No external file needed. Express in design spec section 3.2 as Direct Copy rows. |
| **String Concatenate** | Simple language or XSLT | built-in / `camel-xslt-saxon` | Use Simple for single concat; XSLT for complex. |
| **String Upper/Lower/Trim** | Simple language or XSLT | built-in / `camel-xslt-saxon` | Simple: `${body.field.toUpperCase()}` or XSLT `upper-case()`. |
| **String functions** (Substring, Find, Replace) | XSLT | `camel-xslt-saxon` | Use XSLT `substring()`, `contains()`, `replace()`. |
| **Math functoids** (Add, Multiply, Divide, etc.) | XSLT | `camel-xslt-saxon` | Use XSLT arithmetic expressions. |
| **Logical functoids** (AND, OR, NOT, comparisons) | `choice` EIP + Simple predicates | built-in | Express as routing in design spec section 3.3. |
| **Looping functoid** | `split` EIP | built-in | `split(xpath("//item"))` for XML collections. |
| **Database Lookup functoid** | `enrich` EIP + `sql` component | `camel-sql` | Express in design spec section 3.5 as enrichment step. |
| **Scripting functoid** (C#/VB code) | **Flag for manual review** | Groovy or `camel-groovy` | Suggest Groovy as replacement. Document intent in design spec. |
| **Value Mapping functoid** | XSLT `xsl:choose` | `camel-xslt-saxon` | Lookup table mapping. |
| **Complex multi-functoid chain** | XSLT via Kaoto DataMapper | `camel-xslt-saxon` | Describe field mappings in the flow design; `camel-execute` generates XSLT. |
| **Mass Copy functoid** | Direct body forwarding | built-in | No transformation needed — pass body unchanged. |
| **Iteration functoid** | `split` EIP + per-item processing | built-in | Express in design spec section 3.5 as collection mapping. |
| **Nil Value functoid** | XSLT with `xsi:nil="true"` | `camel-xslt-saxon` | Generate `<element xsi:nil="true"/>` in XSLT. |
| **Record Count functoid** | XSLT `count()` | `camel-xslt-saxon` | Use `count(//element)` in XSLT. |

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

**Note:** Express arithmetic in the Transformation column. The XSLT will use `<xsl:value-of select="Quantity * UnitPrice"/>`.

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

**Flag for Manual Review:** Add a required custom implementation action in the design spec:

```markdown
> **Manual Review Required:** The BizTalk map contains a scripting functoid with custom C# code. This logic must be re-implemented in Groovy or as a custom Camel Processor. Original C# code:
> ```csharp
> public string FormatPhoneNumber(string input) {
>     return input.Replace("-", "").Replace(" ", "");
> }
> ```
```

**Suggested Groovy Replacement:**

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

Alternatively, use XSLT `<xsl:choose>` for cleaner implementation:

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

**Implementation Note:** Use XSLT `<xsl:copy-of select="Address"/>` or document as a direct body copy in Camel if the entire message is being forwarded.

---

### Pattern 11: Nil Value Functoid

**BizTalk Map:** Nil Value functoid setting `OptionalField` to `xsi:nil="true"` when input is missing.

**design spec section 3.2 Row:**

| Source Field | Target Field | Transformation | Type | BizTalk Origin |
|---|---|---|---|---|
| `OptionalField` | `OptionalField` | Set `xsi:nil="true"` if missing | String (nullable) | Nil Value functoid |

**XSLT Equivalent:**

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
5. **Classify each functoid using the Decision Matrix** above.
6. **For scripting functoids:** Extract the inline script code from the `<Script>` element, flag for manual review, and suggest Groovy as a replacement.
7. **Document in design spec section 3.2–3.6 tables** using the pattern examples above.

---

## Functoid Types Quick Reference

| BizTalk Functoid Pattern | Camel Equivalent |
|---|---|
| String Concatenate | Simple or XSLT `concat()` |
| Uppercase / Lowercase | XSLT `upper-case()` / `lower-case()` |
| Logical comparison / AND / OR / NOT | `choice` EIP + Simple predicate |
| Looping / Iteration | `split` EIP |
| Database Lookup | `enrich` EIP + `sql` |
| Scripting | Groovy or custom Processor |
| Value Mapping | XSLT `xsl:choose` |
| Mass Copy | XSLT `xsl:copy-of` |
| Record Count | XSLT `count()` |
| Nil Value | XSLT `xsi:nil="true"` |

See Microsoft BizTalk Server documentation and the map's own metadata for authoritative functoid identifiers.

---

## XSLT Generation Note

When the flow-design mapping tables are complete, `camel-execute` will read them and generate:
- An XSLT stylesheet for the transformation
- Or a Groovy script skeleton (for complex logic not expressible in XSLT)

The richer and more complete the design spec mapping tables, the more accurate the generated implementation will be.

---

## BizTalk Map Features Requiring Manual Review

| BizTalk Feature | Complexity | Recommended Approach |
|---|---|---|
| **Scripting functoid** (C#/VB code) | High | Re-implement in Groovy or custom Processor. Flag for manual review. |
| **Custom XSLT** (inline or external) | Medium | Review and adapt to Camel XSLT component. |
| **Database Lookup functoid** | Medium | Implement as `enrich` EIP + `sql` component. Document SQL query in the design spec. |
| **External Assembly call** | High | Must be re-implemented; discuss with development team. |
| **Cumulative functoids** (running totals) | High | Implement as custom Processor or Groovy script. |
| **Index functoid** | Low | Use XSLT `position()` function. |
| **Iteration functoid** | Medium | Use `split` EIP with per-item processing. |

When these patterns are found, record a required custom implementation action in the relevant design spec section and
flag it for development team attention.

---

## Notes

- Always verify component names in the MCP catalog before writing design spec entries (using `camel_catalog_component_doc`). Pass `runtime` and the full `platformBom` GAV (derived from `.camel-kit/config.properties` per `shared/mcp-setup.md` — the file stores bare versions, not the GAV) on every call, and check the echoed `camelVersion` matches the project version (Iron Law 1).
- BizTalk maps are XSLT-based — most functoids map cleanly to XSLT functions.
- Scripting functoids MUST be flagged for manual review and suggested Groovy replacements.
- Database Lookup functoids require `enrich` EIP + `sql` component in Camel.
- Complex multi-functoid chains should be expressed as field mapping rows in the flow design; `camel-execute` will
  generate the XSLT.
