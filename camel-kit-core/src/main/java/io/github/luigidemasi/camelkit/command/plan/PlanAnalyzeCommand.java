package io.github.luigidemasi.camelkit.command.plan;

import java.nio.file.Files;
import java.nio.file.Path;

import io.github.luigidemasi.camelkit.plan.PlanAnalyzer;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "analyze", description = "Analyze plan for parallel execution waves")
public class PlanAnalyzeCommand implements Runnable {

    @Parameters(index = "0", description = "Path to plan markdown",
                defaultValue = "docs/implementation-plan.md")
    Path planFile;

    @Override
    public void run() {
        try {
            String markdown = Files.readString(planFile);
            PlanAnalyzer analyzer = new PlanAnalyzer();
            var tasks = analyzer.parseTasks(markdown);
            System.out.println(analyzer.toJson(tasks));
        } catch (Exception e) {
            System.err.println("Error analyzing plan: " + e.getMessage());
        }
    }
}
