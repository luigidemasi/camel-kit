package io.github.luigidemasi.camelkit.ship.evidence;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Minimal JDK-only bootstrap for a controller-owned nested-JAR evidence payload. */
public final class ShipJvmPayloadBootstrap {

    private ShipJvmPayloadBootstrap() {
    }

    public static void main(String[] arguments) throws Throwable {
        if (arguments.length == 0 || !arguments[0].startsWith("--launcher=")) {
            throw new IllegalArgumentException("JVM payload bootstrap requires --launcher=<class> first");
        }
        String launcherClass = arguments[0].substring("--launcher=".length());
        String[] launcherArguments = Arrays.copyOfRange(arguments, 1, arguments.length);
        Path archive = archive();
        Path extraction = Files.createTempDirectory("camel-kit-ship-payload-");
        try {
            List<URL> urls = new ArrayList<>();
            try (ZipFile zip = new ZipFile(archive.toFile())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (entry.isDirectory() || !name.startsWith("lib/") || !name.endsWith(".jar")) {
                        continue;
                    }
                    Path target = extraction.resolve(Path.of(name).getFileName().toString());
                    try (InputStream input = zip.getInputStream(entry)) {
                        Files.copy(input, target);
                    }
                    urls.add(target.toUri().toURL());
                }
            }
            try (URLClassLoader loader = new URLClassLoader(
                    urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
                Class<?> launcher = Class.forName(launcherClass, true, loader);
                Method main = launcher.getMethod("main", String[].class);
                Thread thread = Thread.currentThread();
                ClassLoader previous = thread.getContextClassLoader();
                thread.setContextClassLoader(loader);
                try {
                    main.invoke(null, (Object) launcherArguments);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                } finally {
                    thread.setContextClassLoader(previous);
                }
            }
        } finally {
            deleteTree(extraction);
        }
    }

    private static Path archive() throws Exception {
        Path path = Path.of(ShipJvmPayloadBootstrap.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI())
                .toAbsolutePath().normalize();
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("JVM payload bootstrap must run from one regular archive");
        }
        return path.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("Private JVM payload extraction contained a symbolic link");
                }
                Files.delete(path);
            }
        }
    }
}
