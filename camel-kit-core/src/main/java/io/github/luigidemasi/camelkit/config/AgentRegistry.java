package io.github.luigidemasi.camelkit.config;

import java.util.Map;
import java.util.Set;

/**
 * Registry of supported AI coding agents.
 */
public final class AgentRegistry {

    private static final Map<String, AgentConfig> AGENTS = Map.of(
        "bob", new AgentConfig(
            "IBM Project Bob",
            ".bob/commands",
            "md",
            "$ARGUMENTS",
            "IBM's AI-powered development assistant"
        ),
        "gemini", new AgentConfig(
            "Gemini CLI",
            ".gemini/commands",
            "toml",
            "{{args}}",
            "Google's Gemini CLI"
        ),
        "claude", new AgentConfig(
            "Claude Code",
            ".claude/commands",
            "md",
            "$ARGUMENTS",
            "Anthropic's Claude Code CLI"
        ),
        "qwen", new AgentConfig(
            "Qwen Code",
            ".qwen/commands",
            "md",
            "$ARGUMENTS",
            "Alibaba's Qwen Code CLI"
        ),
        "opencode", new AgentConfig(
            "OpenCode",
            ".opencode/commands",
            "md",
            "$ARGUMENTS",
            "AI coding agent for the terminal"
        )
    );

    private AgentRegistry() {
        // Utility class
    }

    public static AgentConfig get(String name) {
        return AGENTS.get(name);
    }

    public static boolean contains(String name) {
        return AGENTS.containsKey(name);
    }

    public static Set<String> names() {
        return AGENTS.keySet();
    }

    public static Map<String, AgentConfig> all() {
        return AGENTS;
    }
}
