package io.github.luigidemasi.camelkit.command.plan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

import io.github.luigidemasi.camelkit.plan.PlanAnalyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "analyze", description = "Analyze plan for parallel execution waves")
public class PlanAnalyzeCommand implements Callable<Integer> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Parameters(index = "0", description = "Path to plan markdown",
                defaultValue = "docs/implementation-plan.md")
    Path planFile;

    @Override
    public Integer call() {
        try {
            String markdown = Files.readString(planFile);
            PlanAnalyzer analyzer = new PlanAnalyzer();
            var tasks = analyzer.parseTasks(markdown);
            System.out.println(analyzer.toJson(tasks));
            return 0;
        } catch (Exception e) {
            try {
                System.err.println(MAPPER.writeValueAsString(Map.of(
                        "error", "plan-analysis-failed",
                        "message", String.valueOf(e.getMessage()))));
            } catch (Exception ignored) {
                System.err.println("{\"error\":\"plan-analysis-failed\"}");
            }
            return 1;
        }
    }
}
