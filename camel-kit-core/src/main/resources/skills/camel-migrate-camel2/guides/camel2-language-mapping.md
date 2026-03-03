# Camel 2.x/3.x → 4.x Expression Language Mapping

This guide contains **name mappings and syntax changes only**. All language options MUST be retrieved from the MCP catalog at runtime using `camel_catalog_language_doc`.

## How to Use This Guide

1. For each expression language used in routes (conditions, transformations, predicates), check the tables below
2. Apply name renames and syntax changes
3. Verify with `camel_catalog_languages` that the language exists in 4.x
4. Call `camel_catalog_language_doc` to verify options

## Language Renames

| 2.x / 3.x Name | 4.x Name | Notes |
|----------------|----------|-------|
| `property` | `exchangeProperty` | Renamed to avoid ambiguity with system/component properties |
| `spel` | *(removed)* | Spring Expression Language removed. Use `simple`, `groovy`, or `bean` instead. |

## Simple Language Syntax Changes

The Simple expression language is the most commonly used. Key syntax changes:

| 2.x / 3.x Syntax | 4.x Syntax | Notes |
|------------------|-----------|-------|
| `$simple{expression}` | `${expression}` | `$simple{}` prefix deprecated — use `${}` directly |
| `${property.X}` | `${exchangeProperty.X}` | Property access renamed |
| `${in.header.X}` | `${header.X}` | `in.` prefix removed (no more IN/OUT distinction) |
| `${in.body}` | `${body}` | `in.` prefix removed |
| `${out.header.X}` | `${header.X}` | OUT message concept removed in 4.x |
| `${out.body}` | `${body}` | OUT message concept removed |
| `${exception.message}` | `${exception.message}` | Unchanged |
| `${exchangeId}` | `${exchangeId}` | Unchanged |
| `${routeId}` | `${routeId}` | Unchanged |

## XPath Changes

| 2.x / 3.x | 4.x | Notes |
|-----------|-----|-------|
| `xpath` | `xpath` | Name unchanged |
| Saxon dependency | `camel-xpath` (built-in) or `camel-xslt-saxon` | Verify via MCP |
| `resultType` attribute | `resultType` | Unchanged — verify via MCP |

## Removed Languages

| Language | Removed In | Replacement |
|---------|-----------|-------------|
| `spel` | 4.0 | Use `simple` for basic expressions, `groovy` for complex logic, `bean` for method calls |
| `mvel` | 4.0 | Use `simple` or `groovy` |
| `terser` | 4.0 | No direct replacement — use custom processor |
| `jxpath` | 3.0 | Use `xpath` |

## Expression Context Changes (Java DSL)

| 2.x / 3.x | 4.x | Notes |
|-----------|-----|-------|
| `exchange.getIn().getBody()` | `exchange.getMessage().getBody()` | `getIn()` deprecated |
| `exchange.getOut().setBody()` | `exchange.getMessage().setBody()` | OUT removed — modify message in-place |
| `exchange.getProperty("X")` | `exchange.getProperty("X")` | Unchanged |
| `exchange.getIn().getHeader("X")` | `exchange.getMessage().getHeader("X")` | `getIn()` deprecated |
