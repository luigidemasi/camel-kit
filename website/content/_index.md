---
title: Camel-Kit
layout: hextra-home
---

<div style="display: flex; align-items: center; gap: 3rem; margin-top: 2rem; margin-bottom: 2rem; flex-wrap: wrap; justify-content: center;">
  <div style="flex-shrink: 0;">
    <img src="images/camel-kit.gif" alt="Camel-Kit" style="width: 280px; border-radius: 12px; box-shadow: 0 8px 30px rgba(0, 0, 0, 0.12);" />
  </div>
  <div style="flex: 1; min-width: 280px;">

{{< hextra/hero-headline >}}
  Design and Migrate Apache Camel&nbsp;<br class="sm:block hidden" />Integrations with AI
{{< /hextra/hero-headline >}}

{{< hextra/hero-subtitle >}}
  Structured slash commands for Claude Code, IBM Project Bob, and Gemini CLI&nbsp;<br class="sm:block hidden" />that guide you through designing, implementing, and testing Camel routes.
{{< /hextra/hero-subtitle >}}

<div class="hx:mb-12 hx:mt-6 hx:flex hx:gap-3">
{{< hextra/hero-button text="Get Started" link="docs/getting-started" >}}
{{< hextra/hero-button text="GitHub" link="https://github.com/luigidemasi/camel-kit" style="background: transparent; border: 1px solid #e5e7eb; color: #374151;" >}}
</div>

  </div>
</div>

<div class="hx:mt-6"></div>

{{< hextra/feature-grid >}}
  {{< hextra/feature-card
    title="AI-Guided Design"
    subtitle="Slash commands guide you through flow design, implementation, validation, and testing. One flow = one route."
    icon="sparkles"
  >}}
  {{< hextra/feature-card
    title="Platform Migration"
    subtitle="Migrate from MuleSoft Mule or upgrade from Camel 2.x/3.x to 4.x. Auto-detects source, maps components, converts transformations."
    icon="switch-horizontal"
  >}}
  {{< hextra/feature-card
    title="MCP-Powered"
    subtitle="Real-time catalog queries and validation via the Apache Camel MCP Server. 60-70% token savings."
    icon="lightning-bolt"
  >}}
  {{< hextra/feature-card
    title="Kaoto Compatible"
    subtitle="Generated YAML opens directly in the Kaoto visual editor. DataMapper XSLT included."
    icon="eye"
  >}}
  {{< hextra/feature-card
    title="Multi-Agent Support"
    subtitle="Works with Claude Code, IBM Project Bob, and Gemini CLI. Same skills, same workflow."
    icon="users"
  >}}
  {{< hextra/feature-card
    title="Validated Output"
    subtitle="Every generated route is catalog-verified. URI validation, 47 security checks, schema validation."
    icon="shield-check"
  >}}
{{< /hextra/feature-grid >}}

<div class="hx:mt-16"></div>

{{< hextra/hero-section >}}
  Quick Install
{{< /hextra/hero-section >}}

```bash
# Install via JBang
jbang app install camel-kit@luigidemasi/camel-kit

# Or run without installing
jbang run camel-kit@luigidemasi/camel-kit init my-project --ai claude

# Or as a Camel JBang plugin
camel plugin add kit \
  --gav io.github.luigidemasi:camel-kit-jbang-plugin:0.3.1 \
  -d "Design Apache Camel Integrations with AI"
```

{{< hextra/hero-section >}}
  Workflow
{{< /hextra/hero-section >}}

```mermaid
flowchart TB
    subgraph CLI
        A[camel-kit init]
    end
    subgraph "Greenfield"
        B["/camel-project<br/>(optional)"]
        C["/camel-flow"]
    end
    subgraph "Migration"
        M["/camel-migrate"]
    end
    subgraph "Shared"
        D["/camel-implement"]
        V["/camel-validate"]
        T["/camel-test"]
    end
    subgraph Output
        E["flow-name.camel.yaml"]
    end

    A --> B --> C --> D
    A --> M --> D
    D --> V --> T --> E
```
