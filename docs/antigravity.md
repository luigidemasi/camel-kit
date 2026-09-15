# Google Antigravity setup and retired-target migration

Camel Kit `0.4.0` supports Google Antigravity with `--ai antigravity` and IBM Bob 2 with `--ai bob2`
(the default). The `gemini` and `bob` targets have been removed. When upgrading from `0.3.1`, use the
replacement targets and regenerate project assets as described below.

## Set up Antigravity

Install Antigravity using [Google's getting-started guide](https://antigravity.google/docs/getting-started).
Install Camel Kit `0.4.0` as described in the [installation instructions](../README.md#installation), then run:

```bash
camel-kit init my-integration --ai antigravity
```

Open the initialized project root in Antigravity. Invoke `/camel-start` in the CLI, or ask it to use the
`camel-start` skill by name. `/skills` lists native CLI skills. The `/camel-*` workflow references in these docs
name skills; some clients expose a different picker or support activation by name. Codex uses `$camel-start`.

Camel-Kit installs shared skills under `.agents/skills/`, two custom agents under `.agents/agents/`, complete role
personas under `.agents/camel-kit-personas/`, project routing in `AGENTS.md`, and three MCP servers in
`.agents/mcp_config.json`: Camel, Camel Knowledge, and Citrus. Inspect connections in Antigravity's MCP settings
or `/mcp` in the CLI. Existing unrelated MCP servers, settings, and comments are preserved on reinitialization. Tool calls
remain subject to Antigravity's permissions; Camel-Kit does not generate MCP auto-approval policies.

The primary conversation owns questions, approvals, orchestration, and report writes. It dispatches independent
implementation and review tasks to fresh worker/reviewer contexts using native `invoke_subagent`, with inherited
workspace permissions. The reviewer has no native edit or command tools; its instructions also restrict inherited
MCP tools to research and review. The agents use `inheritMcp: true`, documented in Google's
[CLI 1.1.6 release notes](https://antigravity.google/changelog). If delegation is unavailable, skills run inline and
record missing isolation.

## Migrate an existing project

Save your current work before forcing regeneration. As with other targets, `--force` replaces generated
`AGENTS.md` and Camel-Kit skills/configuration. Preserve any custom additions to those generated files first;
custom project rules can live in `GEMINI.md`, which Antigravity also reads. Then run the matching command from the project root:

```bash
# Former Gemini target
camel-kit init --here --ai antigravity --force

# Former IBM Bob v1 target
camel-kit init --here --ai bob2 --force

camel-kit doctor
```

A current source-built Camel JBang plugin supports the same options with `camel kit` in place of `camel-kit`.
Review the generated changes before committing them.

An existing `.agents/mcp_config.json` must contain a JSON object; use `{}` for an empty configuration. Comments
and trailing commas are supported. Fix syntax errors and remove duplicate object keys before reinitializing; Doctor also reports duplicate keys as failures. `AGENTS.md`, `GEMINI.md`, and
every path under `.agents/` must be real files or directories, including custom skills. Replace symbolic links
before initializing. These checks run before Camel-Kit changes project files.

Antigravity reinitialization removes the four old Camel-Kit-generated `@.gemini/...` imports from `GEMINI.md`
because Antigravity also reads that file. Custom text and imports remain intact. Other `.gemini/` files are retained;
move any custom MCP servers you still need from `.gemini/settings.json` to Antigravity's native MCP configuration
using Google's [MCP schema](https://antigravity.google/docs/mcp/). Old Gemini approval fields and TOML policies do
not configure Antigravity permissions. Move custom skills from `.gemini/skills/<name>/` to `.agents/skills/<name>/`,
checking for name collisions before moving them, then restart Antigravity. Custom Gemini agents or commands need
conversion to Antigravity's native formats; reinit regenerates only the Camel-Kit assets.

Bob 2 reinitialization replaces the generated gate skills and modes with the shared pipeline and native subagents.
It removes only the known obsolete Bob v1 mode-rule files, preserving neighboring custom rules. In Bob Shell use
`$camel-start` or `/skills`; in Bob IDE use `/camel-start`.

This is Camel-Kit's target policy. Google's [retirement notice](https://developers.google.com/gemini-code-assist/docs/deprecations/code-assist-individuals)
specifically covers consumer Google sign-in for Gemini CLI and Code Assist; Standard and Enterprise offerings are
outside that retirement notice.
