# Pipeline Overview

## Camel Version

This project uses Apache Camel version `{camelVersion}`.

## Pipeline Commands

Use `{commandPrefix}` to invoke camel-kit CLI commands:

| Command | Phase | Subagent |
|---------|-------|----------|
| `/camel:brainstorm` | Discovery | camel-brainstormer |
| `/camel:plan` | Planning | camel-planner |
| `/camel:implement` | Implementation | camel-implementer |
| `/camel:validate` | Validation | camel-validator |
| `/camel:test` | Testing | camel-tester |
| `/camel:migrate` | Migration | camel-migrator |
| `/camel:execute` | Orchestration | Main agent |
