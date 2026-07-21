package io.github.luigidemasi.camelkit.ship.security;

import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.luigidemasi.camelkit.ship.ShipDigest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledOnOs(OS.LINUX)
class StagedArtifactSourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void declaredSizeIsPolicyBoundedBeforeAnyReadArithmetic() throws Exception {
        Path output = Files.createDirectory(temporaryDirectory.resolve("output"));
        Files.writeString(output.resolve("artifact.txt"), "content");

        try (StagedArtifactSource.Session source = StagedArtifactSource.open(output)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> source.copyTo("artifact.txt", Long.MAX_VALUE, new CountingChannel()));
        }
    }

    @Test
    void firstByteBeyondDeclarationIsRejectedBeforeItReachesQuarantine() throws Exception {
        Path output = Files.createDirectory(temporaryDirectory.resolve("output"));
        Files.write(output.resolve("artifact.bin"), new byte[128 * 1024]);
        CountingChannel target = new CountingChannel();

        try (StagedArtifactSource.Session source = StagedArtifactSource.open(output)) {
            assertThrows(
                    java.io.IOException.class,
                    () -> source.copyTo("artifact.bin", 0, target));
        }

        assertEquals(0, target.bytes);
    }

    @Test
    void hashesTheExactBytesWrittenToTheCallerChannel() throws Exception {
        Path output = Files.createDirectory(temporaryDirectory.resolve("output"));
        byte[] content = "same-opened-stream".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(output.resolve("artifact.bin"), content);
        CountingChannel target = new CountingChannel();

        StagedArtifactSource.CopyResult result;
        try (StagedArtifactSource.Session source = StagedArtifactSource.open(output)) {
            result = source.copyTo("artifact.bin", content.length, target);
        }

        assertEquals(content.length, target.bytes);
        assertEquals(content.length, result.size());
        assertEquals(ShipDigest.sha256(content), result.digest());
    }

    private static final class CountingChannel implements WritableByteChannel {

        private int bytes;
        private boolean open = true;

        @Override
        public int write(ByteBuffer source) {
            int count = source.remaining();
            source.position(source.limit());
            bytes += count;
            return count;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }
}
