package io.github.luigidemasi.camelkit.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.luigidemasi.camelkit.workflow.WorkflowManifest;
import io.github.luigidemasi.camelkit.workflow.WorkflowManifestLoader;

public class DefaultGenerator implements AgentGenerator {

    private final AgentsMdGenerator agentsMdGenerator;
    private final CommandStubGenerator commandStubGenerator;
    private final SkillResourceInstaller skillResourceInstaller;
    private final TraitApplicator traitApplicator;
    private final McpConfigGenerator mcpConfigGenerator;
    private final TemplateResourceCopier templateResourceCopier;

    public DefaultGenerator() {
        this(
             new AgentsMdGenerator(),
             new CommandStubGenerator(),
             new SkillResourceInstaller(),
             new TraitApplicator(),
             new McpConfigGenerator(),
             new TemplateResourceCopier());
    }

    DefaultGenerator(
                     AgentsMdGenerator agentsMdGenerator,
                     CommandStubGenerator commandStubGenerator,
                     SkillResourceInstaller skillResourceInstaller,
                     TraitApplicator traitApplicator,
                     McpConfigGenerator mcpConfigGenerator,
                     TemplateResourceCopier templateResourceCopier) {
        this.agentsMdGenerator = agentsMdGenerator;
        this.commandStubGenerator = commandStubGenerator;
        this.skillResourceInstaller = skillResourceInstaller;
        this.traitApplicator = traitApplicator;
        this.mcpConfigGenerator = mcpConfigGenerator;
        this.templateResourceCopier = templateResourceCopier;
    }

    @Override
    public void generate(InitContext ctx) throws Exception {
        WorkflowManifest workflow = loadWorkflowManifest();
        generateBaseAssets(ctx, workflow);
        applyTraits(ctx, workflow);
        generateMcpConfig(ctx, workflow);
    }

    protected WorkflowManifest loadWorkflowManifest() throws IOException {
        return WorkflowManifestLoader.loadDefault();
    }

    protected void generateBaseAssets(InitContext ctx, WorkflowManifest workflow) throws Exception {
        Files.createDirectories(ctx.commandsDir());
        Files.createDirectories(ctx.skillsDir());
        agentsMdGenerator.generate(ctx);
        commandStubGenerator.generate(ctx, workflow);
        skillResourceInstaller.install(ctx);
    }

    protected void applyTraits(InitContext ctx, WorkflowManifest workflow) throws Exception {
        traitApplicator.apply(ctx, workflow);
    }

    protected void generateMcpConfig(InitContext ctx, WorkflowManifest workflow) throws Exception {
        mcpConfigGenerator.generate(ctx, workflow);
    }

    protected void copyTemplateResource(String resourcePath, Path target) throws IOException {
        templateResourceCopier.copy(resourcePath, target);
    }
}
