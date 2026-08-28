package io.github.luigidemasi.camelkit.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.luigidemasi.camelkit.config.AgentDescriptor;
import io.github.luigidemasi.camelkit.config.AgentRegistry;

final class PersonaResourceInstaller {

    static final List<String> PERSONAS = List.of(
            "acr-moderator",
            "catalog-researcher",
            "code-quality-reviewer",
            "critic-behavioral-equivalence",
            "critic-boundary-compliance",
            "critic-performance",
            "critic-route-architecture",
            "critic-security",
            "implementation-engineer",
            "integration-architect",
            "knowledge-researcher",
            "migration-specialist",
            "spec-compliance-reviewer",
            "test-engineer");

    private final TemplateResourceCopier copier = new TemplateResourceCopier();

    void install(InitContext ctx) throws IOException {
        AgentDescriptor descriptor = AgentRegistry.descriptor(ctx.agentName());
        List<AgentDescriptor.TemplateInstall> personaTemplates = descriptor.templates().stream()
                .filter(PersonaResourceInstaller::isPersonaTemplate)
                .toList();
        if (personaTemplates.isEmpty()) {
            return;
        }
        String targetDirectory = targetDirectory(ctx).orElseThrow();
        Set<String> registeredPersonas = personaTemplates.stream()
                .map(template -> Path.of(template.target()).getFileName().toString().replaceFirst("\\.md$", ""))
                .collect(Collectors.toSet());
        if (!registeredPersonas.equals(Set.copyOf(PERSONAS))) {
            throw new IllegalStateException(
                    "Agent descriptor '" + ctx.agentName() + "' must register the complete persona library");
        }
        for (AgentDescriptor.TemplateInstall template : personaTemplates) {
            Path installed = ctx.projectDir().resolve(template.target());
            copier.copy(template.source(), installed);
            rewriteReferences(installed, targetDirectory);
        }
    }

    static Optional<String> targetDirectory(InitContext ctx) {
        Set<String> directories = AgentRegistry.descriptor(ctx.agentName()).templates().stream()
                .filter(PersonaResourceInstaller::isPersonaTemplate)
                .map(template -> parentTarget(template.target()))
                .collect(Collectors.toSet());
        if (directories.size() > 1) {
            throw new IllegalStateException(
                    "Agent descriptor '" + ctx.agentName() + "' registers personas in multiple directories");
        }
        return directories.stream().findFirst();
    }

    static String parentTarget(String target) {
        int separator = target.lastIndexOf('/');
        if (separator < 1) {
            throw new IllegalStateException("Persona target must include a parent directory: " + target);
        }
        return target.substring(0, separator);
    }

    static boolean isPersonaTemplate(AgentDescriptor.TemplateInstall template) {
        Path source = Path.of(template.source());
        return source.getNameCount() == 2 && "agents".equals(source.getName(0).toString())
                && source.getFileName().toString().endsWith(".md");
    }

    static void rewriteReferences(Path markdown, String targetDirectory) throws IOException {
        String content = Files.readString(markdown);
        String rewritten = rewriteReferences(content, targetDirectory);
        if (!rewritten.equals(content)) {
            Files.writeString(markdown, rewritten);
        }
    }

    static String rewriteReferences(String content, String targetDirectory) {
        String rewritten = content
                .replace("`agents/`", "`" + targetDirectory + "/`")
                .replace("agents/[persona].md", targetDirectory + "/[persona].md")
                .replace("agents/critic-<lane>.md", targetDirectory + "/critic-<lane>.md");
        for (String persona : PERSONAS) {
            rewritten = rewritten.replace(
                    "agents/" + persona + ".md",
                    targetDirectory + "/" + persona + ".md");
        }
        return rewritten;
    }
}
