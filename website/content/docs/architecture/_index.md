---
title: Architecture
weight: 4
---

Internal architecture documentation for contributors and extenders.

Camel-Kit combines two mechanisms to give AI agents accurate, efficient access to the Apache Camel ecosystem:

1. **Skills** — bundled component documentation loaded on-demand during implementation (complete schemas, usage patterns)
2. **MCP Server** — real-time queries against the live Camel catalog during design and validation (lightweight, always current)

Together they achieve **99% reduction** in context usage compared to loading all 396 component catalogs.

{{< cards >}}
  {{< card link="skills" title="Skills Architecture" subtitle="How component skills work and progressive disclosure" >}}
  {{< card link="mcp" title="MCP Integration" subtitle="MCP tools, invocation flow, and token savings" >}}
{{< /cards >}}
