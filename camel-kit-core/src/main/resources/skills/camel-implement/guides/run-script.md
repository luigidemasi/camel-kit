# Run Script Generation Guide (JBang Only)

This guide generates `run.sh` for JBang runtime only.

**Context variables:** `MODULE_NAME`, `MODULE_DIR`, `ROUTE_FILES` (every module `.camel.yaml` file), `XSL_FILES` (every
module XSLT DataMapper file).

---

## Mandatory Rules for run.sh

| Rule | Detail |
|------|--------|
| JBang alias | Use `jbang camel@apache/camel run` -- **NOT** `org.apache.camel:camel-jbang:VERSION:runner` (non-existent Maven artifact) and **NOT** bare `camel run` (requires global install) |
| Route files | List every file in `ROUTE_FILES` in the single `camel run` command |
| XSL files | List every file in `XSL_FILES` in the same command; omit XSL arguments only when the list is empty |
| Properties | Pass via `--properties=application.properties` |

## run.sh Template

Adapt to actual file names:

```bash
#!/bin/bash
# ============================================
# Run Script for {MODULE_NAME}
# ============================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if ! command -v jbang &>/dev/null; then
  echo "ERROR: jbang not found. Install from https://www.jbang.dev/installation/" >&2
  exit 1
fi

echo "Starting {MODULE_NAME} integration..."
jbang camel@apache/camel run \
  {route-file-1}.camel.yaml {route-file-N}.camel.yaml \
  {xslt-file-1}.xsl {xslt-file-N}.xsl \
  --properties=application.properties
```

Expand `ROUTE_FILES` and `XSL_FILES` completely and remove the illustrative `-1`/`-N` placeholders. Never reduce the
script to the active flow. When `XSL_FILES` is empty, omit that continued line entirely.

Make it executable:

```bash
chmod +x {MODULE_DIR}run.sh
```

**File location:** Use `MODULE_DIR` for file location.
