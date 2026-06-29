package io.github.luigidemasi.camelkit.workflow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Validates cross-references in the workflow manifest.
 */
final class WorkflowManifestValidator {

    private static final Set<String> REQUIRED_MCP_SERVERS = Set.of("camel", "camel-knowledge", "citrus");

    private WorkflowManifestValidator() {
    }

    static void validateOrThrow(WorkflowManifest manifest) throws ManifestValidationException {
        List<String> errors = validate(manifest);
        if (!errors.isEmpty()) {
            throw new ManifestValidationException(errors);
        }
    }

    static List<String> validate(WorkflowManifest manifest) {
        List<String> errors = new ArrayList<>();
        if (manifest == null) {
            errors.add("manifest must not be null");
            return errors;
        }

        Map<String, WorkflowManifest.WorkflowCommand> commands = indexBy(
                "commands", "name", manifest.commands(), WorkflowManifest.WorkflowCommand::name, errors);
        Map<String, WorkflowManifest.WorkflowSkill> skills = indexBy(
                "skills", "name", manifest.skills(), WorkflowManifest.WorkflowSkill::name, errors);
        Map<String, WorkflowManifest.WorkflowStage> stages = indexBy(
                "stages", "id", manifest.stages(), WorkflowManifest.WorkflowStage::id, errors);
        Map<String, WorkflowManifest.WorkflowArtifact> artifacts = indexBy(
                "artifacts", "id", manifest.artifacts(), WorkflowManifest.WorkflowArtifact::id, errors);
        Map<String, WorkflowManifest.WorkflowMcpServer> mcpServers = indexBy(
                "mcp_servers", "id", manifest.mcpServers(), WorkflowManifest.WorkflowMcpServer::id, errors);

        validateCommands(manifest.commands(), commands, skills, errors);
        validateSkills(manifest.skills(), commands, errors);
        validateStages(manifest.stages(), stages.keySet(), skills.keySet(), errors);
        validateArtifacts(manifest.artifacts(), skills.keySet(), errors);
        validateMcpServers(mcpServers.keySet(), errors);

        return errors;
    }

    private static void validateCommands(
            List<WorkflowManifest.WorkflowCommand> commands,
            Map<String, WorkflowManifest.WorkflowCommand> commandsByName,
            Map<String, WorkflowManifest.WorkflowSkill> skillsByName,
            List<String> errors) {
        commands = nullToEmpty(commands);
        for (int i = 0; i < commands.size(); i++) {
            WorkflowManifest.WorkflowCommand command = commands.get(i);
            if (command == null) {
                continue;
            }
            String commandName = label(command.name(), i);
            validateRequired("commands[" + commandName + "].skill", command.skill(), errors);

            WorkflowManifest.WorkflowSkill skill = skillsByName.get(command.skill());
            if (!isBlank(command.skill()) && skill == null) {
                errors.add("commands[" + commandName + "].skill references unknown skill '" + command.skill() + "'");
            }
            if (skill != null && command.generatedStub()
                    && !Objects.equals(command.name(), skill.generatedCommand())) {
                errors.add("commands[" + commandName + "].generated_stub is true but skills["
                           + skill.name() + "].generated_command is '" + skill.generatedCommand() + "'");
            }
            if (skill != null && command.generatedStub()
                    && commandsByName.get(skill.generatedCommand()) != command) {
                errors.add("skills[" + skill.name() + "].generated_command does not point back to command '"
                           + command.name() + "'");
            }
        }
    }

    private static void validateSkills(
            List<WorkflowManifest.WorkflowSkill> skills,
            Map<String, WorkflowManifest.WorkflowCommand> commandsByName,
            List<String> errors) {
        skills = nullToEmpty(skills);
        for (int i = 0; i < skills.size(); i++) {
            WorkflowManifest.WorkflowSkill skill = skills.get(i);
            if (skill == null) {
                continue;
            }
            String skillName = label(skill.name(), i);
            if (isBlank(skill.generatedCommand())) {
                continue;
            }

            WorkflowManifest.WorkflowCommand command = commandsByName.get(skill.generatedCommand());
            if (command == null) {
                errors.add("skills[" + skillName + "].generated_command references unknown command '"
                           + skill.generatedCommand() + "'");
                continue;
            }
            if (!command.generatedStub()) {
                errors.add("skills[" + skillName + "].generated_command references non-generated command '"
                           + command.name() + "'");
            }
            if (!Objects.equals(skill.name(), command.skill())) {
                errors.add("skills[" + skillName + "].generated_command references command '" + command.name()
                           + "' but that command points to skill '" + command.skill() + "'");
            }
        }
    }

    private static void validateStages(
            List<WorkflowManifest.WorkflowStage> stages,
            Set<String> stageIds,
            Set<String> skillNames,
            List<String> errors) {
        stages = nullToEmpty(stages);
        for (int i = 0; i < stages.size(); i++) {
            WorkflowManifest.WorkflowStage stage = stages.get(i);
            if (stage == null) {
                continue;
            }
            String stageId = label(stage.id(), i);
            validateRequired("stages[" + stageId + "].skill", stage.skill(), errors);
            if (!isBlank(stage.skill()) && !skillNames.contains(stage.skill())) {
                errors.add("stages[" + stageId + "].skill references unknown skill '" + stage.skill() + "'");
            }
            for (String transition : nullToEmpty(stage.transitions())) {
                if (isBlank(transition)) {
                    errors.add("stages[" + stageId + "].transitions contains a blank stage id");
                } else if (!stageIds.contains(transition)) {
                    errors.add("stages[" + stageId + "].transitions references unknown stage '" + transition + "'");
                }
            }
        }
    }

    private static void validateArtifacts(
            List<WorkflowManifest.WorkflowArtifact> artifacts,
            Set<String> skillNames,
            List<String> errors) {
        artifacts = nullToEmpty(artifacts);
        for (int i = 0; i < artifacts.size(); i++) {
            WorkflowManifest.WorkflowArtifact artifact = artifacts.get(i);
            if (artifact == null) {
                continue;
            }
            String artifactId = label(artifact.id(), i);
            validateRequired("artifacts[" + artifactId + "].path", artifact.path(), errors);
            validateSkillReferences(
                    "artifacts[" + artifactId + "].produced_by", artifact.producedBy(), skillNames, errors);
            validateSkillReferences(
                    "artifacts[" + artifactId + "].consumed_by", artifact.consumedBy(), skillNames, errors);
        }
    }

    private static void validateMcpServers(Set<String> mcpServerIds, List<String> errors) {
        for (String required : REQUIRED_MCP_SERVERS) {
            if (!mcpServerIds.contains(required)) {
                errors.add("mcp_servers missing required server '" + required + "'");
            }
        }
    }

    private static void validateSkillReferences(
            String path, List<String> referencedSkills, Set<String> skillNames, List<String> errors) {
        referencedSkills = nullToEmpty(referencedSkills);
        for (String skill : referencedSkills) {
            if (isBlank(skill)) {
                errors.add(path + " contains a blank skill name");
            } else if (!skillNames.contains(skill)) {
                errors.add(path + " references unknown skill '" + skill + "'");
            }
        }
    }

    private static <T> Map<String, T> indexBy(
            String section,
            String idField,
            List<T> items,
            Function<T, String> idExtractor,
            List<String> errors) {
        items = nullToEmpty(items);
        Map<String, T> values = new LinkedHashMap<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (int i = 0; i < items.size(); i++) {
            T item = items.get(i);
            if (item == null) {
                errors.add(section + "[" + i + "] must not be null");
                continue;
            }
            String id = idExtractor.apply(item);
            String path = section + "[" + i + "]." + idField;
            if (isBlank(id)) {
                errors.add(path + " must not be blank");
                continue;
            }
            if (values.putIfAbsent(id, items.get(i)) != null) {
                duplicates.add(id);
            }
        }
        for (String duplicate : duplicates) {
            errors.add(section + " contains duplicate " + idField + " '" + duplicate + "'");
        }
        if (items.isEmpty()) {
            errors.add(section + " must not be empty");
        }
        return values;
    }

    private static <T> List<T> nullToEmpty(List<T> items) {
        return items == null ? List.of() : items;
    }

    private static void validateRequired(String path, String value, List<String> errors) {
        if (isBlank(value)) {
            errors.add(path + " must not be blank");
        }
    }

    private static String label(String value, int index) {
        return isBlank(value) ? String.valueOf(index) : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
