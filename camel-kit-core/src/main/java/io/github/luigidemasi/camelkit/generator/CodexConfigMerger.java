package io.github.luigidemasi.camelkit.generator;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

final class CodexConfigMerger {

    static final String BEGIN_MARKER = "# BEGIN CAMEL-KIT MANAGED MCP SERVERS";
    static final String END_MARKER = "# END CAMEL-KIT MANAGED MCP SERVERS";

    private static final List<String> MANAGED_SERVERS = List.of("camel", "camel-knowledge", "citrus");

    void merge(Path configFile, String renderedConfig) throws IOException {
        validateToml(renderedConfig, "generated Codex MCP configuration");

        String managedBlock = BEGIN_MARKER + System.lineSeparator()
                              + renderedConfig.strip() + System.lineSeparator()
                              + END_MARKER;
        String existing = Files.isRegularFile(configFile) ? Files.readString(configFile) : "";
        String merged = merge(existing, managedBlock);
        validateToml(merged, configFile.toString());
        atomicWrite(configFile, merged + (merged.endsWith(System.lineSeparator()) ? "" : System.lineSeparator()));
    }

    private String merge(String existing, String managedBlock) throws IOException {
        if (existing.isBlank()) {
            return managedBlock;
        }

        validateToml(existing, "existing .codex/config.toml");
        int begin = existing.indexOf(BEGIN_MARKER);
        int end = existing.indexOf(END_MARKER);
        if (begin >= 0 || end >= 0) {
            if (begin < 0 || end < begin || existing.indexOf(BEGIN_MARKER, begin + 1) >= 0
                    || existing.indexOf(END_MARKER, end + 1) >= 0) {
                throw new IOException("existing .codex/config.toml has invalid Camel-Kit managed block markers");
            }
            return existing.substring(0, begin) + managedBlock + existing.substring(end + END_MARKER.length());
        }

        Object mcpServers = Toml.parse(existing).get("mcp_servers");
        if (mcpServers instanceof TomlTable servers) {
            List<String> conflicts = MANAGED_SERVERS.stream()
                    .filter(server -> servers.get(server) instanceof TomlTable)
                    .toList();
            if (!conflicts.isEmpty()) {
                throw new IOException(
                        "existing .codex/config.toml already defines Camel-Kit MCP server tables: "
                                      + String.join(", ", conflicts));
            }
        }

        return existing + (existing.endsWith(System.lineSeparator())
                ? System.lineSeparator()
                : System.lineSeparator() + System.lineSeparator())
               + managedBlock;
    }

    private void validateToml(String content, String source) throws IOException {
        TomlParseResult parsed = Toml.parse(content);
        if (parsed.hasErrors()) {
            throw new IOException(source + " is not valid TOML: " + parsed.errors().get(0));
        }
    }

    private void atomicWrite(Path target, String content) throws IOException {
        Path temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temp, content);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
