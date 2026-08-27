# Security Analysis Guide

> **Context variables provided by master SKILL.md:**
> - `FLOW_NAME` — the flow being validated
> - `CAMEL_VERSION` — from `.camel-kit/config.properties`
> - `RUNTIME` — project runtime from `.camel-kit/config.properties`
> - `PLATFORM_BOM` — resolved from `CAMEL_VERSION` + `RUNTIME` via the version mapping table in `skills/shared/mcp-setup.md`
>
> **Version mapping:** When calling MCP catalog tools, translate `CAMEL_VERSION` + `RUNTIME` to the correct `camelVersion` and `platformBom` parameters using the version mapping table in `skills/shared/mcp-setup.md`.

## Stage 8: Security Analysis (MCP Enhanced)

**This is the most powerful MCP integration - 47 automated security checks!**

### 8.1 MCP Security Analysis

**If tool call succeeds:**

```
== SECURITY ANALYSIS (MCP - 47 Checks) ==

Running comprehensive security scan...

MCP Tool: camel_route_harden_context
Params: {
  "route": "[route-yaml-content]",
  "camelVersion": "{{CAMEL_VERSION}}",
  "platformBom": "{{PLATFORM_BOM}}",
  "runtime": "{{RUNTIME}}"
}

Analyzing route for security vulnerabilities...
```

**MCP checks include:**

**Hardcoded Credentials (Critical):**
```
✅ No hardcoded passwords found
✅ No API keys in route
✅ No OAuth tokens hardcoded
✅ No database credentials in YAML
```

**Insecure Protocols (High Risk):**
```
⚠️ WARNING: HTTP endpoint detected
   Line 42: to: http://{{api.endpoint}}
   Risk: Unencrypted communication
   Fix: Change to https://{{api.endpoint}}

✅ Kafka SSL configured
✅ Database connections use SSL
```

**SQL Injection Risks (High Risk):**
```
✅ Using parameterized queries
✅ No string concatenation in SQL
```

**Encryption Issues:**
```
✅ TLS/SSL enabled for messaging
✅ Database connections encrypted
```

**Authentication:**
```
✅ Kafka SASL authentication configured
⚠️ HTTP endpoint: No authentication detected
   Consider adding OAuth2 or API key authentication
```

**PII and Sensitive Data:**
```
⚠️ WARNING: Logging full message body at line 28
   Risk: May expose PII or sensitive data
   Fix: Log only message ID or specific fields
```

**MCP Security Summary:**
```
== SECURITY SCAN RESULTS ==

Critical Issues: 0
High Risk: 0
Warnings: 3
  1. HTTP instead of HTTPS (line 42)
  2. No authentication on HTTP endpoint (line 42)
  3. Logging full body may expose PII (line 28)

Passed Checks: 44/47

Recommendation: Fix warnings before production deployment
```

### 8.2 Fallback: Manual Anti-Pattern Detection

**If the tool call fails:**

```
MCP tool call failed. Loading manual anti-pattern guide...
→ Reading guides/anti-patterns.md

Running manual security checks...
```

Then apply manual checks from anti-patterns guide.
