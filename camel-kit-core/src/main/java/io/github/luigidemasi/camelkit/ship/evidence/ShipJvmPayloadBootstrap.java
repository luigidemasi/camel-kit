package io.github.luigidemasi.camelkit.ship.evidence;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Minimal JDK-only bootstrap for a content-locked nested-JAR evidence payload. */
public final class ShipJvmPayloadBootstrap {

    private static final int MAX_ARCHIVE_ENTRIES = 1_024;
    private static final long MAX_ENTRY_BYTES = 128L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 768L * 1024 * 1024;

    private ShipJvmPayloadBootstrap() {
    }

    public static void main(String[] arguments) throws Throwable {
        Path archive = archive();
        JvmPayloadLock lock;
        List<PayloadEntry> classpath;
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            lock = JvmPayloadLock.parse(read(zip, JvmPayloadLock.LOCK_PATH, 1024 * 1024));
            requireJdk(lock);
            classpath = verify(zip, lock);
        }

        Path extraction = Files.createTempDirectory("camel-kit-ship-payload-");
        try {
            List<URL> urls = extract(archive, extraction, classpath);
            try (URLClassLoader loader = new URLClassLoader(
                    urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
                Class<?> launcher = Class.forName(lock.launcherClass(), true, loader);
                if (launcher.getClassLoader() != loader) {
                    throw new SecurityException("Evidence launcher escaped the isolated payload classloader");
                }
                Method main = launcher.getMethod("main", String[].class);
                Thread thread = Thread.currentThread();
                ClassLoader previous = thread.getContextClassLoader();
                thread.setContextClassLoader(loader);
                try {
                    main.invoke(null, (Object) arguments);
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

    private static void requireJdk(JvmPayloadLock lock) {
        String actual = System.getenv("CAMEL_KIT_JDK_DIGEST");
        if (!lock.jdkDigest().equals(actual)) {
            throw new SecurityException("JVM payload JDK identity differs from its content lock");
        }
    }

    private static List<PayloadEntry> verify(ZipFile zip, JvmPayloadLock lock) throws IOException {
        Set<String> expected = new HashSet<>();
        expected.add(JvmPayloadLock.LOCK_PATH);
        lock.entries().forEach(entry -> expected.add(entry.path()));
        Set<String> observed = new HashSet<>();
        long total = 0;
        int count = 0;
        var enumeration = zip.entries();
        while (enumeration.hasMoreElements()) {
            ZipEntry entry = enumeration.nextElement();
            if (++count > MAX_ARCHIVE_ENTRIES || entry.isDirectory() || !observed.add(entry.getName())
                    || !expected.contains(entry.getName()) || entry.getSize() <= 0
                    || entry.getSize() > MAX_ENTRY_BYTES) {
                throw new IOException("JVM payload archive contains an unsafe entry: " + entry.getName());
            }
            total = Math.addExact(total, entry.getSize());
            if (total > MAX_TOTAL_BYTES) {
                throw new IOException("JVM payload archive exceeds the controller size limit");
            }
        }
        if (!observed.equals(expected)) {
            throw new IOException("JVM payload archive entries differ from its content lock");
        }

        List<PayloadEntry> result = new ArrayList<>();
        for (JvmPayloadLock.Entry entry : lock.entries()) {
            byte[] bytes = read(zip, entry.path(), Math.toIntExact(Math.min(entry.size(), Integer.MAX_VALUE)));
            if (bytes.length != entry.size() || !entry.digest().equals(digest(bytes))) {
                throw new IOException("JVM payload entry differs from its content lock: " + entry.path());
            }
            if ("launcher".equals(entry.kind()) || "maven".equals(entry.kind())) {
                result.add(new PayloadEntry(entry, bytes));
            }
        }
        result.sort(Comparator.comparing(payload -> payload.entry().path()));
        return List.copyOf(result);
    }

    private static byte[] read(ZipFile zip, String name, int maximum) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null || entry.isDirectory() || entry.getSize() <= 0 || entry.getSize() > maximum) {
            throw new IOException("JVM payload archive lacks a bounded entry: " + name);
        }
        try (InputStream input = zip.getInputStream(entry)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream((int) entry.getSize());
            byte[] buffer = new byte[16_384];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total = Math.addExact(total, count);
                if (total > maximum) {
                    throw new IOException("JVM payload entry exceeds its bound: " + name);
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static List<URL> extract(Path archive, Path root, List<PayloadEntry> entries) throws IOException {
        List<URL> result = new ArrayList<>();
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            for (PayloadEntry payload : entries) {
                Path target = root.resolve(Path.of(payload.entry().path()).getFileName().toString()).normalize();
                if (!target.getParent().equals(root)) {
                    throw new IOException("JVM payload extraction path escaped its private directory");
                }
                Files.write(target, payload.bytes(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                if (!payload.entry().digest().equals(digest(Files.readAllBytes(target)))) {
                    throw new IOException("Extracted JVM payload entry failed digest verification");
                }
                result.add(target.toUri().toURL());
            }
        }
        return List.copyOf(result);
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

    private static String digest(byte[] bytes) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private record PayloadEntry(JvmPayloadLock.Entry entry, byte[] bytes) {

        private PayloadEntry {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
