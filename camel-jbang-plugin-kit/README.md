# Camel Kit JBang Plugin

A Camel JBang plugin that exposes Camel-Kit commands under `camel kit`.

## Requirements

- Apache Camel JBang 4.18.0 or higher
- JDK 17 or higher

## Release Channels

| Channel | Install source | Plugin surface |
|---------|----------------|----------------|
| Stable `0.3.1` | Maven Central | `camel kit init`; agents `bob`, `gemini`, and `claude` |
| Current `0.3.2-SNAPSHOT` | Source build and local Maven repository | `init`, `doctor`, `doc`, `graph`, `plan`, `nextId`, and `ship`; all nine current agents |

Use an explicit version. Maven Central's stable `0.3.1` does not provide the current-main command and agent surface.

### Stable 0.3.1

```bash
camel plugin add kit \
  --gav io.github.luigidemasi:camel-jbang-plugin-kit:0.3.1 \
  --description "Design Apache Camel Integrations with AI"

camel kit init my-integration --ai claude
```

### Current 0.3.2-SNAPSHOT from Source

```bash
git clone https://github.com/luigidemasi/camel-kit.git
cd camel-kit
./mvnw clean install -DskipTests

camel plugin add kit \
  --gav io.github.luigidemasi:camel-jbang-plugin-kit:0.3.2-SNAPSHOT \
  --description "Design Apache Camel Integrations with AI"

camel kit --help
```

Use `camel plugin add` rather than editing `~/.camel-jbang-plugins.json` manually; the command records the exact plugin coordinate and description.

## Current Snapshot Usage

```bash
# IBM Bob 2 is the default agent
camel kit init my-integration

# Initialize in the current directory for any supported agent
camel kit init --here --ai codex

# Inspect the generated workspace
camel kit doctor
```

The current source-built snapshot supports `bob2`, `bob`, `gemini`, `claude`, `codex`, `copilot`, `pi`, `qwen`, and `opencode`.

## Current Snapshot Commands

| Command | Purpose |
|---------|---------|
| `camel kit init` | Initialize a Camel-Kit project |
| `camel kit doctor` | Diagnose a generated workspace |
| `camel kit doc` | Manage pipeline document metadata and staleness |
| `camel kit graph` | Query the project graph |
| `camel kit plan` | Analyze implementation-plan execution waves |
| `camel kit nextId` | Create the next pipeline ID |
| `camel kit ship` | Run or control the local Ship workflow |

Stable `0.3.1` exposes only `camel kit init`.

## Development

The plugin uses `@CamelJBangPlugin` discovery and delegates its commands to `camel-kit-core`. See the [root installation guide](../README.md#release-channels) for standalone JBang options and the full current feature surface.

## Related Projects

- [Camel Kit](https://github.com/luigidemasi/camel-kit)
- [Apache Camel](https://camel.apache.org/)
- [Camel JBang](https://camel.apache.org/manual/camel-jbang.html)

## License

Apache License 2.0
