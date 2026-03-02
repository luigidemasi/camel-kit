---
title: Contributing
weight: 5
---

Thank you for your interest in contributing to Camel-Kit!

> **Note:** Camel-Kit is heavily inspired by [GitHub Spec-Kit](https://github.com/github/spec-kit). When contributing, we encourage you to explore the spec-kit project to understand the design philosophy that guides this project.

## Prerequisites

- Java 17 or higher
- [JBang](https://www.jbang.dev/) (for running the CLI)
- Maven 3.9+ (included via Maven Wrapper)
- Git

## Development Setup

### Fork and Clone

```bash
git clone https://github.com/YOUR_USERNAME/camel-kit.git
cd camel-kit
git remote add upstream https://github.com/luigidemasi/camel-kit.git
```

### Building

```bash
# Build all modules
./mvnw clean install

# Run CLI using JBang
jbang camel-kit@. --help

# Run tests
./mvnw test

# Install locally (after build)
mvn install
jbang app install --force camel-kit@./
```

## Project Structure

```
camel-kit/
├── camel-kit-main/              # Main JBang CLI module
│   └── src/main/jbang/main/
│       └── CamelKit.java        # JBang entry point
├── camel-kit-core/              # Core functionality module
│   └── src/main/java/
│       ├── command/             # CLI command implementations
│       ├── catalog/             # Catalog fetching logic
│       ├── config/              # Configuration handling
│       ├── output/              # Output formatting
│       └── util/                # Utility classes
│   └── src/main/resources/
│       ├── templates/           # Template files
│       └── skills/              # Component skills (396)
├── camel-kit-plugins/           # Plugin modules
├── docs/                        # Documentation
├── pom.xml                      # Parent Maven POM
├── jbang-catalog.json           # JBang catalog definition
└── README.md
```

## How to Contribute

### Types of Contributions

1. **Bug Reports** - Open an issue describing the bug
2. **Feature Requests** - Open an issue describing the feature
3. **Documentation** - Improve docs, fix typos, add examples
4. **Code** - Fix bugs, implement features, improve tests
5. **New Commands** - Add new slash commands for AI assistants
6. **Templates** - Enhance constitution, design patterns, or command templates

### Branch Naming

- `feature/description` - New features
- `fix/description` - Bug fixes
- `docs/description` - Documentation changes
- `command/command-name` - New slash commands

## Pull Request Process

1. **Sync with upstream:** `git fetch upstream && git rebase upstream/main`
2. **Run tests:** `./mvnw test`
3. **Build the project:** `./mvnw clean install`
4. **Test the CLI:** `jbang camel-kit@. init`

### PR Checklist

- [ ] Tests pass locally
- [ ] Linting passes
- [ ] Documentation updated (if applicable)
- [ ] CHANGELOG.md updated (for significant changes)
- [ ] Commit messages are clear and descriptive

## Adding New Commands

### 1. Create Command Template

Add a new markdown file in `camel-kit-core/src/main/resources/skills/`:

```markdown
---
name: camel-yourcommand
description: Brief description
user-invocable: true
---

# /camel-yourcommand

## Purpose
...

## Workflow
1. Step one
2. Step two
```

### 2. Register Command

Add the command class in `camel-kit-core/src/main/java/.../command/`:

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

- Add to `docs/commands.md`
- Add to `README.md` if it's a major command

## Documentation

| Type | Location |
|------|----------|
| User documentation | `docs/user-guide.md` |
| Command reference | `docs/commands.md` |
| Architecture (skills, MCP) | `docs/architecture.md` |
| Change log | `CHANGELOG.md` |

## Release Process

We follow [Semantic Versioning](https://semver.org/):
- MAJOR: Breaking changes
- MINOR: New features (backward compatible)
- PATCH: Bug fixes (backward compatible)

### Release Steps

1. Update version in `pom.xml` (parent and all modules)
2. Update `CHANGELOG.md`
3. Update version in `jbang-catalog.json`
4. Create release PR
5. After merge, create GitHub release with tag `v0.x.0`
