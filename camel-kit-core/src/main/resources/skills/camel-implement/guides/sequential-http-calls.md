# Sequential HTTP Calls & State Management

## 📌 Context
When generating Apache Camel YAML DSL code that involves **two or more HTTP/S endpoint calls within the same route**, you must ALWAYS manage the lifecycle of the Exchange headers and state.
By default, Camel propagates message headers. If not explicitly cleared, response or request headers from the first endpoint (including security tokens and query parameters) will leak into the second endpoint, causing HTTP 400, 401, 403 errors. Furthermore, to handle API responses without polluting or overwriting the current Message Body, you must utilize **Camel Variables** with the `variableReceive` feature (introduced in Camel 4.4+).

## 🤖 Agent Workflow & MCP Tool Usage
You are connected to the `camel-jbang-mcp` server. Follow this strict workflow:
1. **Verify (Optional):** Call `CatalogTools` to inspect the `http` component or EIP documentation if unsure about specific attributes (like `variableReceive`).
2. **Generate:** Draft the YAML DSL code following the "Mandatory Rules" below.
3. **Validate (Required):** Call the `ValidateTools` passing your generated YAML. Fix any syntax or compilation errors before proceeding.
4. **Security Check (Required):** Call the `HardenTools` on your generated route. Ensure the tool does not flag any header leakage between the HTTP calls.
5. **Output:** Present the final, validated YAML to the user.

## 🚨 Mandatory Rules for the Agent

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

## 💻 YAML DSL Code Example (Best Practice Template)

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
            uri: "[https://api.system-one.com/v1/users](https://api.system-one.com/v1/users)"
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
            uri: "[https://api.system-two.com/v2/process](https://api.system-two.com/v2/process)"
            
        - log: "Processing completed successfully."