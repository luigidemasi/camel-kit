# Sequential HTTP Calls & State Management

## Context
When generating Apache Camel YAML DSL code that involves **two or more HTTP/S endpoint calls within the same route**, 
you must ALWAYS manage the lifecycle of the Exchange headers and state.
By default, Camel propagates message headers. If not explicitly cleared, response or request headers from the first 
endpoint (including security tokens and query parameters) will leak into the second endpoint, causing HTTP 400, 401, 403 errors. 
Furthermore, to handle API responses without polluting or overwriting the current Message Body, 
you must utilize **Camel Variables** with the `variableReceive` feature (introduced in Camel 4.4+).

---

## HTTP Component Quick Reference

Camel provides multiple HTTP-related components. Choose based on role (consumer vs. producer) and runtime.

### Consumer Components (used in `from:` — expose HTTP endpoints)

| Component | URI Format | Best For |
|-----------|-----------|---------|
| `platform-http` | `platform-http:/path` | **Preferred** for Quarkus and Spring Boot — delegates to the platform's native server (RESTEasy Reactive / Spring MVC) |
| `servlet` | `servlet:/contextPath` | WAR deployments inside an existing servlet container |
| `jetty` | `jetty:http://host:port/path` | Standalone Jetty server, no external container needed |
| `undertow` | `undertow:http://host:port/path` | Standalone Undertow server; also supports WebSocket |
| `netty-http` | `netty-http:http://host:port/path` | High-performance Netty server; also supports HTTPS and WebSocket |

> **Rule:** Consumer components cannot be used in `to:`. They only appear in `from:`.

### Producer Components (used in `to:` — make outbound HTTP calls)

| Component | URI Format | Best For |
|-----------|-----------|---------|
| `http` | `http://host/path` | Standard outbound HTTP calls — **default choice** |
| `https` | `https://host/path` | TLS/SSL outbound calls — same component as `http`, different scheme |
| `undertow` | `undertow:http://host:port/path` | Outbound calls using Undertow client |
| `netty-http` | `netty-http:http://host:port/path` | Outbound calls using Netty client (high-performance) |
| `vertx-http` | `vertx-http:http://host/path` | Outbound calls using Vert.x HTTP client (reactive) |

> **Rule:** The mandatory header-sanitization rules below apply to **all producer components**, not just `http`. Whenever any of the components above appears in a `to:` step, treat it the same way.

### Consumer + Producer (both roles)

`undertow` and `netty-http` can appear in both `from:` and `to:` in the same project. When used as a producer in a route that already has an HTTP consumer, the sanitization rules still apply.

---

## Agent Workflow & MCP Tool Usage
You are connected to the `camel-jbang-mcp` server. Follow this strict workflow:
1. **Verify (Optional):** Call `CatalogTools` to inspect the `http` component or EIP documentation if unsure about specific attributes (like `variableReceive`).
2. **Generate:** Draft the YAML DSL code following the "Mandatory Rules" below.
3. **Validate (Required):** Call the `ValidateTools` passing your generated YAML. Fix any syntax or compilation errors before proceeding.
4. **Security Check (Required):** Call the `HardenTools` on your generated route. Ensure the tool does not flag any header leakage between the HTTP calls.
5. **Output:** Present the final, validated YAML to the user.

## Mandatory Rules for the Agent

### 1. Prevent Query Parameter Leakage (`CamelHttpQuery`)
After an HTTP call, internal Camel headers remain in the Exchange.
* **Action:** Before making a subsequent HTTP call, always remove routing-related headers, specifically `CamelHttpQuery`, `CamelHttpUri`, `CamelHttpPath`, and `CamelHttpMethod`.

### 2. Authorization Token Sanitization
Security tokens MUST NOT accidentally propagate between different APIs.
* **Action:** Explicitly remove the `Authorization` header (and custom headers like `X-Api-Key`) between calls.

### 3. Zero-Interference Responses (`variableReceive`)
Making an HTTP call normally overwrites the current Message Body with the HTTP response. If the original Body is needed later, or if you want to avoid payload pollution, use the `variableReceive` attribute on the `to` EIP.
* **Action:** Use `variableReceive: "variableName"` on the `to` step to store the HTTP response directly into a Camel Variable. The original Message Body will remain untouched.

### 4. Safe State Cleanup Pattern (`removeHeaders`)
Use the `removeHeaders` EIP right before setting up the request for the second `<to>` endpoint. A wildcard pattern (`CamelHttp*`) is the safest approach.

## YAML DSL Code Example (Best Practice Template)

```yaml
- route:
    id: "sequential-http-calls-route"
    from:
      uri: "direct:start"
      steps:
        # Initial state: a body that we want to preserve
        - setBody:
            constant: '{"originalRequest": "importantData"}'

        # 1. Setup FIRST HTTP call
        - setHeader:
            name: "CamelHttpMethod"
            constant: "GET"
        - setHeader:
            name: "CamelHttpQuery"
            constant: "status=active"
        - setHeader:
            name: "Authorization"
            constant: "Bearer token_for_api_one"
            
        # 2. Execute FIRST call and isolate response via variableReceive
        - to: 
            uri: "https://api.system-one.com/v1/users"
            variableReceive: "FirstApiResponse"

        # 3. HEADER SANITIZATION (Crucial Step!)
        - removeHeaders:
            pattern: "CamelHttp*"
        - removeHeader:
            name: "Authorization"

        # 4. Setup and execute SECOND HTTP call (Clean State)
        - setHeader:
            name: "CamelHttpMethod"
            constant: "POST"
        # We inject data from the variable into a new header, while the 
        # original body '{"originalRequest": "importantData"}' is safely sent as the POST body
        - setHeader:
            name: "X-User-Context"
            simple: "${variable.FirstApiResponse}"
        - to:
            uri: "https://api.system-two.com/v2/process"
            
        - log: "Processing completed successfully."
```

---

## Producer Component URI Notes

The same pattern (removeHeaders + removeHeader + variableReceive) applies to **all** producer components: `http`/`https`, `undertow`, `netty-http`, `vertx-http`. Only the URI scheme changes — the sanitization rules are identical.

---

## When the Route Source is an HTTP Consumer

When a route is triggered by an HTTP consumer (`platform-http`, `servlet`, `jetty`, `undertow`, `netty-http`), the incoming HTTP request headers — including `Authorization`, `Content-Type`, `CamelHttpUri`, `CamelHttpQuery` — are already present on the Exchange before the first `to:` call.

**This means header leakage can happen from the inbound request into the first outbound call**, not just between two outbound calls.

Apply the same sanitization pattern before the very first `to:` producer step:

```yaml
- route:
    id: "platform-http-to-backend-route"
    from:
      uri: "platform-http:/api/orders"
      steps:
        # Inbound HTTP headers (Authorization, CamelHttpQuery, etc.) are now on the Exchange.
        # Sanitize before calling any backend.
        - removeHeaders:
            pattern: "CamelHttp*"
        - removeHeader:
            name: "Authorization"

        # Now set up a clean outbound call
        - setHeader:
            name: "CamelHttpMethod"
            constant: "GET"
        - setHeader:
            name: "Authorization"
            constant: "Bearer {{backend.token}}"
        - to:
            uri: "https://backend.example.com/orders"
            variableReceive: "backendResponse"

        - setBody:
            simple: "${variable.backendResponse}"
```

The same applies for `servlet`, `jetty`, `undertow` (as consumer), and `netty-http` (as consumer) in place of `platform-http`.
