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

- Python 3.11 or higher
- [uv](https://github.com/astral-sh/uv) (recommended) or pip
- Git
- [Camel JBang](https://camel.apache.org/manual/camel-jbang.html) (for testing routes)

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

### Using uv (Recommended)

```bash
# Install dependencies and create virtual environment
uv sync

# Run CLI in development mode
uv run camel-kit --help

# Run tests
uv run pytest

# Run linting
uv run ruff check src/
uv run ruff format src/
```

### Using pip

```bash
# Create virtual environment
python -m venv .venv
source .venv/bin/activate  # On Windows: .venv\Scripts\activate

# Install in development mode
pip install -e ".[dev]"

# Run CLI
camel-kit --help
```

## Project Structure

```
camel-kit/
├── src/camel_kit_cli/           # Main Python package
│   ├── __init__.py              # CLI entry point and agent config
│   ├── catalog.py               # Catalog fetching logic
│   └── templates/               # Template files
│       ├── commands/            # Slash command definitions
│       │   ├── init.md
│       │   ├── context.md
│       │   ├── route.md
│       │   ├── validate.md
│       │   ├── test.md
│       │   └── generate.md
│       ├── constitution.md      # Best practices template
│       ├── context.md           # Integration context template
│       ├── route.md             # Route specification template
│       ├── yaml-generation-guide.md
│       └── validation-guide.md
├── docs/                        # Documentation
│   ├── user-guide.md
│   ├── commands.md
│   └── constitution.md
├── tests/                       # Test files
├── pyproject.toml               # Project configuration
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
5. **New AI Agents** - Add support for additional AI assistants

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
- `agent/agent-name` - New AI agent support

## Pull Request Process

### Before Submitting

1. **Sync with upstream:**
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Run tests:**
   ```bash
   uv run pytest
   ```

3. **Run linting:**
   ```bash
   uv run ruff check src/ --fix
   uv run ruff format src/
   ```

4. **Test the CLI:**
   ```bash
   uv run camel-kit init test-project --ai bob --no-fetch-catalog
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

### Python Style

- Follow PEP 8 style guide
- Use type hints for function signatures
- Maximum line length: 100 characters
- Use `ruff` for linting and formatting

### Code Example

```python
from pathlib import Path
from typing import Optional

def process_route(
    route_name: str,
    project_dir: Path,
    force: bool = False,
) -> dict[str, Any]:
    """
    Process a route specification.

    Args:
        route_name: Name of the route to process
        project_dir: Project directory path
        force: Force overwrite existing files

    Returns:
        Dictionary with processing results
    """
    # Implementation here
    pass
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
uv run pytest

# Run with coverage
uv run pytest --cov=camel_kit_cli

# Run specific test
uv run pytest tests/test_catalog.py -v
```

### Writing Tests

```python
import pytest
from pathlib import Path
from camel_kit_cli import catalog

def test_get_latest_camel_version():
    """Test fetching latest Camel version."""
    version = catalog.get_latest_camel_version()
    assert version.startswith("4.")
    assert len(version.split(".")) == 3

def test_search_components(tmp_path):
    """Test component search functionality."""
    # Setup test catalog
    catalog_data = {
        "components": {
            "kafka": {"component": {"title": "Kafka"}},
            "http": {"component": {"title": "HTTP"}},
        }
    }

    results = catalog.search_components("kafka", catalog_data)
    assert len(results) == 1
    assert results[0]["name"] == "kafka"
```

### Test Categories

1. **Unit Tests** - Test individual functions
2. **Integration Tests** - Test CLI commands
3. **Template Tests** - Verify template generation

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

## Adding New AI Agents

To add support for a new AI coding assistant:

### 1. Update Agent Configuration

Edit `src/camel_kit_cli/__init__.py`:

```python
AGENT_CONFIG = {
    # Existing agents...

    "new-agent": {
        "name": "New Agent Name",
        "folder": ".new-agent/commands",
        "file_format": "md",
        "install_url": "https://example.com/install",
        "requires_cli": True,  # or False for IDE-based
        "description": "Description of the agent",
    },
}
```

### 2. Verify Command Format

Check that the command template format works with the new agent:
- Some agents use different slash command syntax
- Some require different file extensions
- Test with actual agent before merging

### 3. Update Documentation

- Add to README.md agents table
- Add to docs/user-guide.md
- Update any agent-specific instructions

### 4. Add Tests

```python
def test_new_agent_init(tmp_path):
    """Test initialization with new agent."""
    # Test that init creates correct folder structure
    pass
```

## Release Process

### Version Numbering

We follow [Semantic Versioning](https://semver.org/):
- MAJOR: Breaking changes
- MINOR: New features (backward compatible)
- PATCH: Bug fixes (backward compatible)

### Release Steps

1. Update version in `pyproject.toml`
2. Update `CHANGELOG.md`
3. Create release PR
4. After merge, create GitHub release
5. Tag with version: `v0.1.0`

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
