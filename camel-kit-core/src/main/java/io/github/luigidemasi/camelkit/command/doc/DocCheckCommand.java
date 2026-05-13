package io.github.luigidemasi.camelkit.command.doc;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import io.github.luigidemasi.camelkit.doc.FrontmatterHandler;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "check", description = "Check document staleness status")
public class DocCheckCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Path to the document to check")
    Path file;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        if (!Files.exists(file)) {
            err.println("Error: file not found: " + file);
            err.flush();
            return 1;
        }

        try {
            String content = Files.readString(file);
            String json = FrontmatterHandler.toCheckJson(file.toString(), content);
            out.println(json);
            out.flush();
            return 0;
        } catch (Exception e) {
            err.println("Error reading file: " + e.getMessage());
            err.flush();
            return 1;
        }
    }
}
