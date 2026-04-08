# Graph Availability Check

> **Loaded by:** Per-skill graph guides (camel-validate, camel-implement, camel-test).
> **Purpose:** Common graph detection and fallback contract.

## Check

**Before running any graph command:**

1. Read `.camel-kit/config.yaml` to get the `command-prefix` field. If not set, default to `camel-kit`.
2. Run `{COMMAND_PREFIX} graph stats` as a bash command.
3. Check the exit code:
   - Exit code 0 → graph available. Proceed with graph-enhanced steps.
   - Exit code != 0 → graph unavailable. Skip all graph-enhanced steps silently.

**Example bash command:**
```bash
camel-kit graph stats
```

Replace `camel-kit` with the actual value from `config.yaml`.

## Fallback Rule

Every graph CLI command must be wrapped in graceful fallback:
- If the command exits with code != 0 → skip that step silently
- If the command times out or fails → skip that step silently
- NEVER hard-stop a workflow because graph tools are unavailable

**Graph enhances, never gates.**

## Graph CLI Commands

All graph queries are now CLI subcommands under `{COMMAND_PREFIX} graph`:
- `graph stats` — project statistics
- `graph find --type <TYPE>` — find nodes by type
- `graph neighbors <nodeId> [--direction in|out|both]` — get neighbors
- `graph impact <nodeId> --direction <upstream|downstream|both>` — impact analysis
- `graph route-flow <routeId>` — route message flow
- `graph route-topology` — all route connections
- `graph dead-code` — unused code detection
- `graph project-norms` — composite for validation (norms + topology + stats)
- `graph project-context` — composite for implementation (properties + beans + deps + route dir)
- `graph route-context <routeId>` — composite for testing (upstream + downstream + endpoints + error flow)
