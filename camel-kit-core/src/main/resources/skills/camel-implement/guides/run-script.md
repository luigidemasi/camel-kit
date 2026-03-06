# Run Script Generation Guide (JBang Only)

This guide generates `run.sh` for JBang runtime only.

**Context variables:** `FLOW_NAME`, `MODULE_DIR`.

---

## Mandatory Rules for run.sh

| Rule | Detail |
|------|--------|
| JBang alias | Use `jbang camel@apache/camel run` -- **NOT** `org.apache.camel:camel-jbang:VERSION:runner` (non-existent Maven artifact) and **NOT** bare `camel run` (requires global install) |
| XSL files | Include `*.xsl` (or list each file) in the `camel run` arguments -- omitting them causes `FileNotFoundException` |
| Properties | Pass via `--properties=application.properties` |

## run.sh Template

Adapt to actual file names:

```bash
#!/bin/bash
# ============================================
# Run Script for {FLOW_NAME}
# ============================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if ! command -v jbang &>/dev/null; then
  echo "ERROR: jbang not found. Install from https://www.jbang.dev/installation/" >&2
  exit 1
fi

echo "Starting {FLOW_NAME} integration..."
jbang camel@apache/camel run {FLOW_NAME}.camel.yaml *.xsl --properties=application.properties
```

Make it executable:

```bash
chmod +x run.sh
```

**File location:** Use `MODULE_DIR` for file location.
