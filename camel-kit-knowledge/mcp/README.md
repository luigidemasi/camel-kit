# Camel Kit Knowledge MCP Server

MCP server for the camel-kit knowledge layer — domain-specific search tools backed by Lucene with hybrid BM25 + vector search.

## Pre-built Index

The Lucene index is committed in `src/main/resources/knowledge-index/` and ships with the uber-jar. For most development work, you don't need to rebuild it.

## Rebuilding the Knowledge Index

The `rebuild-index` Maven profile automates the full pipeline: starts a Docling container, runs the indexer, and stops the container.

### Prerequisites

- **Docker** (or Podman with Docker CLI compatibility)
- **Port 5001** available (used by the Docling container)

### Command

```bash
mvn package -pl camel-kit-knowledge/mcp -Prebuild-index -am -DskipTests
```

### What it does

1. **Downloads the ONNX embedding model** (Granite Small R2, ~50MB) if not already present
2. **Starts a Docling container** (`quay.io/docling-project/docling-serve`) for document parsing (HTML/PDF to Markdown)
3. **Runs `IndexerMain`** which:
   - Processes all domains (`CamelMigrationDomain`, `RhBuildCamelDomain`)
   - Fetches errata and CVE data from the Red Hat Hydra API
   - Generates embeddings with the Granite Small R2 ONNX model (384-dim)
   - Writes the Lucene index to `src/main/resources/knowledge-index/`
4. **Stops the Docling container**
5. **Packages the Quarkus uber-jar** with the freshly built index

### Build phases

| Phase | Action |
|-------|--------|
| `generate-resources` | Download ONNX model, start Docling container |
| `process-classes` | Run `IndexerMain` (requires compiled indexer classes) |
| `prepare-package` | Stop Docling container |
| `package` | Build Quarkus uber-jar |

### Customization

The indexer accepts system properties to override default paths:

| Property | Default | Description |
|----------|---------|-------------|
| `index.output` | `src/main/resources/knowledge-index` | Where the Lucene index is written |
| `index.resources` | `../indexer/src/main/resources` | Indexer source documents (HTML guides, errata JSON) |
| `index.cache` | `target/doc-cache` | Docling markdown cache (avoids re-parsing unchanged docs) |
| `docling.url` | `http://localhost:5001` | Docling server URL |

### Troubleshooting

**Docling fails to start:** Ensure port 5001 is free and Docker is running. The build waits up to 120 seconds for the container to become ready.

**Stale container:** If a previous build was interrupted, a `camel-kit-docling` container may still be running. The build automatically removes it before starting a new one.

**First run is slow:** The Docling container image (~5GB) must be pulled on first use. Subsequent runs reuse the cached image. Document parsing results are also cached in `target/doc-cache/`.
