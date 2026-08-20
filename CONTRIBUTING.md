# Contributing to Camel-Kit

Thank you for your interest in contributing to Camel-Kit! This document provides guidelines and information for contributors.

> **Note:** Camel-Kit is heavily inspired by [GitHub Spec-Kit](https://github.com/github/spec-kit). When contributing, we encourage you to explore the spec-kit project to understand the design philosophy that guides this project. The spec-kit team's innovative work on spec-driven development with AI assistants has been foundational to our approach.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [How to Contribute](#how-to-contribute)
- [Pull Request Process](#pull-request-process)
- [Coding Standards](#coding-standards)
- [Testing](#testing)
- [Documentation](#documentation)
- [Adding New Skills](#adding-new-skills)
- [Release Process](#release-process)

## Code of Conduct

This project follows the [Apache Software Foundation Code of Conduct](https://www.apache.org/foundation/policies/conduct.html). Please be respectful and constructive in all interactions.

## Getting Started

### Prerequisites

- Java 17 or higher
- [JBang](https://www.jbang.dev/) (for running the CLI)
- Maven 3.9+ (included via Maven Wrapper)
- Git

### Fork and Clone

1. Fork the repository on GitHub
2. Clone your fork:
   ```bash
   git clone https://github.com/YOUR_USERNAME/camel-kit.git
   cd camel-kit
   ```
3. Add upstream remote:
   ```bash
   git remote add upstream https://github.com/luigidemasi/camel-kit.git
   ```

## Development Setup

### Building the Project

```bash
# Portable build of all modules using Maven Wrapper
./mvnw clean install

# CI always certifies packaged Ship payloads on Linux; reproduce it locally on a Linux host
./mvnw -B -Plinux-ship-certification clean install

# Run CLI using JBang (from jbang-catalog.json)
jbang camel-kit@. --help

# Or run directly from the main JBang script
jbang camel-kit-main/src/main/jbang/main/CamelKit.java --help

# Run tests
./mvnw test

# Package for distribution
./mvnw package

# Install locally (after build)
./mvnw install
jbang app install --force camel-kit@./
```

### IDE Setup

For IntelliJ IDEA or Eclipse:
1. Import as Maven project
2. Enable annotation processing for Picocli
3. Set JDK 17 or higher as project SDK

## Project Structure

```
camel-kit/
├── camel-kit-main/              # Main JBang CLI module
│   └── src/main/jbang/main/
│       └── CamelKit.java        # JBang entry point
├── camel-kit-core/              # Core functionality module
│   └── src/main/java/io/github/luigidemasi/camelkit/
│       ├── CamelKitMain.java    # Picocli CLI main class
│       ├── command/             # CLI command implementations
│       ├── catalog/             # Catalog fetching logic
│       ├── config/              # Configuration handling
│       ├── output/              # Output formatting (TUI, logo)
│       └── util/                # Utility classes
│   └── src/main/resources/
│       ├── distribution.properties  # Single source of truth for all config defaults
│       ├── skills/              # Skill definitions (markdown instruction files)
│       │   ├── camel-start/       # Meta-router and primary entry point
│       │   ├── camel-design/      # Design-phase reference guides (internal, loaded by camel-brainstorm)
│       │   ├── camel-brainstorm/  # Interactive design session
│       │   ├── camel-plan/        # Implementation planning
│       │   ├── camel-execute/     # Orchestrated execution
│       │   ├── camel-migrate/     # Migration orchestrator
│       │   ├── camel-verify/      # Runtime verification
│       │   ├── camel-implement/   # YAML generation (internal)
│       │   ├── camel-validate/    # Quality review (internal)
│       │   ├── camel-test/        # Test generation (internal)
│       │   ├── camel-knowledge/   # Camel docs (internal)
│       │   └── shared/            # Shared guides (iron laws, DataMapper, MCP)
│       └── templates/           # Agent-specific instruction templates
│           ├── bob/             # IBM Project Bob
│           ├── claude/          # Anthropic Claude Code
│           ├── gemini/          # Google Gemini CLI
│           ├── qwen/            # Qwen
│           ├── opencode/        # OpenCode
│           └── traits/          # Agent-specific trait files (appended to skills at init)
│               ├── claude/      # Claude Code traits
│               ├── gemini/      # Gemini CLI traits
│               ├── bob/         # IBM Bob traits
│               ├── qwen/        # Qwen traits
│               └── opencode/    # OpenCode traits
├── camel-jbang-plugin-kit/      # Camel JBang plugin
├── camel-kit-graph/             # Project graph analysis (9 parsers)
│   │   # Parsers: JavaClassParser, CamelRouteParser, MavenPomParser,
│   │   # PropertiesParser, DockerComposeParser, OpenApiParser,
│   │   # MuleXmlFlowParser, DataWeaveParser, BizTalkParser
├── camel-kit-plugins/           # Plugin extensions
├── docs/                        # Documentation
│   ├── user-guide.md            # End-user walkthrough
│   ├── commands.md              # Command reference
│   ├── architecture.md          # Contributor guide
│   └── constitution.md          # Route quality rules
├── distribution.properties      # Root config (source of truth, copied into JAR)
├── examples/                    # Usage examples
├── pom.xml                      # Parent Maven POM
├── jbang-catalog.json           # JBang catalog definition
├── mvnw, mvnw.cmd               # Maven Wrapper scripts
├── README.md
├── CONTRIBUTING.md
└── LICENSE
```

## How to Contribute

### Types of Contributions

1. **Bug Reports** - Open an issue describing the bug
2. **Feature Requests** - Open an issue describing the feature
3. **Documentation** - Improve docs, fix typos, add examples
4. **Code** - Fix bugs, implement features, improve tests
5. **New Commands** - Add new slash commands for AI assistants
6. **Templates** - Enhance constitution, design patterns, or command templates

### Issue Guidelines

Before opening an issue:
1. Search existing issues to avoid duplicates
2. Use the appropriate issue template
3. Provide clear reproduction steps for bugs
4. Include relevant version information

### Branch Naming

- `feature/description` - New features
- `fix/description` - Bug fixes
- `docs/description` - Documentation changes
- `command/command-name` - New slash commands
- `template/template-name` - Template enhancements

## Pull Request Process

### Before Submitting

1. **Sync with upstream:**
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Run tests:**
   ```bash
   ./mvnw test
   ```

3. **Build the project:**
   ```bash
   # Portable build
   ./mvnw clean install

   # CI always certifies packaged Ship payloads on Linux; reproduce it locally on a Linux host
   ./mvnw -B -Plinux-ship-certification clean install
   ```

4. **Test the CLI:**
   ```bash
   jbang camel-kit@. init
   ```

### Submitting

1. Create a descriptive PR title
2. Fill out the PR template
3. Link related issues
4. Request review from maintainers

### PR Checklist

- [ ] Tests pass locally
- [ ] Linting passes
- [ ] Documentation updated (if applicable)
- [ ] CHANGELOG.md updated (for significant changes)
- [ ] Commit messages are clear and descriptive

## Coding Standards

### Java Style

- Follow standard Java conventions
- Use meaningful variable and method names
- Maximum line length: 120 characters
- Prefer composition over inheritance
- Follow SOLID principles

### Code Example

```java
package io.github.luigidemasi.camelkit.command;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Command to process a route specification.
 */
@Command(
    name = "route",
    description = "Process a route specification"
)
public class RouteCommand implements Runnable {

    @Parameters(index = "0", description = "Name of the route")
    private String routeName;

    @Override
    public void run() {
        // Implementation here
    }
}
```

### Template Style (Markdown)

- Use clear headings and structure
- Include examples for all features
- Use tables for structured information
- Add comments for complex sections

## Testing

### Running Tests

```bash
# Run all tests
./mvnw test

# Run tests for specific module
./mvnw test -pl camel-kit-core

# Run with verbose output
./mvnw test -X

# Run specific test class
./mvnw test -Dtest=CatalogServiceTest
```

### Writing Tests

```java
package io.github.luigidemasi.camelkit.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CatalogServiceTest {

    @Test
    void testGetLatestCamelVersion() {
        CatalogService service = new CatalogService();
        String version = service.getLatestCamelVersion();

        assertNotNull(version);
        assertTrue(version.startsWith("4."));
        assertEquals(3, version.split("\\.").length);
    }

    @Test
    void testSearchComponents(@TempDir Path tempDir) {
        // Setup test
        CatalogService service = new CatalogService();

        // Test search
        var results = service.searchComponents("kafka");
        assertFalse(results.isEmpty());
    }
}
```

### Test Categories

1. **Unit Tests** - Test individual classes and methods
2. **Integration Tests** - Test CLI command execution
3. **Template Tests** - Verify template generation and validation

## Documentation

### Where to Document

| Type | Location |
|------|----------|
| User documentation | `docs/user-guide.md` |
| Command reference | `docs/commands.md` |
| Architecture (skills, MCP) | `docs/architecture.md` |
| API documentation | Docstrings in code |
| Change log | `CHANGELOG.md` |

### Documentation Style

- Use clear, concise language
- Include code examples
- Add screenshots for visual features
- Keep documentation up-to-date with code

## Adding New Skills

Skills are markdown instruction files that guide AI agents. To add a new skill:

### 1. Create Skill Directory

Create a new directory in `camel-kit-core/src/main/resources/skills/`:

```
skills/camel-{name}/
├── SKILL.md              # Skill manifest with YAML frontmatter
└── guides/               # Instruction files loaded on demand
    ├── main-guide.md
    └── helper-guide.md
```

### 2. Write SKILL.md

```markdown
---
name: camel-{name}
description: Brief description with trigger keywords for AI agent discovery
user_invocable: false
---

# /camel-{name}

> One-line description

## Guides

| Guide | When to Load | Purpose |
|-------|-------------|---------|
| `guides/main-guide.md` | Always | Primary instruction guide |
| `guides/helper-guide.md` | When X | Conditional reference guide |
```

### 3. Write Guide Files

Each guide in `guides/` is a self-contained markdown file loaded by the agent when the skill is active. See existing skills for examples.

### 4. Register the Skill

- **If user-invocable:** update agent templates for all 5 agents (Claude, Bob, Gemini, Qwen, OpenCode) to register the slash command
- **If internal:** update the parent skill's SKILL.md to reference your new guides
- Update `docs/commands.md` if user-invocable

See [Architecture Guide](docs/architecture.md#9-how-to-add-a-skill) for the full process.

### 5. Add Agent Traits (Optional)

If the skill benefits from agent-specific optimizations (parallel dispatch, tool-specific guidance, state management), add trait files:

```
templates/traits/{agent}/{skill-name}.append.md          # SKILL.md-level trait
templates/traits/{agent}/{skill-name}/{guide-name}.append.md  # Guide-level trait
```

Traits are appended to the corresponding skill files during `camel-kit init` with idempotent sentinels. Each trait should contain instructions specific to that agent's tools and capabilities — not generic content that belongs in the shared skill.

No code registration is required for a new trait file. `TraitApplicator` applies SKILL.md-level traits for workflow skills and discovers guide-level traits from the shipped `.append.md` files under each `templates/traits/{agent}/{skill-name}/` directory. Bob is the exception in ordering only: it installs its monolithic gate templates first, then appends Bob traits to the final generated files.

`ShippedAssetStructureTest` verifies both sides of the contract: trait files must target existing shipped skills or guides, and every shipped trait must appear in generated output for the production generator of its agent.

See [Architecture Guide](docs/architecture.md#agent-traits) for details on the trait system.

## Release Process

### Version Numbering

We follow [Semantic Versioning](https://semver.org/):
- MAJOR: Breaking changes
- MINOR: New features (backward compatible)
- PATCH: Bug fixes (backward compatible)

### Release Steps

1. Update version in `pom.xml` (parent and all modules)
2. Update `CHANGELOG.md`
3. Update version in `jbang-catalog.json`
4. Create release PR
5. After merge, create GitHub release
6. Tag with version: `v0.2.0`

### Changelog Format

```markdown
## [0.2.0] - 2024-01-20

### Added
- New feature description

### Changed
- Changed feature description

### Fixed
- Bug fix description

### Deprecated
- Deprecated feature description
```

## Getting Help

- **Questions**: Open a GitHub Discussion
- **Bugs**: Open a GitHub Issue
- **Chat**: Join Apache Camel community channels

## Recognition

Contributors are recognized in:
- GitHub contributors page
- CONTRIBUTORS.md file (for significant contributions)
- Release notes

Thank you for contributing to Camel-Kit!
