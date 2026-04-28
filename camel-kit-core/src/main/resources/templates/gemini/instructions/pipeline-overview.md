# Pipeline Overview

## Camel Version

The Apache Camel version for this project is configured in `.camel-kit/config.properties` (`project.camelVersion`).

## Pipeline Commands

Use `{COMMAND_PREFIX}` to invoke camel-kit CLI commands:

| Command | Phase | Subagent |
|---------|-------|----------|
| `/camel:brainstorm` | Discovery | camel-brainstormer |
| `/camel:plan` | Planning | camel-planner |
| `/camel:implement` | Implementation | camel-implementer |
| `/camel:validate` | Validation | camel-validator |
| `/camel:test` | Testing | camel-tester |
| `/camel:migrate` | Migration | camel-migrator |
| `/camel:execute` | Orchestration | Main agent |
