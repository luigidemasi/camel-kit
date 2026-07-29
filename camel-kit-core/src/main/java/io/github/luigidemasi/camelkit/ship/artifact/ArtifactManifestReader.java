package io.github.luigidemasi.camelkit.ship.artifact;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Draft202012;
import com.networknt.schema.resource.SchemaLoader;

/** Strictly reads a controller-owned artifact manifest from a candidate workspace. */
public final class ArtifactManifestReader {

    private static final int MAX_MANIFEST_BYTES = 16 * 1024 * 1024;
    private static final String SCHEMA_ID
            = "https://github.com/luigidemasi/camel-kit/schemas/ship/v1/artifact-manifest.schema.json";
    private static final String SCHEMA_RESOURCE = "ship/schema/artifact-manifest.schema.json";
    private static final ObjectMapper JSON = new ObjectMapper(
            JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder()
                            .maxNestingDepth(100)
                            .maxStringLength(MAX_MANIFEST_BYTES)
                            .build())
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);

    private ArtifactManifestReader() {
    }

    public static Document read(Path candidateRoot, Path manifestPath)
            throws IOException {
        ManifestFile file = manifestFile(candidateRoot, manifestPath);
        byte[] encoded = ProjectEvidenceFiles.readMaterial(
                file.root(), file.relativePath(), MAX_MANIFEST_BYTES);
        String text = decode(encoded);

        JsonNode document;
        try {
            document = JSON.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IOException("Artifact manifest is not strict JSON");
        }
        if (document == null) {
            throw new IOException("Artifact manifest is not strict JSON");
        }

        Schema schema = schema();
        try {
            if (!schema.validate(document).isEmpty()) {
                throw new IOException("Artifact manifest does not match its schema");
            }
        } catch (RuntimeException e) {
            throw new IOException("Artifact manifest schema validation failed");
        }

        try {
            return new Document(
                    JSON.treeToValue(document, ArtifactManifest.class),
                    ShipDigest.sha256(encoded));
        } catch (JsonProcessingException e) {
            throw new IOException("Artifact manifest cannot be decoded");
        }
    }

    private static ManifestFile manifestFile(
            Path suppliedRoot, Path suppliedManifest)
            throws IOException {
        if (suppliedRoot == null) {
            throw new IOException("Artifact candidate root is required");
        }
        if (suppliedManifest == null) {
            throw new IOException("Artifact manifest path is required");
        }

        Path normalizedRoot = suppliedRoot.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalizedRoot)
                || !Files.isDirectory(normalizedRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Artifact candidate root must be a real directory");
        }
        Path root = normalizedRoot.toRealPath();
        if (!root.equals(normalizedRoot)) {
            throw new IOException(
                    "Artifact candidate root must not traverse symbolic links");
        }
        Path requested = suppliedManifest.isAbsolute()
                ? suppliedManifest.toAbsolutePath().normalize()
                : root.resolve(suppliedManifest).normalize();
        if (!requested.startsWith(root) || requested.equals(root)) {
            throw new IOException("Artifact manifest must be a contained real regular file");
        }
        String relative = root.relativize(requested)
                .toString()
                .replace(File.separatorChar, '/');
        return new ManifestFile(root, relative);
    }

    private static String decode(byte[] encoded) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IOException("Artifact manifest is not valid UTF-8");
        }
    }

    private static Schema schema() throws IOException {
        String source;
        try (InputStream input = ArtifactManifestReader.class.getClassLoader()
                .getResourceAsStream(SCHEMA_RESOURCE)) {
            if (input == null) {
                throw new IOException("Artifact manifest schema is unavailable");
            }
            byte[] encoded = input.readNBytes(MAX_MANIFEST_BYTES + 1);
            if (encoded.length == 0 || encoded.length > MAX_MANIFEST_BYTES) {
                throw new IOException("Artifact manifest schema has an invalid size");
            }
            source = decode(encoded);
        }

        try {
            SchemaLoader loader = SchemaLoader.builder()
                    .resourceLoaders(loaders -> loaders.resources(Map.of(SCHEMA_ID, source)))
                    .allow(iri -> SCHEMA_ID.equals(iri.toString()))
                    .build();
            Schema schema = SchemaRegistry.withDialect(
                    Draft202012.getInstance(), builder -> builder.schemaLoader(loader))
                    .getSchema(SchemaLocation.of(SCHEMA_ID));
            schema.initializeValidators();
            return schema;
        } catch (RuntimeException e) {
            throw new IOException("Artifact manifest schema is unavailable");
        }
    }

    /** The typed manifest and digest of the exact bytes parsed for acceptance. */
    public record Document(ArtifactManifest manifest, String digest) {

        public Document {
            Objects.requireNonNull(manifest, "artifact manifest");
            if (!ShipDigest.isSha256(digest)) {
                throw new IllegalArgumentException(
                        "Artifact manifest digest must be SHA-256");
            }
        }
    }

    private record ManifestFile(Path root, String relativePath) {
    }
}
