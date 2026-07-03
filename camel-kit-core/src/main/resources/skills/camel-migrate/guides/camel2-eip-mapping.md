# Camel 2.x/3.x → 4.x EIP Name & Attribute Mapping

This guide contains **name and attribute mappings only**. All EIP options and syntax details MUST be retrieved from the MCP catalog at runtime using `camel_catalog_eip_doc`.

## How to Use This Guide

1. For each EIP used in a source route, check the tables below
2. If found, apply the rename or attribute change
3. If not found, the EIP name is likely unchanged — verify with `camel_catalog_eips`
4. After mapping, call `camel_catalog_eip_doc` to verify all attributes/options

## EIP Renames (3.x → 4.x)

| 3.x EIP | 4.x EIP | Notes |
|---------|---------|-------|
| `hystrix` | `circuitBreaker` | Netflix Hystrix removed. Resilience4j is the default implementation. |
| `hystrixConfiguration` | `resilience4jConfiguration` | Configuration element renamed |
| `onFallback` (under hystrix) | `onFallback` (under circuitBreaker) | Same element name, different parent |

## EIP Attribute Renames (3.x → 4.x)

These EIPs keep the same element name but had attributes renamed in Camel 4.0.

| EIP | Old Attribute | New Attribute | Example |
|-----|--------------|---------------|---------|
| `setHeader` | `headerName` | `name` | `<setHeader name="myHeader">` |
| `setProperty` | `propertyName` | `name` | `<setProperty name="myProp">` |
| `removeHeader` | `headerName` | `name` | `<removeHeader name="myHeader"/>` |
| `removeHeaders` | `pattern` | `pattern` | Unchanged — verify via MCP |
| `removeProperty` | `propertyName` | `name` | `<removeProperty name="myProp"/>` |
| `convertBodyTo` | `type` | `type` | Unchanged — verify via MCP |
| `enrich` | `strategyRef` | `aggregationStrategy` | Attribute renamed |
| `pollEnrich` | `strategyRef` | `aggregationStrategy` | Attribute renamed |
| `split` | `strategyRef` | `aggregationStrategy` | Attribute renamed |
| `aggregate` | `strategyRef` | `aggregationStrategy` | Attribute renamed |
| `multicast` | `strategyRef` | `aggregationStrategy` | Attribute renamed |
| `recipientList` | `strategyRef` | `aggregationStrategy` | Attribute renamed |

## Removed EIPs

| EIP | Removed In | Replacement |
|-----|-----------|-------------|
| `hystrix` | 4.0 | `circuitBreaker` with `resilience4jConfiguration` |

## XML Namespace Changes

| 2.x / 3.x | 4.x |
|-----------|-----|
| `http://camel.apache.org/schema/spring` | YAML DSL (no XML namespace needed) |
| `http://camel.apache.org/schema/blueprint` | YAML DSL (Blueprint removed entirely) |

## Java DSL API Changes (2.x → 4.x)

These apply to Java DSL route definitions in `RouteBuilder` classes:

| 2.x/3.x API | 4.x API | Notes |
|-------------|---------|-------|
| `simple("${property.X}")` | `simple("${exchangeProperty.X}")` | `property` language renamed |
| `exchange.getIn().getHeader()` | `exchange.getMessage().getHeader()` | `getIn()` deprecated, use `getMessage()` |
| `exchange.getOut()` | `exchange.getMessage()` | `getOut()` removed in 4.x |
| `processor.process(exchange)` | Same | API unchanged |
| `new DefaultExchange(context)` | `ExchangeBuilder.anExchange(context).build()` | Constructor removed |
| `exchange.getProperty(Exchange.CHARSET_NAME)` | Verify via MCP — some Exchange constants removed | |
