# Test Runner Guide

> **Context variables provided by master SKILL.md:**
> - `FLOW_NAME` — the flow being tested
> - `RUNNER_DIR` — resolved runner script directory

---

## Step 5: Generate Test Runner Script

**IMPORTANT: Save this file in the correct directory based on runtime!**

Create file: `{RUNNER_DIR}run-tests.sh` (make it executable with chmod +x)

```bash
#!/bin/bash
# ============================================
# Test Runner for {flow-name}
# ============================================

set -e

echo "Running integration tests for {flow-name}..."

# Ensure Docker is running (required for Testcontainers)
if ! docker info > /dev/null 2>&1; then
  echo "❌ Docker is not running. Testcontainers requires Docker."
  exit 1
fi

echo "✓ Docker is running"

# Run Citrus tests
echo "Starting Citrus tests..."

citrus run tests/{flow-name}.camel.it.yaml

# Or using Maven:
# ./mvnw test -Dtest={flow-name}IntegrationTest

echo "✅ All tests passed"
```

Make executable:
```bash
chmod +x {RUNNER_DIR}run-tests.sh
```
