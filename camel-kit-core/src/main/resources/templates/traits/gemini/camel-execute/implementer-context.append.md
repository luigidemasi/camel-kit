## Agent Optimization: Gemini CLI

### Batch Guide Pre-Loading

Before constructing the implementer prompt, use `read_many_files` to load all implementation guides in a single call:

- Load: `camel-implement/guides/orchestrator.md`, `camel-implement/guides/yaml-structure.md`, `camel-implement/guides/yaml-catalog-rules.md`, `camel-implement/guides/component-loading.md`, `camel-implement/guides/properties-generation.md`
- Include only the guides relevant to the current task (check the task description for which patterns are needed)

### Named Agent Delegation

Delegate the implementation to the `camel-implementer` pre-registered agent. Include `max_turns: 50` to prevent excessive iteration.
