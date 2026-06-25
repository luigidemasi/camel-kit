package io.github.luigidemasi.camelkit.plan;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Parses implementation plans to extract task metadata, builds a dependency graph from explicit, logical, and file
 * dependencies, and computes parallel execution waves.
 */
public class PlanAnalyzer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    // Matches "### Task N: Name" or "### Task N-M: Name"
    private static final Pattern TASK_HEADER = Pattern.compile(
            "^###\\s+Task\\s+(\\d+(?:-\\d+)?):\\s+(.+)$", Pattern.MULTILINE);

    // Matches "- Create/Modify/Test/Delete: `path`"
    private static final Pattern FILE_LINE = Pattern.compile(
            "^-\\s+(?:Create|Modify|Test|Delete):\\s+`([^`]+)`$", Pattern.MULTILINE);

    /**
     * A task extracted from the plan. Markdown-only plans populate only number, name, and files; structured plans also
     * populate logical resource metadata and explicit dependencies.
     */
    public record TaskInfo(
            int number,
            String name,
            List<String> files,
            Map<String, List<String>> provides,
            Map<String, List<String>> consumes,
            List<Integer> dependsOn) {

        public TaskInfo(int number, String name, List<String> files) {
            this(number, name, files, Map.of(), Map.of(), List.of());
        }

        public TaskInfo {
            files = List.copyOf(files == null ? List.of() : files);
            provides = copyResourceMap(provides);
            consumes = copyResourceMap(consumes);
            dependsOn = List.copyOf(dependsOn == null ? List.of() : dependsOn);
        }
    }

    /**
     * A wave of tasks that can be executed in parallel.
     */
    public record Wave(int waveNumber, List<Integer> taskNumbers) {
    }

    /**
     * Parses tasks from a structured plan metadata block when present, otherwise falls back to Markdown task headers
     * and file lists.
     */
    public List<TaskInfo> parseTasks(String planMarkdown) {
        List<TaskInfo> markdownTasks = parseMarkdownTasks(planMarkdown);
        Optional<String> metadataYaml = extractStructuredMetadata(planMarkdown);
        if (metadataYaml.isPresent()) {
            List<TaskInfo> structuredTasks = parseStructuredTasks(metadataYaml.get());
            validateStructuredTasksMatchMarkdownTasks(markdownTasks, structuredTasks);
            return structuredTasks;
        }
        return markdownTasks;
    }

    private List<TaskInfo> parseMarkdownTasks(String planMarkdown) {
        if (planMarkdown == null || planMarkdown.isBlank()) {
            return List.of();
        }

        List<TaskInfo> tasks = new ArrayList<>();
        Matcher headerMatcher = TASK_HEADER.matcher(planMarkdown);

        List<int[]> headerPositions = new ArrayList<>();
        List<String[]> headerData = new ArrayList<>();

        while (headerMatcher.find()) {
            headerPositions.add(new int[]{headerMatcher.start(), headerMatcher.end()});
            String numberStr = headerMatcher.group(1);
            // For combined tasks like "1-2", use the first number
            int number = Integer.parseInt(numberStr.contains("-")
                    ? numberStr.substring(0, numberStr.indexOf('-'))
                    : numberStr);
            headerData.add(new String[]{String.valueOf(number), headerMatcher.group(2).trim()});
        }

        for (int i = 0; i < headerPositions.size(); i++) {
            int sectionStart = headerPositions.get(i)[1];
            int sectionEnd = (i + 1 < headerPositions.size())
                    ? headerPositions.get(i + 1)[0]
                    : planMarkdown.length();

            String section = planMarkdown.substring(sectionStart, sectionEnd);
            List<String> files = extractFiles(section);

            int number = Integer.parseInt(headerData.get(i)[0]);
            String name = headerData.get(i)[1];
            tasks.add(new TaskInfo(number, name, files));
        }

        return tasks;
    }

    private Optional<String> extractStructuredMetadata(String planMarkdown) {
        if (planMarkdown == null || planMarkdown.isBlank()) {
            return Optional.empty();
        }

        String normalized = planMarkdown.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        boolean inMetadataFence = false;
        StringBuilder metadata = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (!inMetadataFence) {
                if (trimmed.startsWith("```") && isPlanMetadataFence(trimmed.substring(3).trim())) {
                    inMetadataFence = true;
                    metadata.setLength(0);
                }
                continue;
            }

            if (trimmed.startsWith("```")) {
                return Optional.of(metadata.toString());
            }

            metadata.append(line).append('\n');
        }

        return Optional.empty();
    }

    private boolean isPlanMetadataFence(String infoString) {
        String normalized = infoString.toLowerCase(Locale.ROOT).trim();
        return normalized.equals("yaml plan-metadata") || normalized.equals("yml plan-metadata")
                || normalized.equals("plan-metadata yaml") || normalized.equals("plan-metadata yml");
    }

    private List<TaskInfo> parseStructuredTasks(String metadataYaml) {
        try {
            JsonNode root = YAML_MAPPER.readTree(metadataYaml);
            if (root == null || root.isNull()) {
                throw new IllegalArgumentException("plan metadata must contain a tasks array");
            }
            JsonNode tasksNode = root.path("tasks");
            if (tasksNode.isMissingNode() && root.has("planMetadata")) {
                tasksNode = root.path("planMetadata").path("tasks");
            }
            if (!tasksNode.isArray()) {
                throw new IllegalArgumentException("plan metadata must contain a tasks array");
            }

            List<TaskInfo> tasks = new ArrayList<>();
            for (JsonNode taskNode : tasksNode) {
                int number = parseTaskNumber(taskNode);
                String name = parseTaskName(taskNode);
                List<String> files = parseFiles(taskNode.path("files"));
                Map<String, List<String>> provides = parseResourceMap(taskNode.path("provides"));
                Map<String, List<String>> consumes = parseResourceMap(taskNode.path("consumes"));
                List<Integer> dependsOn = parseIntegerList(taskNode.path("dependsOn"));
                tasks.add(new TaskInfo(number, name, files, provides, consumes, dependsOn));
            }

            validateDependsOnReferences(tasks);
            return tasks;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to parse plan metadata: " + e.getMessage(), e);
        }
    }

    private void validateStructuredTasksMatchMarkdownTasks(
            List<TaskInfo> markdownTasks, List<TaskInfo> structuredTasks) {
        if (markdownTasks.isEmpty()) {
            return;
        }

        Map<Integer, TaskInfo> structuredByNumber = indexTasksByNumber(structuredTasks, "plan metadata");
        Map<Integer, TaskInfo> markdownByNumber = indexTasksByNumber(markdownTasks, "Markdown");

        for (TaskInfo markdownTask : markdownTasks) {
            TaskInfo structuredTask = structuredByNumber.get(markdownTask.number());
            if (structuredTask == null) {
                throw new IllegalArgumentException(
                        "plan metadata is missing task id " + markdownTask.number()
                                                   + " for Markdown task: " + markdownTask.name());
            }
            if (!structuredTask.name().equals(markdownTask.name())) {
                throw new IllegalArgumentException(
                        "plan metadata task " + markdownTask.number()
                                                   + " title does not match Markdown task title: expected '"
                                                   + markdownTask.name()
                                                   + "' but found '" + structuredTask.name() + "'");
            }
        }

        for (TaskInfo structuredTask : structuredTasks) {
            if (!markdownByNumber.containsKey(structuredTask.number())) {
                throw new IllegalArgumentException(
                        "plan metadata task id " + structuredTask.number()
                                                   + " has no matching Markdown task");
            }
        }
    }

    private Map<Integer, TaskInfo> indexTasksByNumber(List<TaskInfo> tasks, String sourceName) {
        Map<Integer, TaskInfo> tasksByNumber = new LinkedHashMap<>();
        for (TaskInfo task : tasks) {
            TaskInfo previous = tasksByNumber.putIfAbsent(task.number(), task);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate " + sourceName + " task id: " + task.number());
            }
        }
        return tasksByNumber;
    }

    private int parseTaskNumber(JsonNode taskNode) {
        JsonNode idNode = taskNode.has("id") ? taskNode.get("id") : taskNode.get("number");
        if (idNode == null || idNode.isMissingNode() || idNode.isNull()) {
            throw new IllegalArgumentException("plan metadata task is missing id");
        }
        if (idNode.isInt() || idNode.isLong()) {
            return idNode.asInt();
        }
        String id = idNode.asText().trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("plan metadata task has blank id");
        }
        return Integer.parseInt(id);
    }

    private String parseTaskName(JsonNode taskNode) {
        JsonNode titleNode = taskNode.has("title") ? taskNode.get("title") : taskNode.get("name");
        if (titleNode == null || titleNode.isMissingNode() || titleNode.isNull()) {
            throw new IllegalArgumentException("plan metadata task is missing title");
        }
        String title = titleNode.asText().trim();
        if (title.isEmpty()) {
            throw new IllegalArgumentException("plan metadata task has blank title");
        }
        return title;
    }

    private List<String> parseFiles(JsonNode filesNode) {
        if (filesNode == null || filesNode.isMissingNode() || filesNode.isNull()) {
            return List.of();
        }

        LinkedHashSet<String> files = new LinkedHashSet<>();
        if (filesNode.isArray()) {
            addTextValues(files, filesNode);
        } else if (filesNode.isObject()) {
            filesNode.fields().forEachRemaining(entry -> addTextValues(files, entry.getValue()));
        } else {
            addTextValue(files, filesNode);
        }
        return List.copyOf(files);
    }

    private Map<String, List<String>> parseResourceMap(JsonNode resourceNode) {
        if (resourceNode == null || resourceNode.isMissingNode() || resourceNode.isNull() || !resourceNode.isObject()) {
            return Map.of();
        }

        Map<String, List<String>> resources = new LinkedHashMap<>();
        resourceNode.fields().forEachRemaining(entry -> {
            LinkedHashSet<String> values = new LinkedHashSet<>();
            addTextValues(values, entry.getValue());
            if (!values.isEmpty()) {
                resources.put(entry.getKey(), List.copyOf(values));
            }
        });
        return resources;
    }

    private List<Integer> parseIntegerList(JsonNode listNode) {
        if (listNode == null || listNode.isMissingNode() || listNode.isNull()) {
            return List.of();
        }

        LinkedHashSet<Integer> values = new LinkedHashSet<>();
        if (listNode.isArray()) {
            for (JsonNode item : listNode) {
                values.add(parseInteger(item));
            }
        } else {
            values.add(parseInteger(listNode));
        }
        return List.copyOf(values);
    }

    private int parseInteger(JsonNode node) {
        if (node.isInt() || node.isLong()) {
            return node.asInt();
        }
        return Integer.parseInt(node.asText().trim());
    }

    private void addTextValues(Set<String> values, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                addTextValue(values, item);
            }
            return;
        }
        addTextValue(values, node);
    }

    private void addTextValue(Set<String> values, JsonNode node) {
        String value = node.asText().trim();
        if (!value.isEmpty()) {
            values.add(value);
        }
    }

    private void validateDependsOnReferences(List<TaskInfo> tasks) {
        Map<Integer, TaskInfo> tasksByNumber = indexTasksByNumber(tasks, "plan metadata");

        for (TaskInfo task : tasks) {
            for (int dependency : task.dependsOn()) {
                if (!tasksByNumber.containsKey(dependency)) {
                    throw new IllegalArgumentException(
                            "task " + task.number() + " depends on unknown task " + dependency);
                }
            }
        }
    }

    private List<String> extractFiles(String section) {
        List<String> files = new ArrayList<>();
        // File section ends at the first "- [ ]" step line or end of section
        int stepIndex = section.indexOf("- [ ]");
        String fileSection = (stepIndex >= 0) ? section.substring(0, stepIndex) : section;

        Matcher fileMatcher = FILE_LINE.matcher(fileSection);
        while (fileMatcher.find()) {
            files.add(fileMatcher.group(1));
        }
        return files;
    }

    /**
     * Finds dependencies between tasks. Explicit dependsOn entries are authoritative, logical dependencies are inferred
     * when a task consumes a resource another task provides, and file dependencies retain the Markdown-compatible rule
     * where a later task depends on an earlier task if they share any file.
     *
     * @return map from task number to list of dependency task numbers
     */
    public Map<Integer, List<Integer>> findDependencies(List<TaskInfo> tasks) {
        Map<Integer, List<Integer>> deps = new LinkedHashMap<>();
        Set<Integer> taskNumbers = new LinkedHashSet<>();
        tasks.forEach(task -> taskNumbers.add(task.number()));

        for (int i = 0; i < tasks.size(); i++) {
            TaskInfo current = tasks.get(i);
            LinkedHashSet<Integer> currentDeps = new LinkedHashSet<>();

            for (int explicitDependency : current.dependsOn()) {
                if (taskNumbers.contains(explicitDependency) && explicitDependency != current.number()) {
                    currentDeps.add(explicitDependency);
                }
            }

            for (TaskInfo provider : tasks) {
                if (provider.number() != current.number() && consumesProvidedResource(current, provider)) {
                    currentDeps.add(provider.number());
                }
            }

            for (int j = 0; j < i; j++) {
                TaskInfo earlier = tasks.get(j);
                if (sharesAnyFile(current, earlier)) {
                    currentDeps.add(earlier.number());
                }
            }

            if (!currentDeps.isEmpty()) {
                deps.put(current.number(), List.copyOf(currentDeps));
            }
        }

        return deps;
    }

    private boolean sharesAnyFile(TaskInfo a, TaskInfo b) {
        for (String file : a.files()) {
            if (b.files().contains(file)) {
                return true;
            }
        }
        return false;
    }

    private boolean consumesProvidedResource(TaskInfo consumer, TaskInfo provider) {
        for (Map.Entry<String, List<String>> consumedEntry : consumer.consumes().entrySet()) {
            String consumedCategory = normalizeCategory(consumedEntry.getKey());
            Set<String> consumedValues = new LinkedHashSet<>(consumedEntry.getValue());

            for (Map.Entry<String, List<String>> providedEntry : provider.provides().entrySet()) {
                if (consumedCategory.equals(normalizeCategory(providedEntry.getKey()))
                        && overlaps(consumedValues, providedEntry.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String normalizeCategory(String category) {
        return category.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }

    private boolean overlaps(Set<String> left, List<String> right) {
        for (String value : right) {
            if (left.contains(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Computes parallel execution waves using topological sort. Wave 1: tasks with no dependencies. Wave N: tasks whose
     * dependencies are all in waves 1..N-1.
     */
    public List<Wave> computeWaves(List<TaskInfo> tasks) {
        Map<Integer, List<Integer>> deps = findDependencies(tasks);
        Set<Integer> allTaskNumbers = new LinkedHashSet<>();
        tasks.forEach(t -> allTaskNumbers.add(t.number()));

        Set<Integer> assigned = new HashSet<>();
        List<Wave> waves = new ArrayList<>();
        int waveNumber = 0;

        while (assigned.size() < allTaskNumbers.size()) {
            waveNumber++;
            List<Integer> currentWave = new ArrayList<>();

            for (int taskNum : allTaskNumbers) {
                if (assigned.contains(taskNum))
                    continue;

                List<Integer> taskDeps = deps.getOrDefault(taskNum, List.of());
                if (assigned.containsAll(taskDeps)) {
                    currentWave.add(taskNum);
                }
            }

            if (currentWave.isEmpty()) {
                throw new IllegalArgumentException(
                        "cyclic task dependencies detected among tasks: " + remainingTasks(allTaskNumbers, assigned));
            }

            waves.add(new Wave(waveNumber, currentWave));
            assigned.addAll(currentWave);
        }

        return waves;
    }

    private List<Integer> remainingTasks(Set<Integer> allTaskNumbers, Set<Integer> assigned) {
        List<Integer> remaining = new ArrayList<>();
        for (int taskNum : allTaskNumbers) {
            if (!assigned.contains(taskNum)) {
                remaining.add(taskNum);
            }
        }
        return remaining;
    }

    /**
     * Outputs waves, dependencies, and task summaries as JSON.
     */
    public String toJson(List<TaskInfo> tasks) {
        Map<Integer, List<Integer>> deps = findDependencies(tasks);
        List<Wave> waves = computeWaves(tasks);

        // Build a lookup from task number to name
        Map<Integer, String> namesByNumber = new LinkedHashMap<>();
        tasks.forEach(t -> namesByNumber.put(t.number(), t.name()));

        ObjectNode root = MAPPER.createObjectNode();

        // Waves
        ArrayNode wavesArray = root.putArray("waves");
        for (Wave wave : waves) {
            ObjectNode waveNode = wavesArray.addObject();
            waveNode.put("wave", wave.waveNumber());

            ArrayNode tasksArr = waveNode.putArray("tasks");
            wave.taskNumbers().forEach(tasksArr::add);

            ArrayNode namesArr = waveNode.putArray("taskNames");
            wave.taskNumbers()
                    .forEach(n -> namesArr.add("Task " + n + ": " + namesByNumber.getOrDefault(n, "Unknown")));
        }

        // Dependencies
        ObjectNode depsNode = root.putObject("dependencies");
        deps.forEach((taskNum, depList) -> {
            ArrayNode depArray = depsNode.putArray(String.valueOf(taskNum));
            depList.forEach(depArray::add);
        });

        // Tasks
        ArrayNode tasksArray = root.putArray("tasks");
        for (TaskInfo task : tasks) {
            ObjectNode taskNode = tasksArray.addObject();
            taskNode.put("number", task.number());
            taskNode.put("name", task.name());
            ArrayNode filesArray = taskNode.putArray("files");
            task.files().forEach(filesArray::add);
            writeResourceMap(taskNode.putObject("provides"), task.provides());
            writeResourceMap(taskNode.putObject("consumes"), task.consumes());
            ArrayNode dependsOnArray = taskNode.putArray("dependsOn");
            task.dependsOn().forEach(dependsOnArray::add);
        }

        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"error\":\"JSON serialization failed\"}";
        }
    }

    private static Map<String, List<String>> copyResourceMap(Map<String, List<String>> resourceMap) {
        if (resourceMap == null || resourceMap.isEmpty()) {
            return Map.of();
        }

        Map<String, List<String>> copy = new LinkedHashMap<>();
        resourceMap.forEach((key, value) -> copy.put(key, List.copyOf(value == null ? List.of() : value)));
        return Collections.unmodifiableMap(copy);
    }

    private void writeResourceMap(ObjectNode node, Map<String, List<String>> resourceMap) {
        resourceMap.forEach((category, values) -> {
            ArrayNode array = node.putArray(category);
            values.forEach(array::add);
        });
    }
}
