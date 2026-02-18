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
- [Adding New AI Agents](#adding-new-ai-agents)
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
# Build all modules using Maven Wrapper
./mvnw clean install

# Run CLI using JBang (from jbang-catalog.json)
jbang camel-kit@. --help

# Or run directly from the main JBang script
jbang camel-kit-main/src/main/jbang/main/CamelKit.java --help

# Run tests
./mvnw test

# Package for distribution
./mvnw package
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
│   └── src/main/java/com/github/luigidemasi/camelkit/
│       ├── CamelKitMain.java    # Picocli CLI main class
│       ├── command/             # CLI command implementations
│       ├── catalog/             # Catalog fetching logic
│       ├── config/              # Configuration handling
│       ├── output/              # Output formatting
│       └── util/                # Utility classes
│   └── src/main/resources/
│       ├── templates/           # Template files
│       │   ├── commands/        # Slash command definitions
│       │   ├── constitution.md
│       │   ├── design-patterns.md
│       │   └── ...
│       └── maven/               # Maven wrapper generation
├── camel-kit-plugins/           # Plugin modules
│   └── camel-kit-wanaku-plugin/ # Wanaku validation plugin
├── templates/                   # Template sources (copied to resources)
│   ├── commands/
│   ├── constitution.md
│   └── design-patterns.md
├── docs/                        # Documentation
│   ├── user-guide.md
│   ├── commands.md
│   └── constitution.md
├── src/python/                  # Python utilities (validation scripts)
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
   ./mvnw clean install
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
| API documentation | Docstrings in code |
| Change log | `CHANGELOG.md` |

### Documentation Style

- Use clear, concise language
- Include code examples
- Add screenshots for visual features
- Keep documentation up-to-date with code

## Adding New Commands

To add support for a new slash command:

### 1. Create Command Template

Add a new markdown file in `templates/commands/`:

```markdown
# /camel.yourcommand

> Brief description of what this command does

## Purpose

Detailed explanation of the command's purpose and when to use it.

## Workflow

1. Step one
2. Step two
3. Step three

## Example

Provide examples of using the command.
```

### 2. Register Command

Add the command class in `camel-kit-core/src/main/java/com/github/luigidemasi/camelkit/command/`:

```java
@Command(
    name = "yourcommand",
    description = "Description of your command"
)
public class YourCommand implements Runnable {
    @Override
    public void run() {
        // Implementation
    }
}
```

Register it in `CamelKitMain.java`.

### 3. Update Documentation

- Add to docs/commands.md
- Add to README.md if it's a major command
- Update any related documentation

### 4. Add Tests

```java
@Test
void testYourCommand() {
    // Test command execution
}
```

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
