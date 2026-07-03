# Camel 2.x/3.x → 4.x Data Format Name Mapping

This guide contains **name mappings only**. All data format options MUST be retrieved from the MCP catalog at runtime using `camel_catalog_dataformat_doc`.

## How to Use This Guide

1. For each data format in `<marshal>`/`<unmarshal>` blocks or `.marshal()`/`.unmarshal()` calls, check the table below
2. If found, use the mapped 4.x name
3. If not found, the name may be unchanged — verify with `camel_catalog_dataformats`
4. After mapping, call `camel_catalog_dataformat_doc` to verify all options

## Data Format Renames

In Camel 3.x and 4.x, many data format names were normalized to camelCase and the `json-` prefix was dropped.

| 2.x / 3.x Name | 4.x Name | Notes |
|----------------|----------|-------|
| `json-jackson` | `jackson` | Prefix `json-` removed |
| `json-gson` | `gson` | Prefix `json-` removed |
| `json-johnzon` | `johnzon` | Prefix `json-` removed |
| `json-fastjson` | *(removed)* | Use `jackson` |
| `json-xstream` | *(removed)* | Use `jackson` |
| `zip` (deflate) | `zipDeflater` | Disambiguated from `zipFile` — `zip` was ambiguous |
| `zipFile` | `zipFile` | Unchanged |
| `gzip` | `gzipDeflater` | Renamed for clarity |
| `tarfile` | `tarFile` | camelCase normalization |
| `yaml-snakeyaml` | `snakeYaml` | Prefix removed, camelCase |
| `mime-multipart` | `mimeMultipart` | Hyphen → camelCase |
| `csv` | `csv` | Unchanged — verify options via MCP |
| `bindy-csv` | `bindyCsv` | Hyphen → camelCase |
| `bindy-fixed` | `bindyFixed` | Hyphen → camelCase |
| `bindy-kvp` | `bindyKvp` | Hyphen → camelCase |
| `boon` | *(removed)* | Use `jackson` |
| `jibx` | *(removed)* | Use `jaxb` or `jacksonXml` |
| `xstream` | *(removed)* | Stop and choose a safe replacement such as `jaxb`, `jacksonXml`, or JSON Jackson |
| `jacksonxml` | `jacksonXml` | camelCase normalization |
| `protobuf` | `protobuf` | Unchanged — verify via MCP |
| `avro` | `avro` | Unchanged — verify via MCP |
| `thrift` | `thrift` | Unchanged — verify via MCP |

## XML DSL Changes for Data Formats

In Camel 2.x Spring XML, data formats were declared as:

```xml
<marshal>
  <json library="Jackson"/>
</marshal>
```

In Camel 4.x YAML DSL, they are:

```yaml
- marshal:
    json:
      library: Jackson
```

The catalog data format name `jackson` maps to the YAML model key `json` with `library: Jackson`.

## Removed Data Formats

| Data Format | Removed In | Replacement |
|------------|-----------|-------------|
| `boon` | 3.0 | Use `jackson` |
| `jibx` | 3.0 | Use `jaxb` or `jacksonXml` |
| `json-fastjson` | 4.0 | Use `jackson` |
| `json-xstream` | 4.0 | Use `jackson` |
| `xstream` | 4.0 | Use `jaxb`, `jacksonXml`, or JSON Jackson after confirming the target format |
| `json-johnzon` | Still available — verify via MCP | May be removed in future |
