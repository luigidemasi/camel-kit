package io.github.luigidemasi.camelkit.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registry of supported AI coding agents.
 */
public final class AgentRegistry {

    private static final Map<String, AgentDescriptor> DESCRIPTORS = AgentDescriptorLoader.loadDefault();
    private static final Map<String, AgentConfig> AGENTS = configsFrom(DESCRIPTORS);

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

    public static AgentDescriptor descriptor(String name) {
        return DESCRIPTORS.get(name);
    }

    public static Map<String, AgentDescriptor> descriptors() {
        return DESCRIPTORS;
    }

    private static Map<String, AgentConfig> configsFrom(Map<String, AgentDescriptor> descriptors) {
        Map<String, AgentConfig> configs = new LinkedHashMap<>();
        descriptors.forEach((name, descriptor) -> configs.put(name, descriptor.toAgentConfig()));
        return Collections.unmodifiableMap(configs);
    }
}
