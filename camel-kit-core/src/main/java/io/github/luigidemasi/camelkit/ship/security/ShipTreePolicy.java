package io.github.luigidemasi.camelkit.ship.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** One versioned classification and quota policy for every Ship project tree boundary. */
public final class ShipTreePolicy {

    public static final int SCHEMA_VERSION = 5;
    public static final int DEFAULT_MAX_FILE_COUNT = 20_000;
    public static final long DEFAULT_MAX_FILE_BYTES = 64L * 1024 * 1024;
    public static final long DEFAULT_MAX_AGGREGATE_BYTES = 1024L * 1024 * 1024;
    public static final int DEFAULT_MAX_DEPTH = 128;
    public static final int MAX_PATH_CHARACTERS = 4_096;
    public static final int MAX_COMPONENT_UTF8_BYTES = 255;

    private static final List<String> RULES = List.of(
            "classification:ascii-case-insensitive",
            "denied:**/.git/**",
            "protected:**/.idea/**",
            "protected:**/.vscode/**",
            "denied:**/.camel-kit/pipeline.json/**,**/.camel-kit/ship-projection.json/**",
            "volatile:**/.camel-kit/.cache/**,**/.camel-kit/mcp/**",
            "denied:credential directories .ssh,.gnupg,.aws,.azure,.kube,.docker",
            "denied:credential files .env,.env.* except final .env.example,.npmrc,.pypirc,.netrc",
            "denied:**/.m2/settings.xml/**,**/.m2/settings-security.xml/**,**/.gradle/gradle.properties/**",
            "denied:**/.config/gcloud/**,**/.config/gh/hosts.yml/**",
            "volatile:**/target/**",
            "material:everything-else",
            "quota:entry-count-includes-files-and-directories",
            "quota:max-depth=policy-value",
            "reject:symlink,hardlink,fifo,socket,device,unknown,duplicate-file-key",
            "reject:root-lineage-containing-protected,denied,volatile-directory-names",
            "reject:absolute,dot-segment,control,format,line-separator,paragraph-separator,"
                                                                                        + "invalid-surrogate,empty,oversize-path-component");

    private static final ShipTreePolicy CURRENT = new ShipTreePolicy(
            DEFAULT_MAX_FILE_COUNT,
            DEFAULT_MAX_FILE_BYTES,
            DEFAULT_MAX_AGGREGATE_BYTES,
            DEFAULT_MAX_DEPTH);

    private final int maxFileCount;
    private final long maxFileBytes;
    private final long maxAggregateBytes;
    private final int maxDepth;
    private final String digest;

    private ShipTreePolicy(
                           int maxFileCount, long maxFileBytes, long maxAggregateBytes, int maxDepth) {
        if (maxFileCount < 1 || maxFileBytes < 1 || maxAggregateBytes < maxFileBytes
                || maxDepth < 1) {
            throw new IllegalArgumentException("Invalid Ship tree quotas");
        }
        this.maxFileCount = maxFileCount;
        this.maxFileBytes = maxFileBytes;
        this.maxAggregateBytes = maxAggregateBytes;
        this.maxDepth = maxDepth;
        this.digest = computeDigest();
    }

    public static ShipTreePolicy current() {
        return CURRENT;
    }

    static ShipTreePolicy testing(int maxFileCount, long maxFileBytes, long maxAggregateBytes) {
        return new ShipTreePolicy(
                maxFileCount, maxFileBytes, maxAggregateBytes, DEFAULT_MAX_DEPTH);
    }

    static ShipTreePolicy testing(
            int maxFileCount, long maxFileBytes, long maxAggregateBytes, int maxDepth) {
        return new ShipTreePolicy(maxFileCount, maxFileBytes, maxAggregateBytes, maxDepth);
    }

    public int schemaVersion() {
        return SCHEMA_VERSION;
    }

    public int maxFileCount() {
        return maxFileCount;
    }

    public long maxFileBytes() {
        return maxFileBytes;
    }

    public long maxAggregateBytes() {
        return maxAggregateBytes;
    }

    public int maxDepth() {
        return maxDepth;
    }

    public String digest() {
        return digest;
    }

    public Classification classify(String relativePath) {
        String path = requireCanonicalRelativePath(relativePath);
        String folded = path.toLowerCase(Locale.ROOT);
        if (subtreeAtAnyDepth(folded, ".git")
                || subtreeAtAnyDepth(folded, ".camel-kit/pipeline.json")
                || subtreeAtAnyDepth(folded, ".camel-kit/ship-projection.json")
                || isCredentialPath(folded)) {
            return Classification.DENIED;
        }
        if (subtreeAtAnyDepth(folded, ".idea")
                || subtreeAtAnyDepth(folded, ".vscode")) {
            return Classification.PROTECTED;
        }
        if (subtreeAtAnyDepth(folded, ".camel-kit/.cache")
                || subtreeAtAnyDepth(folded, ".camel-kit/mcp")) {
            return Classification.VOLATILE;
        }
        for (String component : folded.split("/")) {
            if ("target".equals(component)) {
                return Classification.VOLATILE;
            }
        }
        return Classification.MATERIAL;
    }

    public boolean isAllowedAbsolutePath(Path absoluteRoot) {
        if (absoluteRoot == null || !absoluteRoot.isAbsolute() || absoluteRoot.getNameCount() == 0) {
            return false;
        }
        Path normalized = absoluteRoot.normalize();
        if (!normalized.equals(absoluteRoot)) {
            return false;
        }
        StringBuilder lineageBuilder = new StringBuilder();
        for (Path component : normalized) {
            if (!lineageBuilder.isEmpty()) {
                lineageBuilder.append('/');
            }
            lineageBuilder.append(component);
        }
        String lineage;
        try {
            lineage = requireCanonicalRelativePath(lineageBuilder.toString()).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String first = normalized.getName(0).toString().toLowerCase(Locale.ROOT);
        return !List.of("proc", "sys", "dev", "run").contains(first)
                && !containsComponent(lineage, ".camel-kit")
                && !containsComponent(lineage, ".git")
                && !containsComponent(lineage, "target")
                && !subtreeAtAnyDepth(lineage, ".idea")
                && !subtreeAtAnyDepth(lineage, ".vscode")
                && !isCredentialPath(lineage);
    }

    public static String requireCanonicalRelativePath(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_PATH_CHARACTERS
                || value.startsWith("/") || value.endsWith("/")
                || value.indexOf('\\') >= 0 || value.indexOf('\0') >= 0
                || isWindowsAbsolute(value)) {
            throw new IllegalArgumentException("Invalid Ship tree path: " + value);
        }
        String[] components = value.split("/", -1);
        for (String component : components) {
            if (component.isEmpty() || ".".equals(component) || "..".equals(component)
                    || component.getBytes(StandardCharsets.UTF_8).length > MAX_COMPONENT_UTF8_BYTES
                    || containsUnsafeDisplayCharacter(component)) {
                throw new IllegalArgumentException("Invalid Ship tree path: " + value);
            }
        }
        return value;
    }

    private static boolean isCredentialPath(String path) {
        String[] components = path.split("/");
        for (String component : components) {
            if (component.equals(".ssh") || component.equals(".gnupg")
                    || component.equals(".aws") || component.equals(".azure")
                    || component.equals(".kube") || component.equals(".docker")
                    || component.equals(".npmrc") || component.equals(".pypirc")
                    || component.equals(".netrc") || component.equals(".env")
                    || component.startsWith(".env.") && !isFinalEnvExample(path, component)) {
                return true;
            }
        }
        return subtreeAtAnyDepth(path, ".m2/settings.xml")
                || subtreeAtAnyDepth(path, ".m2/settings-security.xml")
                || subtreeAtAnyDepth(path, ".gradle/gradle.properties")
                || subtreeAtAnyDepth(path, ".config/gcloud")
                || subtreeAtAnyDepth(path, ".config/gh/hosts.yml");
    }

    private static boolean subtree(String path, String root) {
        return path.equals(root) || path.startsWith(root + "/");
    }

    private static boolean subtreeAtAnyDepth(String path, String root) {
        return subtree(path, root) || path.contains("/" + root + "/") || path.endsWith("/" + root);
    }

    private static boolean exactAtAnyDepth(String path, String exact) {
        return path.equals(exact) || path.endsWith("/" + exact);
    }

    private static boolean isFinalEnvExample(String path, String component) {
        return component.equals(".env.example") && exactAtAnyDepth(path, component);
    }

    private static boolean containsComponent(String path, String component) {
        return path.equals(component)
                || path.startsWith(component + "/")
                || path.endsWith("/" + component)
                || path.contains("/" + component + "/");
    }

    private static boolean isWindowsAbsolute(String value) {
        return value.length() >= 3
                && Character.isLetter(value.charAt(0))
                && value.charAt(1) == ':'
                && value.charAt(2) == '/';
    }

    private static boolean containsUnsafeDisplayCharacter(String value) {
        for (int offset = 0; offset < value.length();) {
            char current = value.charAt(offset);
            if (Character.isHighSurrogate(current)) {
                if (offset + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(offset + 1))) {
                    return true;
                }
            } else if (Character.isLowSurrogate(current)) {
                return true;
            }
            int codePoint = value.codePointAt(offset);
            int type = Character.getType(codePoint);
            if (Character.isISOControl(codePoint)
                    || type == Character.FORMAT
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private String computeDigest() {
        MessageDigest hash;
        try {
            hash = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
        update(hash, "camel-kit.ship.tree-policy.v5");
        update(hash, Integer.toString(SCHEMA_VERSION));
        update(hash, Integer.toString(maxFileCount));
        update(hash, Long.toString(maxFileBytes));
        update(hash, Long.toString(maxAggregateBytes));
        update(hash, Integer.toString(maxDepth));
        update(hash, Integer.toString(MAX_PATH_CHARACTERS));
        update(hash, Integer.toString(MAX_COMPONENT_UTF8_BYTES));
        RULES.forEach(rule -> update(hash, rule));
        return "sha256:" + HexFormat.of().formatHex(hash.digest());
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    public enum Classification {
        MATERIAL,
        PROTECTED,
        VOLATILE,
        DENIED
    }
}
