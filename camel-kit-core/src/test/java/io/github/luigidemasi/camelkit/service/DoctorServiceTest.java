package io.github.luigidemasi.camelkit.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentGeneratorStrategy;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.generator.AgentGeneratorFactory;
import io.github.luigidemasi.camelkit.generator.InitContext;
import io.github.luigidemasi.camelkit.output.Printer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class DoctorServiceTest {

    private static final DoctorExpectations EXPECTATIONS = DoctorExpectations.loadDefault();
    private static final String COPILOT = AgentGeneratorStrategy.COPILOT.descriptorValue();
    private static final String CODEX = AgentGeneratorStrategy.CODEX.descriptorValue();
    private static final String PI = AgentGeneratorStrategy.PI.descriptorValue();
    private static final String QWEN = AgentGeneratorStrategy.QWEN.descriptorValue();
    private static final String OPENCODE = AgentGeneratorStrategy.OPENCODE.descriptorValue();

    @TempDir
    Path tempDir;

    @Test
    void healthyWorkspaceReturnsStructuredFindings() throws Exception {
        createHealthyWorkspace(tempDir, "bob");

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures());
        assertEquals(tempDir.toAbsolutePath().normalize(), result.projectDir());
        assertTrue(result.count(DoctorFinding.Status.PASS) > 0);
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "config"));
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "mcp"));
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "workspace"));
    }

    @Test
    void healthyBob2WorkspaceUsesRegisteredMcpPath() throws Exception {
        createHealthyWorkspace(tempDir, "bob2");

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "MCP config exists and tool allowlists match Camel-Kit expectations",
                "No action required."));
    }

    @Test
    void healthyCopilotWorkspaceUsesGithubMcpToolsSchema() throws Exception {
        createHealthyWorkspace(tempDir, "copilot");

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "MCP config exists and tool allowlists match Camel-Kit expectations",
                "No action required."));
    }

    @Test
    void healthyPiWorkspaceUsesDirectToolsSchemaAndGuardResources() throws Exception {
        createHealthyWorkspace(tempDir, "pi");

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "MCP config exists and tool allowlists match Camel-Kit expectations",
                "No action required."));
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "workspace",
                "Pi safety guard extension and policy are present",
                "No action required."));
    }

    @Test
    void healthyCodexWorkspaceValidatesTomlAgentsAndDoesNotRequireCommands() throws Exception {
        createHealthyWorkspace(tempDir, CODEX);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertFalse(Files.exists(tempDir.resolve(".codex/commands")));
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "Codex MCP config is valid TOML and all tool allowlists match Camel-Kit expectations",
                "No action required."));
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "workspace",
                "Codex custom agents are valid TOML and declare the required fields",
                "No action required."));
    }

    @Test
    void legacyQwenMcpSchemaWarnsWithoutFailing() throws Exception {
        createHealthyWorkspace(tempDir, QWEN);
        writeMcpConfig(tempDir, QWEN, String.format(Locale.ROOT, """
                {
                  "mcpServers": {
                    "camel": {"command": "jbang", "autoApprove": [%s], "alwaysAllow": [%s]},
                    "camel-knowledge": {"command": "jbang", "autoApprove": [%s], "alwaysAllow": [%s]},
                    "citrus": {"command": "jbang", "autoApprove": [%s], "alwaysAllow": [%s]}
                  }
                }
                """, jsonArray(EXPECTATIONS.camelMcpTools()), jsonArray(EXPECTATIONS.camelMcpTools()),
                jsonArray(EXPECTATIONS.knowledgeMcpTools()), jsonArray(EXPECTATIONS.knowledgeMcpTools()),
                jsonArray(EXPECTATIONS.citrusMcpTools()), jsonArray(EXPECTATIONS.citrusMcpTools())));

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.WARN, "mcp",
                "Qwen MCP server 'camel' uses the legacy approval-field schema", "includeTools"));
        assertFalse(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "tool allowlists match", null));
    }

    @Test
    void qwenMissingCurrentToolFilterStillFails() throws Exception {
        createHealthyWorkspace(tempDir, QWEN);
        writeMcpConfig(tempDir, QWEN, """
                {
                  "mcpServers": {
                    "camel": {"command": "jbang"},
                    "camel-knowledge": {"command": "jbang"}
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "MCP server 'camel' is missing includeTools array", "Regenerate"));
    }

    @Test
    void qwenMalformedCurrentToolFilterIsNotDowngradedToLegacy() throws Exception {
        createHealthyWorkspace(tempDir, QWEN);
        writeMcpConfig(tempDir, QWEN, """
                {
                  "mcpServers": {
                    "camel": {"command": "jbang", "includeTools": null, "autoApprove": []},
                    "camel-knowledge": {"command": "jbang", "includeTools": [], "autoApprove": []}
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "MCP server 'camel' has invalid includeTools array", "Regenerate"));
        assertFalse(hasFinding(result, DoctorFinding.Status.WARN, "mcp",
                "Qwen MCP server 'camel' uses the legacy approval-field schema", null));
    }

    @Test
    void qwenExplicitNullTrustIsEquivalentToAbsent() throws Exception {
        createHealthyWorkspace(tempDir, QWEN);
        Path config = tempDir.resolve(".qwen/settings.json");
        Files.writeString(config, Files.readString(config).replaceFirst(
                "\\\"includeTools\\\":", "\"trust\": null, \"includeTools\":"));

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertFalse(hasFinding(result, DoctorFinding.Status.FAIL, "mcp", "trust must be absent", null));
    }

    @Test
    void legacyOpenCodeMcpSchemaWarnsWithoutFailing() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        writeMcpConfig(tempDir, OPENCODE, String.format(Locale.ROOT, """
                {
                  "mcp": {
                    "camel": {"type": "local", "autoApprove": [%s], "alwaysAllow": [%s]},
                    "camel-knowledge": {"type": "local", "autoApprove": [%s], "alwaysAllow": [%s]},
                    "citrus": {"type": "local", "autoApprove": [%s], "alwaysAllow": [%s]}
                  }
                }
                """, jsonArray(EXPECTATIONS.camelMcpTools()), jsonArray(EXPECTATIONS.camelMcpTools()),
                jsonArray(EXPECTATIONS.knowledgeMcpTools()), jsonArray(EXPECTATIONS.knowledgeMcpTools()),
                jsonArray(EXPECTATIONS.citrusMcpTools()), jsonArray(EXPECTATIONS.citrusMcpTools())));

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.WARN, "mcp",
                "contains unsupported field autoApprove", "regenerate"));
        assertTrue(hasFinding(result, DoctorFinding.Status.WARN, "mcp",
                "Legacy OpenCode config has no supported permission", "approval prompts"));
        assertFalse(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "tool calls retain OpenCode permission prompts", null));
    }

    @Test
    void mixedOpenCodeSchemasOnlyDowngradePermissionsForTheirLegacyServer() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        writeMcpConfig(tempDir, OPENCODE, """
                {
                  "mcp": {
                    "camel": {"type": "local", "autoApprove": []},
                    "camel-knowledge": {"type": "local"},
                    "citrus": {"type": "local"}
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.WARN, "mcp",
                "Legacy OpenCode config has no supported permission for 'camel_*'", "approval prompts"));
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "OpenCode permission 'camel-knowledge_*' must be 'ask'", "permission prompts"));
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "OpenCode permission 'citrus_*' must be 'ask'", "permission prompts"));
    }

    @Test
    void currentOpenCodePermissionNotAskStillFails() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        writeMcpConfig(tempDir, OPENCODE, """
                {
                  "permission": {
                    "camel_*": "allow",
                    "camel-knowledge_*": "ask",
                    "citrus_*": "ask"
                  },
                  "mcp": {
                    "camel": {"type": "local"},
                    "camel-knowledge": {"type": "local"},
                    "citrus": {"type": "local"},
                    "custom": {"type": "local", "autoApprove": ["custom_tool"]}
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "OpenCode permission namespace 'camel_*' must end with a namespace-wide 'ask' rule",
                "permission prompts"));
        assertFalse(hasFinding(result, DoctorFinding.Status.WARN, "mcp",
                "Legacy OpenCode config has no supported permission for 'camel_*'", null));
        assertFalse(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "tool calls retain OpenCode permission prompts", null));
    }

    @Test
    void laterOpenCodeWildcardCannotOverrideManagedPermissionPrompts() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        writeMcpConfig(tempDir, OPENCODE, """
                {
                  "permission": {
                    "camel_*": "ask",
                    "camel-knowledge_*": "ask",
                    "citrus_*": "ask",
                    "*": "deny"
                  },
                  "mcp": {
                    "camel": {"type": "local"},
                    "camel-knowledge": {"type": "local"},
                    "citrus": {"type": "local"}
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "OpenCode permission namespace 'camel_*' must end with a namespace-wide 'ask' rule",
                "permission prompts"));
        assertFalse(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "tool calls retain OpenCode permission prompts", null));
    }

    @Test
    void higherPrecedenceOpenCodeWildcardCannotOverrideLowerManagedPermissions() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        Files.writeString(tempDir.resolve("opencode.jsonc"), """
                {
                  // Loaded after opencode.json.
                  "permission": {"*": "deny"},
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "OpenCode permission namespace 'camel_*' must end with a namespace-wide 'ask' rule",
                "permission prompts"));
    }

    @Test
    void doctorNormalizesScalarPermissionBeforeMergingOpenCodeLayers() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        writeMcpConfig(tempDir, OPENCODE, """
                {
                  "permission": {
                    "*": "allow",
                    "camel_*": "ask",
                    "camel-knowledge_*": "ask",
                    "citrus_*": "ask"
                  },
                  "mcp": {
                    "camel": {"type": "local"},
                    "camel-knowledge": {"type": "local"},
                    "citrus": {"type": "local"}
                  }
                }
                """);
        Files.writeString(tempDir.resolve("opencode.jsonc"), "{\"permission\": \"deny\"}");

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "tool calls retain OpenCode permission prompts", "No action required."));
    }

    @Test
    void objectWildcardAskIsAValidOpenCodePromptPolicy() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        writeMcpConfig(tempDir, OPENCODE, """
                {
                  "permission": {"*": {"*": "ask"}},
                  "mcp": {
                    "camel": {"type": "local"},
                    "camel-knowledge": {"type": "local"},
                    "citrus": {"type": "local"}
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "tool calls retain OpenCode permission prompts", "No action required."));
    }

    @Test
    void doctorEvaluatesOverridesAgainstOpenCodesPrefixedMcpToolNames() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        writeMcpConfig(tempDir, OPENCODE, """
                {
                  "permission": {
                    "camel_*": "ask",
                    "camel-knowledge_*": "ask",
                    "citrus_*": "ask",
                    "camel-knowledge_camel_docs_search": "allow"
                  },
                  "mcp": {
                    "camel": {"type": "local"},
                    "camel-knowledge": {"type": "local"}
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "OpenCode permission namespace 'camel-knowledge_*' must end with a namespace-wide 'ask' rule",
                "permission prompts"));
    }

    @Test
    void futureOpenCodeToolOverrideCannotBypassPermissionPrompts() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        writeMcpConfig(tempDir, OPENCODE, """
                {
                  "permission": {
                    "camel_*": "ask",
                    "camel-knowledge_*": "ask",
                    "citrus_*": "ask",
                    "camel_future_tool": "allow"
                  },
                  "mcp": {
                    "camel": {"type": "local"},
                    "camel-knowledge": {"type": "local"},
                    "citrus": {"type": "local"}
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "OpenCode permission namespace 'camel_*' must end with a namespace-wide 'ask' rule",
                "permission prompts"));
    }

    @Test
    void futureOpenCodeToolWildcardCannotBypassPermissionPrompts() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        writeMcpConfig(tempDir, OPENCODE, """
                {
                  "permission": {
                    "*": "ask",
                    "camel_future_*": "deny"
                  },
                  "mcp": {
                    "camel": {"type": "local"},
                    "camel-knowledge": {"type": "local"},
                    "citrus": {"type": "local"}
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "OpenCode permission namespace 'camel_*' must end with a namespace-wide 'ask' rule",
                "permission prompts"));
    }

    @Test
    void broadNestedOpenCodeAskDoesNotHideAFutureToolOverride() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        writeMcpConfig(tempDir, OPENCODE, """
                {
                  "permission": {
                    "*": {"*": "ask"},
                    "camel_future_external": "allow"
                  },
                  "mcp": {
                    "camel": {"type": "local"},
                    "camel-knowledge": {"type": "local"},
                    "citrus": {"type": "local"}
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "OpenCode permission namespace 'camel_*' must end with a namespace-wide 'ask' rule",
                "permission prompts"));
    }

    @Test
    void currentToolOnlyOpenCodeRulesDoNotCoverFutureTools() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        writeMcpConfig(tempDir, OPENCODE, """
                {
                  "permission": {
                    "camel_camel_*": "ask",
                    "camel-knowledge_*": "ask",
                    "citrus_*": "ask"
                  },
                  "mcp": {
                    "camel": {"type": "local"},
                    "camel-knowledge": {"type": "local"},
                    "citrus": {"type": "local"}
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "OpenCode permission namespace 'camel_*' must end with a namespace-wide 'ask' rule",
                "permission prompts"));
    }

    @Test
    void unrelatedOpenCodePermissionAfterManagedRulesStillPasses() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        writeMcpConfig(tempDir, OPENCODE, """
                {
                  "permission": {
                    "camel_*": "ask",
                    "camel-knowledge_*": "ask",
                    "citrus_*": "ask",
                    "bash": "allow"
                  },
                  "mcp": {
                    "camel": {"type": "local"},
                    "camel-knowledge": {"type": "local"},
                    "citrus": {"type": "local"}
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "tool calls retain OpenCode permission prompts", "No action required."));
    }

    @Test
    void laterOverlappingOpenCodeAskStillPasses() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        writeMcpConfig(tempDir, OPENCODE, """
                {
                  "permission": {
                    "camel_*": "ask",
                    "camel-knowledge_*": "ask",
                    "citrus_*": "ask",
                    "camel_future_*": "ask"
                  },
                  "mcp": {
                    "camel": {"type": "local"},
                    "camel-knowledge": {"type": "local"},
                    "citrus": {"type": "local"}
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "tool calls retain OpenCode permission prompts", "No action required."));
    }

    @Test
    void impossibleOpenCodeToolNameOverrideStillPasses() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        writeMcpConfig(tempDir, OPENCODE, """
                {
                  "permission": {
                    "camel_*": "ask",
                    "camel-knowledge_*": "ask",
                    "citrus_*": "ask",
                    "camel_future/path": "allow"
                  },
                  "mcp": {
                    "camel": {"type": "local"},
                    "camel-knowledge": {"type": "local"},
                    "citrus": {"type": "local"}
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "tool calls retain OpenCode permission prompts", "No action required."));
    }

    @Test
    void doctorMatchesOpenCodesOptionalTrailingArgumentWildcard() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        writeMcpConfig(tempDir, OPENCODE, """
                {
                  "permission": {
                    "camel_*": "ask",
                    "camel-knowledge_*": "ask",
                    "citrus_*": "ask",
                    "camel_* *": "allow"
                  },
                  "mcp": {
                    "camel": {"type": "local"},
                    "camel-knowledge": {"type": "local"},
                    "citrus": {"type": "local"}
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "OpenCode permission namespace 'camel_*' must end with a namespace-wide 'ask' rule",
                "permission prompts"));
    }

    @Test
    void namespaceWideOpenCodeOptionalTrailingArgumentAskPasses() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        writeMcpConfig(tempDir, OPENCODE, """
                {
                  "permission": {
                    "camel_* *": "ask",
                    "camel-knowledge_*": "ask",
                    "citrus_*": "ask"
                  },
                  "mcp": {
                    "camel": {"type": "local"},
                    "camel-knowledge": {"type": "local"},
                    "citrus": {"type": "local"}
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "tool calls retain OpenCode permission prompts", "No action required."));
    }

    @Test
    void repeatedOptionalOpenCodeSuffixDoesNotFakeNamespaceCoverage() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        writeMcpConfig(tempDir, OPENCODE, """
                {
                  "permission": {
                    "camel_camel_*": "ask",
                    "camel_* * *": "ask",
                    "camel-knowledge_*": "ask",
                    "citrus_*": "ask"
                  },
                  "mcp": {
                    "camel": {"type": "local"},
                    "camel-knowledge": {"type": "local"},
                    "citrus": {"type": "local"}
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "OpenCode permission namespace 'camel_*' must end with a namespace-wide 'ask' rule",
                "final namespace-wide ask rule"));
    }

    @Test
    void partialOpenCodeAskDoesNotRestoreNamespacePolicy() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        writeMcpConfig(tempDir, OPENCODE, """
                {
                  "permission": {
                    "*": "ask",
                    "camel_future_tool": "allow",
                    "camel_future_*": "ask"
                  },
                  "mcp": {
                    "camel": {"type": "local"},
                    "camel-knowledge": {"type": "local"},
                    "citrus": {"type": "local"}
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "OpenCode permission namespace 'camel_*' must end with a namespace-wide 'ask' rule",
                "final namespace-wide ask rule"));
    }

    @Test
    void healthyOpenCodeJsoncWorkspacePassesDoctor() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        Path json = tempDir.resolve("opencode.json");
        String generated = Files.readString(json);
        Files.delete(json);
        Files.writeString(tempDir.resolve("opencode.jsonc"),
                "// Project-level OpenCode configuration.\n"
                                                             + generated.substring(0, generated.lastIndexOf('}'))
                                                             + ",\n}\n");

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "tool calls retain OpenCode permission prompts", "No action required."));
    }

    @Test
    void doctorChecksTheEffectiveOpenCodeConfigurationAcrossAllProjectFiles() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        Files.writeString(tempDir.resolve("opencode.json"), """
                {
                  "permission": {"camel_*": "allow", "camel-knowledge_*": "ask"},
                  "mcp": {
                    "camel": {"type": "local"},
                    "camel-knowledge": {"type": "local"},
                    "citrus": {"type": "local"}
                  }
                }
                """);
        Files.writeString(tempDir.resolve("opencode.jsonc"), """
                {
                  // JSONC has precedence over the root JSON file.
                  "permission": {"camel_*": "ask", "camel-knowledge_*": "deny"},
                }
                """);
        Files.writeString(tempDir.resolve(".opencode/opencode.json"), """
                {"permission": {"camel-knowledge_*": "ask", "citrus_*": "deny"}}
                """);
        Files.writeString(tempDir.resolve(".opencode/opencode.jsonc"), """
                {
                  // The .opencode JSONC file has the final project-level value.
                  "permission": {"citrus_*": "ask"},
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "tool calls retain OpenCode permission prompts", "No action required."));
        assertTrue(result.findings().stream()
                .anyMatch(finding -> finding.status() == DoctorFinding.Status.PASS
                        && "mcp".equals(finding.category())
                        && ".opencode/opencode.jsonc".equals(finding.path())));
    }

    @Test
    void doctorRejectsInvalidLowerPrecedenceOpenCodeConfig() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        String validConfig = Files.readString(tempDir.resolve("opencode.json"));
        Files.writeString(tempDir.resolve("opencode.json"), "{} {}");
        Files.writeString(tempDir.resolve(".opencode/opencode.jsonc"), validConfig);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(result.findings().stream()
                .anyMatch(finding -> finding.status() == DoctorFinding.Status.FAIL
                        && "mcp".equals(finding.category())
                        && "opencode.json".equals(finding.path())
                        && finding.message().contains("not valid JSON or JSONC")));
    }

    @Test
    void doctorTreatsAnEmptyLowerOpenCodeConfigAsAnEmptyLayer() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        String validConfig = Files.readString(tempDir.resolve("opencode.json"));
        Files.writeString(tempDir.resolve("opencode.json"), "");
        Files.writeString(tempDir.resolve("opencode.jsonc"), validConfig);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "tool calls retain OpenCode permission prompts", "No action required."));
    }

    @Test
    void unrelatedOpenCodeLegacyFieldsDoNotDowngradeMissingPermissions() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        writeMcpConfig(tempDir, OPENCODE, """
                {
                  "permission": {
                    "camel-knowledge_*": "ask",
                    "citrus_*": "ask"
                  },
                  "mcp": {
                    "camel": {"type": "local"},
                    "camel-knowledge": {"type": "local"},
                    "custom": {"type": "local", "alwaysAllow": ["custom_tool"]}
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "OpenCode permission 'camel_*' must be 'ask'", "permission prompts"));
        assertFalse(hasFinding(result, DoctorFinding.Status.WARN, "mcp",
                "Legacy OpenCode config has no supported permission", null));
    }

    @Test
    void ignoredManagedOpenCodeFieldsSuppressTheSuccessPass() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        writeMcpConfig(tempDir, OPENCODE, """
                {
                  "permission": {
                    "camel_*": "ask",
                    "camel-knowledge_*": "ask",
                    "citrus_*": "ask"
                  },
                  "mcp": {
                    "camel": {"type": "local", "autoApprove": []},
                    "camel-knowledge": {"type": "local"},
                    "citrus": {"type": "local"}
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.WARN, "mcp",
                "contains unsupported field autoApprove", "regenerate"));
        assertFalse(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "uses supported server fields", null));
    }

    @Test
    void missingOpenCodeCitrusServerFails() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        writeMcpConfig(tempDir, OPENCODE, """
                {
                  "permission": {
                    "camel_*": "ask",
                    "camel-knowledge_*": "ask",
                    "citrus_*": "ask"
                  },
                  "mcp": {
                    "camel": {"type": "local"},
                    "camel-knowledge": {"type": "local"}
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "MCP server 'citrus' is missing", "Regenerate"));
        assertFalse(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "uses supported server fields", null));
    }

    @Test
    void ignoredCitrusOpenCodeFieldsSuppressTheSuccessPass() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        writeMcpConfig(tempDir, OPENCODE, """
                {
                  "permission": {
                    "camel_*": "ask",
                    "camel-knowledge_*": "ask",
                    "citrus_*": "ask"
                  },
                  "mcp": {
                    "camel": {"type": "local"},
                    "camel-knowledge": {"type": "local"},
                    "citrus": {"type": "local", "autoApprove": []}
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.WARN, "mcp",
                "OpenCode MCP server 'citrus' contains unsupported field autoApprove", "regenerate"));
        assertFalse(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "uses supported server fields", null));
    }

    @Test
    void legacyOpenCodeExplicitNullPermissionStillFails() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        writeMcpConfig(tempDir, OPENCODE, """
                {
                  "permission": {
                    "camel_*": null
                  },
                  "mcp": {
                    "camel": {"type": "local", "autoApprove": []},
                    "camel-knowledge": {"type": "local", "alwaysAllow": []},
                    "citrus": {"type": "local", "alwaysAllow": []}
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "OpenCode permission namespace 'camel_*' must end with a namespace-wide 'ask' rule",
                "permission prompts"));
        assertFalse(hasFinding(result, DoctorFinding.Status.WARN, "mcp",
                "Legacy OpenCode config has no supported permission for 'camel_*'", null));
    }

    @Test
    void legacyOpenCodeNonObjectPermissionStillFails() throws Exception {
        createHealthyWorkspace(tempDir, OPENCODE);
        writeMcpConfig(tempDir, OPENCODE, """
                {
                  "permission": [],
                  "mcp": {
                    "camel": {"type": "local", "autoApprove": []},
                    "camel-knowledge": {"type": "local", "alwaysAllow": []}
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "OpenCode permission must be an object", "permission field"));
        assertFalse(hasFinding(result, DoctorFinding.Status.WARN, "mcp",
                "Legacy OpenCode config has no supported permission", null));
    }

    @Test
    void missingRegisteredAgentTemplateProducesUpgradeWarning() throws Exception {
        createHealthyWorkspace(tempDir, "bob2");
        Files.delete(tempDir.resolve(".bob/agents/camel-reviewer.md"));

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.WARN, "workspace",
                "Registered bob2 template assets are missing: .bob/agents/camel-reviewer.md", "--force"));
    }

    @Test
    void malformedCodexConfigProducesActionableFailure() throws Exception {
        createHealthyWorkspace(tempDir, CODEX);
        Files.writeString(tempDir.resolve(".codex/config.toml"), "[mcp_servers.camel\n");

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "Codex MCP config is not valid TOML",
                "camel-kit init --here --ai codex --force"));
    }

    @Test
    void codexMcpApprovalModeMustRemainPrompt() throws Exception {
        createHealthyWorkspace(tempDir, CODEX);
        Path config = tempDir.resolve(".codex/config.toml");
        Files.writeString(config, Files.readString(config)
                .replace("default_tools_approval_mode = \"prompt\"",
                        "default_tools_approval_mode = \"never\""));

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "default_tools_approval_mode must be 'prompt' for least privilege",
                "default_tools_approval_mode = \"prompt\""));
    }

    @Test
    void codexMcpEnabledToolsMustMatchTheGeneratedAllowlist() throws Exception {
        createHealthyWorkspace(tempDir, CODEX);
        Path config = tempDir.resolve(".codex/config.toml");
        String firstTool = EXPECTATIONS.camelMcpTools().iterator().next();
        Files.writeString(config, Files.readString(config)
                .replace("\"" + firstTool + "\"", "\"" + firstTool + "\", \"unexpected_tool\""));

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "enabled_tools has extra unexpected_tool",
                "update the allowlist"));
    }

    @Test
    void missingCodexCustomAgentProducesActionableFailure() throws Exception {
        createHealthyWorkspace(tempDir, CODEX);
        Files.delete(tempDir.resolve(".codex/agents/camel-implementer.toml"));

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "workspace",
                "Codex custom agent is missing",
                "camel-kit init --here --ai codex --force"));
    }

    @Test
    void codexCustomAgentRequiresDocumentedFields() throws Exception {
        createHealthyWorkspace(tempDir, CODEX);
        Path agentFile = tempDir.resolve(".codex/agents/camel-implementer.toml");
        Files.writeString(agentFile, Files.readString(agentFile)
                .replace("name = \"camel_implementer\"", "name = \"\""));

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "workspace",
                "Codex custom agent is missing required fields: name",
                "regenerate Codex agents"));
    }

    @Test
    void codexResearchAgentsMustRemainReadOnly() throws Exception {
        createHealthyWorkspace(tempDir, CODEX);
        Path agentFile = tempDir.resolve(".codex/agents/camel-security-reviewer.toml");
        Files.writeString(agentFile, Files.readString(agentFile)
                .replace("sandbox_mode = \"read-only\"\n", ""));

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "workspace",
                "must declare sandbox_mode = 'read-only'",
                "sandbox_mode = \"read-only\""));
    }

    @Test
    void copilotWildcardToolsSchemaWarnsWithoutFailing() throws Exception {
        createHealthyWorkspace(tempDir, "copilot");
        writeMcpConfig(tempDir, "copilot", """
                {
                  "mcpServers": {
                    "camel": {
                      "type": "stdio",
                      "command": "jbang",
                      "tools": ["*"]
                    },
                    "camel-knowledge": {
                      "type": "stdio",
                      "command": "jbang",
                      "tools": ["*"]
                    },
                    "citrus": {
                      "type": "stdio",
                      "command": "jbang",
                      "tools": ["*"]
                    }
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.WARN, "mcp",
                "MCP server 'camel' tools field allows all tools with '*'",
                "Prefer the generated Camel-Kit tool allowlist"));
        assertFalse(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "tool allowlists match", null));
    }

    @Test
    void copilotCommaSeparatedToolsStringIsAccepted() throws Exception {
        createHealthyWorkspace(tempDir, "copilot");
        writeMcpConfig(tempDir, "copilot", String.format(Locale.ROOT, """
                {
                  "mcpServers": {
                    "camel": {
                      "type": "stdio",
                      "command": "jbang",
                      "tools": "%s"
                    },
                    "camel-knowledge": {
                      "type": "stdio",
                      "command": "jbang",
                      "tools": "%s"
                    },
                    "citrus": {
                      "type": "stdio",
                      "command": "jbang",
                      "tools": "%s"
                    }
                  }
                }
                """, String.join(",", EXPECTATIONS.camelMcpTools()),
                String.join(",", EXPECTATIONS.knowledgeMcpTools()),
                String.join(",", EXPECTATIONS.citrusMcpTools())));

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "MCP config exists and tool allowlists match Camel-Kit expectations",
                "No action required."));
    }

    @Test
    void invalidCopilotToolsScalarProducesClearFailure() throws Exception {
        createHealthyWorkspace(tempDir, "copilot");
        writeMcpConfig(tempDir, "copilot", """
                {
                  "mcpServers": {
                    "camel": {
                      "type": "stdio",
                      "command": "jbang",
                      "tools": 42
                    },
                    "camel-knowledge": {
                      "type": "stdio",
                      "command": "jbang",
                      "tools": []
                    }
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "MCP server 'camel' tools must be an array or comma-separated string",
                "Set tools to"));
    }

    @Test
    void piDirectToolsTrueWarnsWithoutFailing() throws Exception {
        createHealthyWorkspace(tempDir, "pi");
        writeMcpConfig(tempDir, "pi", """
                {
                  "mcpServers": {
                    "camel": {
                      "command": "jbang",
                      "directTools": true
                    },
                    "camel-knowledge": {
                      "command": "jbang",
                      "directTools": true
                    },
                    "citrus": {
                      "command": "jbang",
                      "directTools": true
                    }
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.WARN, "mcp",
                "MCP server 'camel' directTools promotes all tools",
                "Prefer the generated Camel-Kit tool allowlist"));
        assertFalse(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "tool allowlists match", null));
    }

    @Test
    void invalidPiDirectToolsScalarProducesClearFailure() throws Exception {
        createHealthyWorkspace(tempDir, "pi");
        writeMcpConfig(tempDir, "pi", """
                {
                  "mcpServers": {
                    "camel": {
                      "command": "jbang",
                      "directTools": "camel_catalog_components"
                    },
                    "camel-knowledge": {
                      "command": "jbang",
                      "directTools": []
                    }
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "MCP server 'camel' directTools must be an array or true",
                "Set directTools"));
    }

    @Test
    void missingPiGuardPolicyProducesClearFailure() throws Exception {
        createHealthyWorkspace(tempDir, "pi");
        Files.delete(tempDir.resolve(".pi/camel-kit-guard-policy.json"));

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "workspace",
                "Pi safety guard policy is missing",
                "Run camel-kit init --here --ai pi --force"));
    }

    @Test
    void invalidPiGuardPolicyJsonUsesPiRegenerationCommand() throws Exception {
        createHealthyWorkspace(tempDir, "pi");
        writePiGuardPolicy("""
                {
                  "version": 1,
                  "rules": [
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "workspace",
                "Pi safety guard policy is not valid JSON",
                "camel-kit init --here --ai pi --force"));
    }

    @Test
    void malformedPiGuardPolicyRuleProducesClearFailure() throws Exception {
        createHealthyWorkspace(tempDir, "pi");
        writePiGuardPolicy("""
                {
                  "version": 1,
                  "rules": [
                    {
                      "toolNames": "bash",
                      "reason": ""
                    }
                  ]
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "workspace",
                "must declare a non-empty inputPattern",
                "camel-kit init --here --ai pi --force"));
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "workspace",
                "must declare a non-empty reason",
                "camel-kit init --here --ai pi --force"));
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "workspace",
                "has invalid toolNames; expected an array of strings",
                "camel-kit init --here --ai pi --force"));
    }

    @Test
    void nonCopilotToolsKeyDoesNotBypassLegacyAllowlistValidation() throws Exception {
        createHealthyWorkspace(tempDir, "bob");
        writeMcpConfig(tempDir, "bob", """
                {
                  "mcpServers": {
                    "camel": {
                      "command": "jbang",
                      "tools": ["*"]
                    },
                    "camel-knowledge": {
                      "command": "jbang",
                      "tools": ["*"]
                    }
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "MCP server 'camel' is missing autoApprove array",
                "Regenerate the MCP config"));
    }

    @Test
    void mixedCaseAgentNameStillResolvesRegisteredMcpPathForMcpChecks() throws Exception {
        createHealthyWorkspace(tempDir, "bob");
        Path configFile = tempDir.resolve(".camel-kit/config.properties");
        Files.writeString(configFile, Files.readString(configFile).replace("agent.name=bob", "agent.name=Bob"));

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "config", "Unknown agent.name 'Bob'", null));
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "MCP config exists and tool allowlists match Camel-Kit expectations",
                "No action required."));
        assertFalse(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "Cannot determine MCP config path for agent 'Bob'", null));
    }

    @Test
    void missingConfigReturnsActionableFailure() {
        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "config",
                ".camel-kit/config.properties is missing",
                "Run camel-kit init --here --ai <agent>"));
    }

    private void createHealthyWorkspace(Path root, String agentName) throws Exception {
        AgentConfig agent = AgentRegistry.get(agentName);
        Files.createDirectories(root.resolve(".camel-kit"));
        Files.writeString(root.resolve(".camel-kit/config.properties"), String.format(Locale.ROOT, """
                project.name=orders
                project.command-prefix=camel-kit
                project.runtime=main
                project.camelVersion=4.20.0
                project.platformBomVersion=4.20.0
                agent.name=%s
                agent.folder=%s
                """, agentName, agent.folder()));
        Files.writeString(root.resolve(".camel-kit/project-graph.json"), "{}");
        Files.writeString(root.resolve("mvnw"), "#!/bin/sh\n");
        Path commands = agent.generatesCommandStubs()
                ? root.resolve(agent.commandDirectory())
                : root.resolve(".codex/commands");
        InitContext context = new InitContext(
                agent, agentName, commands, root.resolve(agent.skillsDirectory()), root, "camel-kit", Printer.noop());
        AgentGeneratorFactory.create(agentName).generate(context);
    }

    private void writeMcpConfig(Path root, String agentName, String content) throws Exception {
        AgentConfig agent = AgentRegistry.get(agentName);
        Path mcpFile = root.resolve(agent.mcpConfigPath());
        Files.createDirectories(mcpFile.getParent());
        Files.writeString(mcpFile, content);
    }

    private void writePiGuardPolicy(String content) throws Exception {
        Files.writeString(tempDir.resolve(".pi/camel-kit-guard-policy.json"), content);
    }

    private String jsonArray(Collection<String> values) {
        return values.stream()
                .map(value -> "\"" + value + "\"")
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private boolean hasFinding(DoctorResult result, DoctorFinding.Status status, String category) {
        return hasFinding(result, status, category, null, null);
    }

    private boolean hasFinding(
            DoctorResult result,
            DoctorFinding.Status status,
            String category,
            String messageContains,
            String remediationContains) {
        List<DoctorFinding> findings = result.findings();
        for (DoctorFinding finding : findings) {
            boolean matches = status == finding.status()
                    && category.equals(finding.category())
                    && (messageContains == null || finding.message().contains(messageContains));
            if (matches && (remediationContains == null || finding.remediation().contains(remediationContains))) {
                return true;
            }
        }
        return false;
    }
}
