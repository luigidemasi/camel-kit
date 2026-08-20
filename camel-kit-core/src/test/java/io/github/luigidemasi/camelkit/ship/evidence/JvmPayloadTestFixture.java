package io.github.luigidemasi.camelkit.ship.evidence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipOutputStream;

/** Builds a content-valid, dependency-empty test fixture without consulting a repository. */
public final class JvmPayloadTestFixture {

    private JvmPayloadTestFixture() {
    }

    public static JvmPayloadArchive.Identity create(
            Path directory, JvmPayloadRequest request)
            throws IOException {
        Files.createDirectories(directory);
        List<JvmPayloadArchive.ArtifactFile> artifacts = new ArrayList<>();
        int index = 0;
        for (String root : request.roots()) {
            String[] parts = root.split(":", -1);
            Path jar = directory.resolve("root-" + index++ + ".jar");
            try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(jar))) {
                // A valid empty JAR is enough: production verification validates real Central bytes.
            }
            artifacts.add(new JvmPayloadArchive.ArtifactFile(
                    parts[0] + ':' + parts[1] + ":jar:" + parts[2], parts[1], parts[2], jar));
        }
        return JvmPayloadArchive.write(
                directory.resolve(JvmPayloadArchive.ARCHIVE_NAME), request, artifacts);
    }
}
