package io.github.luigidemasi.camelkit.command;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.luigidemasi.camelkit.workflow.WorkflowManifest;
import io.github.luigidemasi.camelkit.workflow.WorkflowManifestLoader;

/**
 * Expected generated workspace contract used by {@link DoctorCommand}.
 */
final class DoctorExpectations {

    private final List<String> userCommands;
    private final List<String> requiredSkills;
    private final Set<String> camelMcpTools;
    private final Set<String> knowledgeMcpTools;

    private DoctorExpectations(
                               List<String> userCommands,
                               List<String> requiredSkills,
                               Set<String> camelMcpTools,
                               Set<String> knowledgeMcpTools) {
        this.userCommands = List.copyOf(userCommands);
        this.requiredSkills = List.copyOf(requiredSkills);
        this.camelMcpTools = Collections.unmodifiableSet(new LinkedHashSet<>(camelMcpTools));
        this.knowledgeMcpTools = Collections.unmodifiableSet(new LinkedHashSet<>(knowledgeMcpTools));
    }

    static DoctorExpectations loadDefault() {
        try {
            return from(WorkflowManifestLoader.loadDefault());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load workflow manifest for doctor expectations", e);
        }
    }

    static DoctorExpectations from(WorkflowManifest workflow) {
        return new DoctorExpectations(
                workflow.generatedCommandStubs().stream()
                        .map(WorkflowManifest.WorkflowCommand::name)
                        .toList(),
                workflow.skills().stream()
                        .map(WorkflowManifest.WorkflowSkill::name)
                        .toList(),
                orderedSet(workflow.mcpServer("camel").allowedTools()),
                orderedSet(workflow.mcpServer("camel-knowledge").allowedTools()));
    }

    List<String> userCommands() {
        return userCommands;
    }

    List<String> requiredSkills() {
        return requiredSkills;
    }

    Set<String> camelMcpTools() {
        return camelMcpTools;
    }

    Set<String> knowledgeMcpTools() {
        return knowledgeMcpTools;
    }

    private static Set<String> orderedSet(List<String> values) {
        return new LinkedHashSet<>(values);
    }
}
