# Schema Generation Guide

This guide generates JSON schemas. Only loaded when schemas were missing and user chose to generate them.

**Context variables:** `FLOW_NAME`, `SCHEMA_DIR`.

---

## Input Schema

From TDD Section 2.3 (Data Contract - Input):

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "{FLOW_NAME} Input Schema",
  "type": "object",
  "properties": {
    "field1": {
      "type": "[type from TDD example]",
      "description": ""
    }
  },
  "required": ["field1"]
}
```

**File location:** Save to `SCHEMA_DIR/{FLOW_NAME}-input.json`.

## Output Schema

From TDD Section 3.3 or 4.3 (Data Contract - Output):

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "{FLOW_NAME} Output Schema",
  "type": "object",
  "properties": {
    "field1": {
      "type": "[type from TDD]",
      "description": ""
    }
  },
  "required": ["field1"]
}
```

**File location:** Save to `SCHEMA_DIR/{FLOW_NAME}-output.json`.
