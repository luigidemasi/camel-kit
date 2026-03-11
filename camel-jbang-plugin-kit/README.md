# Camel Kit JBang Plugin

A Camel JBang plugin that provides AI-powered Camel integration design capabilities directly through the `camel` CLI.

## Installation

### From Maven Central (after release)

```bash
camel plugin add kit \
  -g io.github.luigidemasi \
  -a camel-kit-jbang-plugin \
  -v 0.2.1 \
  -d "Design Apache Camel Integrations with AI"
```

### Manual Configuration

Add to `~/.camel-jbang-plugins.json`:

```json
{
  "plugins": {
    "kit": {
      "name": "kit",
      "command": "kit",
      "description": "Design Apache Camel Integrations with AI",
      "firstVersion": "4.8.0",
      "dependency": "io.github.luigidemasi:camel-kit-jbang-plugin:0.2.1"
    }
  }
}
```

## Usage

Once installed, you can use camel-kit commands through the `camel` CLI:

### Initialize a New Project

```bash
# Initialize in a new directory
camel kit init my-integration

# Initialize in current directory
camel kit init --here

# Specify AI agent (bob, gemini, claude, copilot, or cursor)
camel kit init my-integration --ai claude

# Specify Camel version
camel kit init my-integration -v 4.8.0
```

## Available Commands

- `camel kit init` - Initialize a new Camel-Kit project with AI agent configuration
- `camel kit validate` - Validate Camel routes (coming soon)

## Requirements

- Apache Camel 4.8.0 or higher
- JDK 17 or higher

## How It Works

This plugin integrates camel-kit functionality into the Camel JBang CLI:

1. **Plugin Discovery**: Uses `@CamelJBangPlugin` annotation and SPI service loading
2. **Command Registration**: Adds `kit` subcommand to `camel` CLI at runtime
3. **Core Integration**: Delegates to `camel-kit-core` for actual functionality
4. **Adapter Pattern**: Bridges between Camel JBang's `CamelCommand` and camel-kit's command structure

## Development

Build the plugin:

```bash
./mvnw clean install -pl camel-kit-jbang-plugin -am
```

Test locally by adding the JAR to Camel JBang classpath or installing from local repository.

## Related Projects

- [Camel Kit](https://github.com/luigidemasi/camel-kit) - Main camel-kit project
- [Apache Camel](https://camel.apache.org/) - Integration framework
- [Camel JBang](https://camel.apache.org/manual/camel-jbang.html) - Camel command-line tool

## License

Apache License 2.0
