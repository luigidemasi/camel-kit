package io.github.luigidemasi.camelkit.command;

import java.io.IOException;
import java.nio.file.Path;

import io.github.luigidemasi.camelkit.CamelKitMain;
import io.github.luigidemasi.camelkit.service.DoctorFinding;
import io.github.luigidemasi.camelkit.service.DoctorRequest;
import io.github.luigidemasi.camelkit.service.DoctorResult;
import io.github.luigidemasi.camelkit.service.DoctorService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Validate a generated Camel-Kit workspace.
 */
@Command(name = "doctor", description = "Validate a generated Camel-Kit workspace")
public class DoctorCommand extends CamelKitCommand {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DoctorService doctorService;

    @Option(names = {"--project-dir"}, description = "Workspace directory to validate", defaultValue = ".")
    Path projectDir;

    @Option(names = {"--json"}, description = "Print machine-readable JSON output")
    boolean json;

    public DoctorCommand(CamelKitMain main) {
        this(main, new DoctorService());
    }

    DoctorCommand(CamelKitMain main, DoctorService doctorService) {
        super(main);
        this.doctorService = doctorService;
    }

    @Override
    public Integer doCall() throws Exception {
        DoctorResult result = doctorService.inspect(new DoctorRequest(projectDir));
        if (json) {
            printer().println(toJson(result));
        } else {
            printText(result);
        }
        return result.hasFailures() ? 1 : 0;
    }

    private void printText(DoctorResult result) {
        printer().println(bold("Camel-Kit Doctor"));
        printer().println("Workspace: " + result.projectDir());
        printer().println();
        for (DoctorFinding finding : result.findings()) {
            printer().println(statusLabel(finding.status()) + " " + finding.category() + " - " + finding.message());
            if (finding.path() != null) {
                printer().println("  Path: " + finding.path());
            }
            printer().println("  Fix:  " + finding.remediation());
        }
        printer().println();
        printer().println(summary(result));
    }

    private String toJson(DoctorResult result) throws IOException {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("projectDir", result.projectDir().toString());
        node.put("status", result.hasFailures() ? "FAIL" : "PASS");
        ObjectNode summary = node.putObject("summary");
        summary.put("pass", result.count(DoctorFinding.Status.PASS));
        summary.put("warn", result.count(DoctorFinding.Status.WARN));
        summary.put("fail", result.count(DoctorFinding.Status.FAIL));
        ArrayNode array = node.putArray("findings");
        for (DoctorFinding finding : result.findings()) {
            ObjectNode findingNode = array.addObject();
            findingNode.put("status", finding.status().name());
            findingNode.put("category", finding.category());
            if (finding.path() == null) {
                findingNode.putNull("path");
            } else {
                findingNode.put("path", finding.path());
            }
            findingNode.put("message", finding.message());
            findingNode.put("remediation", finding.remediation());
        }
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    }

    private String summary(DoctorResult result) {
        return "Summary: " + result.count(DoctorFinding.Status.PASS)
               + " PASS, " + result.count(DoctorFinding.Status.WARN)
               + " WARN, " + result.count(DoctorFinding.Status.FAIL) + " FAIL";
    }

    private String statusLabel(DoctorFinding.Status status) {
        return switch (status) {
            case PASS -> green("PASS");
            case WARN -> yellow("WARN");
            case FAIL -> red("FAIL");
        };
    }
}
