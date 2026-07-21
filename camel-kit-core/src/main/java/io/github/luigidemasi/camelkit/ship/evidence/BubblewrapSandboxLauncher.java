package io.github.luigidemasi.camelkit.ship.evidence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Linux bubblewrap launcher. There is deliberately no unsandboxed fallback. */
final class BubblewrapSandboxLauncher implements EvidenceSandboxLauncher {

    static final String PROVIDER = "bubblewrap-linux-v1";
    static final String PROFILE_ID = PROVIDER
                                     + ":die-with-parent,new-session,clearenv,drop-all-caps,merged-usr"
                                     + ":ro-system-lib,ro-system-lib64"
                                     + ":unshare-user,pid,ipc,uts,cgroup,net";

    private final Path configuredExecutable;

    BubblewrapSandboxLauncher() {
        this(configuredPath());
    }

    BubblewrapSandboxLauncher(Path configuredExecutable) {
        this.configuredExecutable = configuredExecutable;
    }

    @Override
    public Identity identity() throws IOException {
        Path executable = requireExecutable(configuredExecutable, "bubblewrap");
        requireMergedUsr();
        return new Identity(PROVIDER, executable);
    }

    @Override
    public String profileId() {
        return PROFILE_ID;
    }

    @Override
    public Process launch(Invocation invocation) throws IOException {
        List<String> arguments = arguments(identity().executable(), invocation);
        ProcessBuilder builder = new ProcessBuilder(arguments);
        builder.environment().clear();
        return builder.start();
    }

    static List<String> arguments(Path bubblewrap, Invocation invocation) {
        List<String> arguments = new ArrayList<>();
        arguments.add(bubblewrap.toString());
        arguments.addAll(List.of(
                "--die-with-parent",
                "--new-session",
                "--unshare-user",
                "--unshare-pid",
                "--unshare-ipc",
                "--unshare-uts",
                "--unshare-cgroup",
                "--unshare-net",
                "--disable-userns",
                "--cap-drop", "ALL",
                "--clearenv",
                "--symlink", "usr/lib", "/lib",
                "--symlink", "usr/lib64", "/lib64",
                "--proc", "/proc",
                "--dev", "/dev",
                "--tmpfs", "/run",
                "--dir", "/etc",
                "--dir", "/etc/ssl",
                "--dir", "/etc/ssl/certs",
                "--dir", "/etc/pki",
                "--dir", "/etc/pki/tls",
                "--dir", "/etc/pki/tls/certs",
                "--dir", "/home",
                "--dir", "/opt",
                "--dir", "/opt/camel-kit",
                "--dir", "/opt/camel-kit/bin"));
        Set<String> existing = Set.of("/", "/dev", "/etc", "/home", "/opt", "/opt/camel-kit", "/proc", "/run");
        TreeSet<String> parents = new TreeSet<>(
                Comparator
                        .comparingInt((String value) -> Path.of(value).getNameCount())
                        .thenComparing(value -> value));
        for (Mount mount : invocation.mounts()) {
            Path parent = Path.of(mount.target()).getParent();
            while (parent != null && !existing.contains(parent.toString())) {
                parents.add(parent.toString());
                parent = parent.getParent();
            }
        }
        for (String parent : parents) {
            arguments.add("--dir");
            arguments.add(parent);
        }
        for (Mount mount : invocation.mounts()) {
            arguments.add(mount.access() == Access.READ_ONLY ? "--ro-bind" : "--bind");
            arguments.add(mount.source().toString());
            arguments.add(mount.target());
        }
        invocation.environment().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    arguments.add("--setenv");
                    arguments.add(entry.getKey());
                    arguments.add(entry.getValue());
                });
        arguments.add("--chdir");
        arguments.add(invocation.sandboxWorkingDirectory());
        arguments.add("--");
        arguments.addAll(invocation.arguments());
        return List.copyOf(arguments);
    }

    private static Path configuredPath() {
        String configured = System.getProperty("camel.kit.ship.bwrap");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        if (Files.exists(Path.of("/usr/bin/bwrap"), LinkOption.NOFOLLOW_LINKS)) {
            return Path.of("/usr/bin/bwrap");
        }
        return Path.of("/bin/bwrap");
    }

    private static Path requireExecutable(Path path, String label) throws IOException {
        if (path == null || !path.isAbsolute() || Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || !Files.isExecutable(path)) {
            throw new IOException(label + " executable is unavailable or unsafe: " + path);
        }
        return path.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    private static void requireMergedUsr() throws IOException {
        Map<Path, Path> required = Map.of(
                Path.of("/lib"), Path.of("usr/lib"),
                Path.of("/lib64"), Path.of("usr/lib64"));
        for (Map.Entry<Path, Path> entry : required.entrySet().stream()
                .sorted(Comparator.comparing(item -> item.getKey().toString())).toList()) {
            if (!Files.isSymbolicLink(entry.getKey())
                    || !entry.getValue().equals(Files.readSymbolicLink(entry.getKey()))) {
                throw new IOException("Ship evidence requires a merged-/usr Linux host: " + entry.getKey());
            }
        }
    }
}
