package io.github.luigidemasi.camelkit.command.doc;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.Callable;

import io.github.luigidemasi.camelkit.doc.FrontmatterHandler;
import io.github.luigidemasi.camelkit.doc.GeneratedInfo;
import io.github.luigidemasi.camelkit.doc.StalenessInfo;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "init", description = "Initialize document with frontmatter metadata")
public class DocInitCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Path to the document to initialize")
    Path file;

    @Option(names = "--by", required = true, description = "Skill that generated this artifact")
    String by;

    @Option(names = "--from", required = true, description = "Source artifact this was generated from")
    String from;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        PrintWriter err = spec.commandLine().getErr();

        if (!Files.exists(file)) {
            err.println("Error: file not found: " + file);
            err.flush();
            return 1;
        }

        try {
            String content = Files.readString(file);

            if (FrontmatterHandler.hasFrontmatter(content)) {
                err.println("Already initialized: " + file);
                err.flush();
                return 0;
            }

            GeneratedInfo generated = new GeneratedInfo(Instant.now().toString(), by, from);
            String result = FrontmatterHandler.writeFrontmatter(StalenessInfo.fresh(), generated, content);
            Files.writeString(file, result);

            err.println("Initialized: " + file);
            err.flush();
            return 0;
        } catch (Exception e) {
            err.println("Error: " + e.getMessage());
            err.flush();
            return 1;
        }
    }
}
