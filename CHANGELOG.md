# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/luigidemasi/camel-kit/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/luigidemasi/camel-kit/releases/tag/v0.1.0
