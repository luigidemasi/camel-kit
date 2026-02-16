# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.3] - 2025-02-13

### Added

- **YAML DSL Schema download**: Schema is now automatically fetched and cached during `camel-kit init`
  - Cached alongside component and Kamelet catalogs in `.camel-kit/.cache/camelYamlDsl-{version}.json`
  - Can be refreshed with `camel-kit catalog fetch --force`
  - Schema status shown in `camel-kit catalog info`

### Changed

- **`/camel.implement` now uses component catalog** during YAML generation:
  - New Step 3: Component Catalog Lookup before generating YAML
  - Looks up each component in `.camel-kit/.cache/components-{version}.json`
  - Verifies component exists and can be used as consumer/producer
  - Identifies required vs optional options from `properties[*].required`
  - Determines option placement: `kind: "path"` in URI, `kind: "parameter"` in parameters block
  - Uses `componentProperties` from catalog for `camel.component.<name>.<prop>` configuration
  - Generates Component Verification Report showing catalog lookup results

## [0.1.2] - 2025-02-13

### Added

- **Claude Code support**: Added Anthropic Claude Code as a supported AI agent
  - Commands are generated in Markdown format (`.claude/commands/`)
  - Uses `$ARGUMENTS` placeholder for arguments
  - Requires `claude` CLI tool
- YAML schema validation in `/camel.validate` and `/camel.implement`
  - Schema fetched from GitHub: `https://raw.githubusercontent.com/apache/camel/camel-{version}/dsl/camel-yaml-dsl/camel-yaml-dsl/src/generated/resources/schema/camelYamlDsl.json`
  - Validates syntax, schema compliance, and property placeholders
  - **Auto-fix**: Automatically fixes common validation errors (handled expressions, property case, etc.)
  - Quick validation via `camel run --check <file>.camel.yaml application.properties`

### Changed

- Renamed `/camel.context` to `/camel.project` for clarity
- `/camel.project` now focuses only on business landscape (purpose, systems, integration goals)
- Technical details (sources, sinks, components) moved to `/camel.flow` command
- Removed test generation prompt from `/camel.implement` (use `/camel.test` instead)
- Updated `camel run` examples to include `application.properties` file
- `/camel.implement` now generates `application.properties` with component-level configuration
- `/camel.implement` now generates `camel.jbang.dependencies` in `application.properties` for Maven dependencies
- `/camel.validate` now checks generated YAML files against schema and application.properties
- Updated "Data Format Discipline" constitution principle: unmarshal is now guidance-based (when needed) instead of mandatory
- Clarified validation order: schema validation (JSON Schema, XSD) happens before unmarshal; bean validation after
- **Improved `/camel.test` command**:
  - Testcontainers are now mandatory for external systems (Kafka, PostgreSQL, MongoDB)
  - Added `application.test.properties` generation with testcontainer variables
  - Improved Citrus YAML syntax with correct property names
  - Added testcontainer-exposed variables reference table
  - Better structured test template with infrastructure setup, test execution, and cleanup phases

### Fixed

- Fixed Camel JBang configuration: use `camel.component.<name>.<prop>` for component settings
- Fixed bean definitions: use `#class:` prefix for bean instantiation
- Fixed property loading: `application.properties` must be included in `camel run` command
- Fixed Citrus `camel.jbang.run` YAML schema: use `files` list instead of `integration.file`
- Removed invalid `systemProperties` property from Citrus test examples
- Fixed `onException` YAML syntax: `handled` requires expression format (`constant: expression: "true"`), not boolean

## [0.1.1] - 2025-02-12

### Added

- **Gemini CLI support**: Added Google Gemini CLI as a supported AI agent
  - Commands are generated in TOML format (`.gemini/commands/`)
  - Uses `{{args}}` placeholder for arguments
  - Requires `gemini` CLI tool

### Changed

- Merged `/camel.flow` and `/camel.route` commands into single `/camel.flow` command
- Renamed `/camel.generate` to `/camel.implement` for clarity
- Simplified `/camel.project` to ask only high-level questions (purpose, systems, flows)
- Updated `/camel.flow` to ask questions one at a time interactively
- Technical details (protocols, EIPs, error handling) now captured in `/camel.flow` instead of `/camel.project`

### Fixed

- Fixed Citrus YAML schema issues in `/camel.test`:
  - Variables now use list format with `name`/`value` properties
  - Testcontainers use simple format (`kafka: {}`, `postgresql: {}`)
  - SQL actions use `dataSource` (camelCase) and `statement:` property
  - Removed invalid `wait` property from `camel.jbang.run` action
  - Message body uses `data:` for inline content (not `file:`)
- Added `citrus-camel` dependency to jbang.properties

### Removed

- Removed obsolete `/camel.init` command (replaced by CLI `camel-kit init`)
- Removed separate `/camel.route` command (merged into `/camel.flow`)

## [0.1.0] - 2024-XX-XX

### Added

- Initial release of camel-kit CLI
- Project initialization with `camel-kit init`
- Support for IBM Project Bob AI agent
- Slash commands for AI-assisted integration design:
  - `/camel.init` - Bootstrap project with constitution and catalog
  - `/camel.project` - Define integration landscape
  - `/camel.route` - Design individual routes with EIP guidance
  - `/camel.validate` - Check specifications against catalog and constitution
  - `/camel.test` - Generate Citrus integration tests
  - `/camel.generate` - Output Kaoto-compatible Camel YAML DSL
- Live catalog fetching from Maven Central (components) and GitHub (Kamelets)
- Constitution-based best practices enforcement
- Kaoto-compatible YAML generation
- Citrus test generation with Testcontainers support
- Update mode for re-running context and route commands

### Notes

- Heavily inspired by [GitHub Spec-Kit](https://github.com/github/spec-kit)
- Built for the Apache Camel community

[Unreleased]: https://github.com/luigidemasi/camel-kit/compare/v0.1.3...HEAD
[0.1.3]: https://github.com/luigidemasi/camel-kit/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/luigidemasi/camel-kit/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/luigidemasi/camel-kit/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/luigidemasi/camel-kit/releases/tag/v0.1.0
