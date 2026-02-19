# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).


## [Unreleased]

### Added

- **Skills-based architecture with MCP integration**
  - Converted 5 commands to skills standard with YAML frontmatter and metadata
  - Commands now use kebab-case naming: `/camel-project`, `/camel-flow`, `/camel-implement`, `/camel-validate`, `/camel-test`
  - All skills are user-invocable and discoverable by AI agents
  - On-demand guide loading for token optimization (60-70% token savings)
  - Bundled component skills structure for offline use

- **Apache Camel MCP Server Integration**
  - Automatic project-specific MCP configuration during `camel-kit init`
  - Support for 3 AI agents: Claude Code (`.mcp.json`), IBM Bob (`.bob/mcp.json`), Gemini CLI (`.gemini/mcp.json`)
  - 15 MCP tools available, 7 actively used across skills
  - Real-time catalog queries: `camel_catalog_components`, `camel_catalog_component_doc`
  - Route validation: `camel_validate_route`, `camel_route_context`
  - Security analysis: `camel_route_harden_context` with 47 automated checks
  - Version management: `camel_version_list`
  - 60-70% token savings compared to loading full catalog
  - Always-current documentation matching exact Camel version

- **Comprehensive Data Transformation & Field Mapping (Kaoto DataMapper)**
  - Interactive schema-based field mapping in `/camel-flow`
  - Support for both XML Schema (XSD) and JSON Schema
  - Automatic field name matching and automapping proposals
  - Nested field handling (e.g., `order.customer.name` → `customer.name`)
  - Detailed field mapping tables in TDD with transformation types
  - Parameter support for Camel Variables and Message Headers
  - Conditional mappings: IF and CHOOSE-WHEN-OTHERWISE
  - Collection processing with FOR-EACH and position tracking
  - Comprehensive XPath function library (string, numeric, date/time, boolean)

- **Automatic XSLT Generation**
  - Generate Kaoto-compatible DataMapper XSLT from TDD field mappings
  - File naming: `{flow-name}-datamapper-{random-8-char-id}.xsl`
  - XSLT 2.0 for XML transformations, XSLT 3.0 for JSON
  - Support for all transformation types:
    - Direct copy, nested flattening, date/time formatting
    - String concatenation, numeric calculations
    - Conditional logic (IF, CHOOSE-WHEN-OTHERWISE)
    - Array iteration with position tracking
    - Parameter usage from Camel context
  - JSON transformation with `fn:json-to-xml()` and `fn:xml-to-json()`
  - XML namespace preservation and handling
  - Automatic integration in route YAML with xslt-saxon component
  - Parameter passing from route to XSLT
  - Best practices and limitations guidance

- **Documentation**
  - `docs/MCP-TOOLS-REFERENCE.md` - Comprehensive MCP tools documentation with 23 invocation points
  - Updated all documentation with MCP integration details
  - Updated all slash command references to kebab-case
  - Enhanced transformation sections in user guide
  - Added MCP configuration examples for all 3 agents

### Changed

- **InitCommand improvements**
  - Create MCP configs only for selected AI agent (not all 3)
  - Fixed JAR filesystem handling for bundled skill distribution
  - Skills copied to both `.bob/commands/` (flat) and `.bob/skills/` (full structure)
  - Clean project initialization without unnecessary files
  - **Removed redundant catalog downloads** - Component and Kamelet catalogs are no longer downloaded during init
    - MCP server queries catalogs in real-time from Maven Central and GitHub
    - YAML DSL schema not needed (Maven validator plugin downloads its own)
    - Only Citrus schemas are downloaded (used by `/camel-test` skill)
    - Faster init, smaller `.camel-kit/.cache/` folder
    - 60-70% reduction in cached data

- **camel-implement skill - Route validation with MCP**
  - Replaced Maven YAML DSL Validator with MCP `camel_validate_route` tool
  - Validates all endpoint URIs against Camel catalog in real-time
  - Checks component options and required parameters
  - Catches typos and suggests corrections automatically
  - Consistent MCP-first approach throughout workflow
  - Faster validation feedback, no Maven execution needed
  - Renumbered steps: Step 5 is now Route Validation with MCP, Steps 6-11 adjusted

- **File generation locations corrected**
  - All generated routes now in project root (NOT in `.camel-kit/`)
  - `{flow-name}.camel.yaml` → Project root
  - `application.properties` → Project root
  - `docker-compose.yaml` → Project root
  - `run.sh` → Project root (executable)
  - XSLT files → Project root (same folder as route)
  - Test files → `test/` directory in project root
  - Schemas → `schemas/` directory in project root
  - `.camel-kit/` reserved ONLY for internal metadata

- **Skills structure enhanced**
  - `/camel-flow` now captures detailed field mappings, parameters, and conditional logic
  - `/camel-implement` generates DataMapper XSLT automatically
  - All skills include explicit file location instructions
  - TDD template expanded to 7 sections for transformations:
    - Section 3.2: Field Mappings
    - Section 3.3: Transformation Parameters
    - Section 3.4: Conditional Mappings
    - Section 3.5: Collection/Array Mappings
    - Section 3.6: Transformation Rules
    - Section 3.7: Additional Processing Steps (EIPs)

- **Documentation updates**
  - All command references changed from `/camel.X` to `/camel-X` throughout
  - README.md updated with MCP features and transformation capabilities
  - docs/user-guide.md enhanced with MCP Integration section
  - docs/commands.md updated with MCP tools by command
  - docs/skills-architecture.md updated with MCP + skills comparison
  - examples/order-processing/README.md updated with kebab-case commands
  - CONTRIBUTING.md updated with skills-based template format

### Fixed

- MCP configuration generation now creates only the config for the selected agent
- Project initialization no longer creates backup/temporary files
- Skills are properly distributed from JAR filesystem to project folders
- File paths in skills explicitly state project root vs .camel-kit folder

## [0.2.0] - 2025-02-18

### Added
- **Camel version updated to 4.18.0** (LTS)

- **Camel-Kit logo** - Added camel-kit.gif logo inspired by K.I.T.T. from Knight Rider
  - Displayed at the top of README.md
  - Represents AI-guided integration development

- **Enhanced error handling guidance** in constitution and design patterns:
  - Three exception handling approaches: `doTry/doCatch/doFinally`, `errorHandler`, `onException`
  - Error handler types: `noErrorHandler`, `defaultErrorHandler`, `deadLetterChannel`
  - `onException` clause with `handled()`, `continued()`, `markRollbackOnly()`
  - Detailed examples for each approach

- **Transaction handling patterns**:
  - Transaction propagation policies (PROPAGATION_REQUIRED, PROPAGATION_REQUIRES_NEW, etc.)
  - Using `.transacted()` DSL for transaction management
  - Combining transactions with exception handling via `markRollbackOnly`
  - Examples for local and distributed transactions

- **Kafka consumer scaling guidance**:
  - Consumer-to-partition relationship and assignment rules
  - Starving consumer scenarios and optimal configurations
  - `consumersCount` parameter usage with Kubernetes replicas
  - Offset reset strategies (earliest, latest, none)

- **Kubernetes deployment best practices**:
  - ConfigMaps and Secrets patterns for configuration
  - Health probes (liveness, readiness, startup)
  - Resource requests and limits configuration
  - Configuration hierarchy (environment variables, ConfigMaps, defaults)

### Changed

- **Rewritten in Java** - Complete rewrite from Python to Java for better JBang integration
  - Multi-module Maven project structure (camel-kit-core, camel-kit-main, camel-kit-plugins)
  - Installation via JBang: `jbang app install camel-kit@io.github.luigidemasi:camel-kit-main:0.2.0-SNAPSHOT`
  - Uses PicoCLI for command-line parsing
  - Uses JLine for terminal handling

- **Camel version updated to 4.14.5** (LTS)

- **Citrus version updated to 4.9.2**

- **Maven Wrapper included in generated projects**
  - `mvnw` (Unix) and `mvnw.cmd` (Windows) generated during init
  - Enables portable Maven execution without pre-installed Maven

- **Validation uses MCP and Maven plugins**:
  - Camel route validation: MCP `camel_validate_route` tool (validates URIs, options, catches typos)
  - Citrus test validation: `./mvnw com.dataliquid.maven:json-yaml-validator-maven-plugin:2.0.0:validate`

- **Citrus JSON schemas downloaded during init**
  - Schemas extracted from `citrus-catalog-schema` JAR on Maven Central
  - Cached in `.camel-kit/.cache/citrus/{version}/`
  - Quick reference files generated for AI agent consumption

- **Updated constitution.md** - Renumbered sections after adding transaction handling
  - Section 4: Enhanced error handling with three approaches
  - Section 7: New transaction handling section
  - Section 15: New Kafka consumer scaling section
  - Section 16: New Kubernetes deployment section

- **Updated design-patterns.md**:
  - Enhanced Data Integrity Pattern with transaction propagation policies
  - Enhanced Service Instance Pattern with Kafka consumer scaling details
  - Added offset reset strategies and Kubernetes scaling patterns

- **Updated docs/constitution.md**:
  - Added principles 11-13 (Transaction Handling, Kafka Consumer Scaling, Kubernetes Deployment)
  - Added validation codes CONST-009 through CONST-011
  - Enhanced error handling section with three approaches

- **Rewrote CONTRIBUTING.md**:
  - Changed from Python development to Java/Maven development
  - Updated prerequisites (Java 17+, JBang, Maven)
  - Updated build commands (./mvnw instead of uv/pip)
  - Changed coding standards from Python/PEP 8 to Java conventions
  - Updated testing from pytest to JUnit 5
  - Changed contribution types (commands/templates instead of agents)

- **Updated README.md**:
  - Added camel-kit.gif logo at the top
  - Better visual presentation with centered logo

### Fixed

- Template consistency across all locations (templates/, camel-kit-core/src/main/resources/templates/, src/camel_kit_cli/templates/)

### Removed

- **Python implementation** - Replaced with Java/JBang
- **`camel-kit catalog` command** - Catalogs are downloaded during init and cached
- **`camel-kit agents` command** - Agent information available via `--help`
- **`camel-kit version` command** - Use `camel-kit --help` for version info

## [0.1.3] - 2025-02-13

### Added

- **YAML DSL Schema download**: Schema is now automatically fetched and cached during `camel-kit init`
  - Cached alongside component and Kamelet catalogs in `.camel-kit/.cache/camelYamlDsl-{version}.json`
  - Can be refreshed with `camel-kit catalog fetch --force`
  - Schema status shown in `camel-kit catalog info`

### Changed

- **`/camel-implement` now uses component catalog** during YAML generation:
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
- YAML schema validation in `/camel-validate` and `/camel-implement`
  - Schema fetched from GitHub: `https://raw.githubusercontent.com/apache/camel/camel-{version}/dsl/camel-yaml-dsl/camel-yaml-dsl/src/generated/resources/schema/camelYamlDsl.json`
  - Validates syntax, schema compliance, and property placeholders
  - **Auto-fix**: Automatically fixes common validation errors (handled expressions, property case, etc.)
  - Quick validation via `camel run --check <file>.camel.yaml application.properties`

### Changed

- Renamed `/camel-context` to `/camel-project` for clarity
- `/camel-project` now focuses only on business landscape (purpose, systems, integration goals)
- Technical details (sources, sinks, components) moved to `/camel-flow` command
- Removed test generation prompt from `/camel-implement` (use `/camel-test` instead)
- Updated `camel run` examples to include `application.properties` file
- `/camel-implement` now generates `application.properties` with component-level configuration
- `/camel-implement` now generates `camel.jbang.dependencies` in `application.properties` for Maven dependencies
- `/camel-validate` now checks generated YAML files against schema and application.properties
- Updated "Data Format Discipline" constitution principle: unmarshal is now guidance-based (when needed) instead of mandatory
- Clarified validation order: schema validation (JSON Schema, XSD) happens before unmarshal; bean validation after
- **Improved `/camel-test` command**:
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

- Merged `/camel-flow` and `/camel-route` commands into single `/camel-flow` command
- Renamed `/camel-generate` to `/camel-implement` for clarity
- Simplified `/camel-project` to ask only high-level questions (purpose, systems, flows)
- Updated `/camel-flow` to ask questions one at a time interactively
- Technical details (protocols, EIPs, error handling) now captured in `/camel-flow` instead of `/camel-project`

### Fixed

- Fixed Citrus YAML schema issues in `/camel-test`:
  - Variables now use list format with `name`/`value` properties
  - Testcontainers use simple format (`kafka: {}`, `postgresql: {}`)
  - SQL actions use `dataSource` (camelCase) and `statement:` property
  - Removed invalid `wait` property from `camel.jbang.run` action
  - Message body uses `data:` for inline content (not `file:`)
- Added `citrus-camel` dependency to jbang.properties

### Removed

- Removed obsolete `/camel-init` command (replaced by CLI `camel-kit init`)
- Removed separate `/camel-route` command (merged into `/camel-flow`)

## [0.1.0] - 2024-XX-XX

### Added

- Initial release of camel-kit CLI
- Project initialization with `camel-kit init`
- Support for IBM Project Bob AI agent
- Slash commands for AI-assisted integration design:
  - `/camel-init` - Bootstrap project with constitution and catalog
  - `/camel-project` - Define integration landscape
  - `/camel-route` - Design individual routes with EIP guidance
  - `/camel-validate` - Check specifications against catalog and constitution
  - `/camel-test` - Generate Citrus integration tests
  - `/camel-generate` - Output Kaoto-compatible Camel YAML DSL
- Live catalog fetching from Maven Central (components) and GitHub (Kamelets)
- Constitution-based best practices enforcement
- Kaoto-compatible YAML generation
- Citrus test generation with Testcontainers support
- Update mode for re-running context and route commands

### Notes

- Heavily inspired by [GitHub Spec-Kit](https://github.com/github/spec-kit)
- Built for the Apache Camel community

[Unreleased]: https://github.com/luigidemasi/camel-kit/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/luigidemasi/camel-kit/compare/v0.1.3...v0.2.0
[0.1.3]: https://github.com/luigidemasi/camel-kit/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/luigidemasi/camel-kit/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/luigidemasi/camel-kit/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/luigidemasi/camel-kit/releases/tag/v0.1.0
