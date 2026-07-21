package io.github.luigidemasi.camelkit.ship.evidence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Launch boundary for controller-owned evidence commands. */
interface EvidenceSandboxLauncher {

    Identity identity() throws IOException;

    String profileId();

    Process launch(Invocation invocation) throws IOException;

    record Identity(String provider, Path executable) {
    }

    record Mount(Path source, String target, Access access) {

        public Mount {
            source = source.toAbsolutePath().normalize();
            if (target == null || !target.startsWith("/") || target.contains("..")) {
                throw new IllegalArgumentException("Sandbox mount target must be absolute and normalized");
            }
        }

        boolean exposes(Path hostPath) {
            Path path = hostPath.toAbsolutePath().normalize();
            return path.equals(source) || path.startsWith(source);
        }
    }

    enum Access {
        READ_ONLY,
        READ_WRITE
    }

    record Invocation(
            Path candidate,
            Path workingDirectory,
            String sandboxWorkingDirectory,
            Path privateHome,
            Path privateTemporaryDirectory,
            Path toolchainExecutable,
            List<Mount> mounts,
            List<String> arguments,
            Map<String, String> environment) {

        public Invocation {
            candidate = candidate.toAbsolutePath().normalize();
            workingDirectory = workingDirectory.toAbsolutePath().normalize();
            privateHome = privateHome.toAbsolutePath().normalize();
            privateTemporaryDirectory = privateTemporaryDirectory.toAbsolutePath().normalize();
            toolchainExecutable = toolchainExecutable.toAbsolutePath().normalize();
            mounts = List.copyOf(mounts);
            arguments = List.copyOf(arguments);
            environment = Map.copyOf(environment);
        }

        boolean exposes(Path hostPath) {
            return mounts.stream().anyMatch(mount -> mount.exposes(hostPath));
        }
    }
}
