# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.1] - 2025-02-12

### Changed

- Merged `/camel.flow` and `/camel.route` commands into single `/camel.flow` command
- Renamed `/camel.generate` to `/camel.implement` for clarity
- Simplified `/camel.context` to ask only high-level questions (purpose, systems, flows)
- Updated `/camel.flow` to ask questions one at a time interactively
- Technical details (protocols, EIPs, error handling) now captured in `/camel.flow` instead of `/camel.context`

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
  - `/camel.context` - Define integration landscape
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

[Unreleased]: https://github.com/luigidemasi/camel-kit/compare/v0.1.1...HEAD
[0.1.1]: https://github.com/luigidemasi/camel-kit/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/luigidemasi/camel-kit/releases/tag/v0.1.0
