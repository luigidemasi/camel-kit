# BizTalk Expression Mapping Guide

This guide maps BizTalk XLANG/s expressions (used in orchestration shapes) to their Apache Camel equivalents. It is used by the `camel-migrate` skill during Phase 2 (Integration Architect).

> **"Load" means READ and FOLLOW.** When this guide says "Load `path/to/file.md`", read that file relative to the skill directory and execute its instructions. The files are always present — do NOT report them as missing.

---

## XLANG/s Overview

XLANG/s is the expression language used in BizTalk orchestrations. It is based on C# syntax with extensions for:
- Message context property access
- XPath queries on XML messages
- Distinguished field access
- Type conversions

---

## XLANG/s Expression → Camel Expression Mapping

| XLANG/s Pattern | XLANG/s Example | Camel Equivalent | Camel Language |
|---|---|---|---|
| **Message context property** | `message(BTS.MessageID)` | `${exchangeProperty.MessageID}` | Simple |
| **Promoted property** | `message(namespace.PropertyName)` | `${header.PropertyName}` | Simple |
| **Distinguished field** | `message.Order.OrderID` | `${body.Order.OrderID}` | Simple (for JSON/XML via JsonPath/XPath) |
| **Orchestration variable** | `myVar` | `${header.myVar}` | Simple |
| **Orchestration parameter** | `param1` | `${header.param1}` | Simple |
| **Logical AND** | `status == "ACTIVE" && amount > 100` | `${header.status} == 'ACTIVE' && ${header.amount} > 100` | Simple |
| **Logical OR** | `status == "ACTIVE" \|\| status == "PENDING"` | `${header.status} == 'ACTIVE' \|\| ${header.status} == 'PENDING'` | Simple |
| **Equality** | `status == "ACTIVE"` | `${header.status} == 'ACTIVE'` | Simple |
| **Inequality** | `status != "INACTIVE"` | `${header.status} != 'INACTIVE'` | Simple |
| **Greater than** | `amount > 100` | `${header.amount} > 100` | Simple |
| **Less than** | `amount < 50` | `${header.amount} < 50` | Simple |
| **Greater or equal** | `amount >= 100` | `${header.amount} >= 100` | Simple |
| **Less or equal** | `amount <= 50` | `${header.amount} <= 50` | Simple |
| **Type cast (int)** | `(int)amount` | Groovy: `amount as int` | Groovy |
| **Type cast (string)** | `(string)orderID` | Groovy: `orderID as String` | Groovy |
| **Type cast (double)** | `(double)price` | Groovy: `price as double` | Groovy |
| **XPath query** | `xpath(message, "//Order/OrderID")` | `${xpath(//Order/OrderID)}` | XPath |
| **XPath query (namespaced)** | `xpath(message, "//ns:Order/ns:OrderID")` | `${xpath(//ns:Order/ns:OrderID)}` | XPath (with namespace registry) |
| **String .ToUpper()** | `status.ToUpper()` | `${header.status.toUpperCase()}` | Simple |
| **String .ToLower()** | `status.ToLower()` | `${header.status.toLowerCase()}` | Simple |
| **String .Trim()** | `name.Trim()` | `${header.name.trim()}` | Simple |
| **String .Substring()** | `code.Substring(0, 3)` | Groovy: `code.substring(0, 3)` | Groovy |
| **String .Replace()** | `phone.Replace("-", "")` | Groovy: `phone.replace("-", "")` | Groovy |
| **String concatenation** | `firstName + " " + lastName` | `${header.firstName} ${header.lastName}` | Simple |
| **Arithmetic (add)** | `quantity + 10` | Groovy: `headers.quantity + 10` | Groovy or custom Processor |
| **Arithmetic (subtract)** | `amount - discount` | Groovy: `headers.amount - headers.discount` | Groovy or custom Processor |
| **Arithmetic (multiply)** | `quantity * unitPrice` | Groovy: `headers.quantity * headers.unitPrice` | Groovy or custom Processor |
| **Arithmetic (divide)** | `total / count` | Groovy: `headers.total / headers.count` | Groovy or custom Processor |
| **Null check** | `status == null` | `${header.status} == null` | Simple |
| **Not null check** | `status != null` | `${header.status} != null` | Simple |
| **Ternary operator** | `status == null ? "PENDING" : status` | `${header.status} ?: 'PENDING'` | Simple (Elvis operator) |
| **Multi-line C# code block** | `if (x > 10) { ... }` | `bean()` or Groovy `process()` | Groovy or custom Processor |

---

## Common XLANG/s Expression Patterns

### Pattern 1: Message Context Property Access

**XLANG/s:**
```csharp
correlationId = message(BTS.InterchangeID);
```

**Camel Equivalent:**
```java
.setHeader("correlationId", exchangeProperty("InterchangeID"))
```

**Simple Expression:**
```
${exchangeProperty.InterchangeID}
```

---

### Pattern 2: Promoted Property Access

**XLANG/s:**
```csharp
customerID = message(Schema.CustomerID);
```

**Camel Equivalent:**
```java
.setHeader("customerID", header("CustomerID"))
```

**Simple Expression:**
```
${header.CustomerID}
```

---

### Pattern 3: Distinguished Field Access

**XLANG/s:**
```csharp
orderID = message.Order.OrderID;
```

**Camel Equivalent (JSON via JsonPath):**
```java
.setHeader("orderID", jsonpath("$.Order.OrderID"))
```

**Camel Equivalent (XML via XPath):**
```java
.setHeader("orderID", xpath("//Order/OrderID"))
```

---

### Pattern 4: Conditional Expression (Logical AND/OR)

**XLANG/s:**
```csharp
if (status == "ACTIVE" && amount > 100) {
    // ...
}
```

**Camel Equivalent:**
```java
.choice()
    .when(simple("${header.status} == 'ACTIVE' && ${header.amount} > 100"))
        // ... processing steps
    .otherwise()
        // ... alternative processing
.end()
```

---

### Pattern 5: XPath Query on Message

**XLANG/s:**
```csharp
orderID = xpath(message, "//Order/OrderID");
```

**Camel Equivalent:**
```java
.setHeader("orderID", xpath("//Order/OrderID"))
```

**With Namespaces:**
```java
.setHeader("orderID", xpath("//ns:Order/ns:OrderID")
    .namespace("ns", "http://schemas.example.com/order"))
```

---

### Pattern 6: Type Conversion

**XLANG/s:**
```csharp
intAmount = (int)amount;
stringOrderID = (string)orderID;
```

**Camel Equivalent (Groovy):**
```groovy
def intAmount = amount as int
def stringOrderID = orderID as String
```

**Or using Simple with type converter:**
```java
.setHeader("intAmount", simple("${header.amount}", Integer.class))
```

---

### Pattern 7: String Functions

**XLANG/s:**
```csharp
upperStatus = status.ToUpper();
lowerCountry = country.ToLower();
trimmedName = name.Trim();
```

**Camel Equivalent (Simple):**
```java
.setHeader("upperStatus", simple("${header.status.toUpperCase()}"))
.setHeader("lowerCountry", simple("${header.country.toLowerCase()}"))
.setHeader("trimmedName", simple("${header.name.trim()}"))
```

---

### Pattern 8: Multi-line C# Code Block

**XLANG/s:**
```csharp
if (orderTotal > 1000) {
    discountPercent = 10;
    priority = "HIGH";
} else {
    discountPercent = 5;
    priority = "NORMAL";
}
```

**Camel Equivalent (Groovy):**
```groovy
.process { exchange ->
    def orderTotal = exchange.in.getHeader("orderTotal", Integer.class)
    if (orderTotal > 1000) {
        exchange.in.setHeader("discountPercent", 10)
        exchange.in.setHeader("priority", "HIGH")
    } else {
        exchange.in.setHeader("discountPercent", 5)
        exchange.in.setHeader("priority", "NORMAL")
    }
}
```

**Note:** Multi-line C# code blocks should be flagged for manual review and documented in the design spec with the original XLANG/s code and suggested Groovy replacement.

---

### Pattern 9: Ternary Operator (Null Coalescing)

**XLANG/s:**
```csharp
status = (orderStatus == null) ? "PENDING" : orderStatus;
```

**Camel Equivalent (Simple — Elvis operator):**
```java
.setHeader("status", simple("${header.orderStatus} ?: 'PENDING'"))
```

---

### Pattern 10: Arithmetic Operations

**XLANG/s:**
```csharp
lineTotal = quantity * unitPrice;
finalAmount = subtotal - discount;
```

**Camel Equivalent (Groovy language or custom Processor):**
```java
.setHeader("lineTotal", groovy("headers.quantity * headers.unitPrice"))
.setHeader("finalAmount", groovy("headers.subtotal - headers.discount"))
```

---

## BizTalk Message Context Properties → Camel Equivalents

| BizTalk Context Property | Camel Equivalent | Notes |
|---|---|---|
| `BTS.MessageID` | `${exchangeProperty.MessageID}` | Unique message identifier |
| `BTS.InterchangeID` | `${exchangeProperty.InterchangeID}` | Interchange correlation ID |
| `BTS.ReceivePortName` | `${header.ReceivePortName}` | Source port name |
| `BTS.SendPortName` | `${header.SendPortName}` | Sink port name |
| `BTS.RouteLabel` | `${header.RouteLabel}` | Routing label |
| `BTS.RetryCount` | `${header.RetryCount}` | Retry attempt count |
| `BTS.InboundTransportLocation` | `${header.InboundTransportLocation}` | Source endpoint URI |
| `BTS.OutboundTransportLocation` | `${header.OutboundTransportLocation}` | Sink endpoint URI |
| Custom promoted property | `${header.PropertyName}` | Promoted from message schema |

---

## When to Use Which Camel Expression Language

| Scenario | Recommended Language | Notes |
|---|---|---|
| Simple header/property access | Simple | `${header.x}`, `${exchangeProperty.y}` |
| Simple arithmetic/comparison | Simple | `${header.x} > 100` |
| String functions (upper/lower/trim) | Simple | `${header.x.toUpperCase()}` |
| XPath query on XML | XPath | `xpath("//Order/OrderID")` |
| JsonPath query on JSON | JsonPath | `jsonpath("$.Order.OrderID")` |
| Type conversion | Groovy | `header.x as int` |
| Multi-line logic | Groovy or custom Processor | Use Groovy `process { }` block |
| Complex string manipulation | Groovy | `header.x.replace("-", "").substring(0, 3)` |

---

## How to Map XLANG/s Expressions in design spec

When you encounter an XLANG/s expression in a BizTalk orchestration, follow this process:

1. **Identify the expression context** — Message Assignment shape, Decide shape condition, Expression shape, etc.
2. **Classify the expression type** using the table above.
3. **Map to the appropriate Camel language** (Simple, XPath, JsonPath, Groovy).
4. **Document in the design spec processing steps** with the BizTalk origin noted in the "BizTalk Origin" column.
5. **For multi-line C# code blocks:** Flag for manual review, document the original XLANG/s code as a required custom implementation action, and suggest a Groovy replacement.

---

## XLANG/s Features Requiring Manual Review

| XLANG/s Feature | Complexity | Recommended Approach |
|---|---|---|
| **Multi-line C# code block** | High | Re-implement in Groovy or custom Processor. Flag for manual review. |
| **External .NET assembly call** | High | Must be re-implemented; discuss with development team. |
| **Complex XPath with variables** | Medium | Re-implement in Camel XPath with namespace registry. |
| **Custom functions** | High | Re-implement in Groovy or custom Processor. |
| **Correlation set manipulation** | Medium | Map to Camel correlation ID patterns (use `exchangeProperty`). |

When these patterns are found, record a required custom implementation action in the relevant design spec section and
flag it for development team attention.

---

## Notes

- Always verify expression language names in the MCP catalog before writing design spec entries (using `camel_catalog_language_doc`). Pass `runtime` and `platformBom` from `.camel-kit/config.properties` on every call, and check the echoed `camelVersion` matches the project version (Iron Law 1).
- BizTalk orchestration variables map to Camel exchange headers (`${header.*}`).
- BizTalk message context properties map to Camel exchange properties (`${exchangeProperty.*}`).
- Multi-line C# code blocks MUST be flagged for manual review and suggested Groovy replacements.
- XPath queries require namespace registration in Camel if the XML uses namespaces.
