package io.github.luigidemasi.camelkit.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses implementation plan markdown to extract tasks and their file lists,
 * builds a dependency graph based on shared files, and computes parallel execution waves.
 */
public class PlanAnalyzer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Matches "### Task N: Name" or "### Task N-M: Name"
    private static final Pattern TASK_HEADER = Pattern.compile(
            "^###\\s+Task\\s+(\\d+(?:-\\d+)?):\\s+(.+)$", Pattern.MULTILINE);

    // Matches "- Create/Modify/Test/Delete: `path`"
    private static final Pattern FILE_LINE = Pattern.compile(
            "^-\\s+(?:Create|Modify|Test|Delete):\\s+`([^`]+)`$", Pattern.MULTILINE);

    /**
     * A task extracted from the plan, with its number, name, and list of files.
     */
    public record TaskInfo(int number, String name, List<String> files) {}

    /**
     * A wave of tasks that can be executed in parallel.
     */
    public record Wave(int waveNumber, List<Integer> taskNumbers) {}

    /**
     * Parses task headers and their file lists from plan markdown.
     */
    public List<TaskInfo> parseTasks(String planMarkdown) {
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
     * Finds dependencies between tasks based on shared files.
     * A later task depends on an earlier task if they share any file.
     *
     * @return map from task number to list of dependency task numbers
     */
    public Map<Integer, List<Integer>> findDependencies(List<TaskInfo> tasks) {
        Map<Integer, List<Integer>> deps = new LinkedHashMap<>();

        for (int i = 0; i < tasks.size(); i++) {
            TaskInfo current = tasks.get(i);
            List<Integer> currentDeps = new ArrayList<>();

            for (int j = 0; j < i; j++) {
                TaskInfo earlier = tasks.get(j);
                if (sharesAnyFile(current, earlier)) {
                    currentDeps.add(earlier.number());
                }
            }

            if (!currentDeps.isEmpty()) {
                deps.put(current.number(), currentDeps);
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

    /**
     * Computes parallel execution waves using topological sort.
     * Wave 1: tasks with no dependencies.
     * Wave N: tasks whose dependencies are all in waves 1..N-1.
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
                if (assigned.contains(taskNum)) continue;

                List<Integer> taskDeps = deps.getOrDefault(taskNum, List.of());
                if (assigned.containsAll(taskDeps)) {
                    currentWave.add(taskNum);
                }
            }

            if (currentWave.isEmpty()) {
                // Circular dependency fallback: force remaining into this wave
                for (int taskNum : allTaskNumbers) {
                    if (!assigned.contains(taskNum)) {
                        currentWave.add(taskNum);
                    }
                }
            }

            waves.add(new Wave(waveNumber, currentWave));
            assigned.addAll(currentWave);
        }

        return waves;
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
            wave.taskNumbers().forEach(n ->
                    namesArr.add("Task " + n + ": " + namesByNumber.getOrDefault(n, "Unknown")));
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
        }

        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"error\":\"JSON serialization failed\"}";
        }
    }
}
