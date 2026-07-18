package io.github.luigidemasi.camelkit.ship.context;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy;

/** Describes an external document that remains unopened pending later protected consent. */
public record PendingDocumentConsent(
        int sourceOrdinal,
        String requestId,
        Path canonicalPath,
        String purpose,
        CandidatePhysicalIdentity candidateIdentity,
        String subjectDigest) {

    public static final String INITIAL_CONTEXT_PURPOSE = "ship-initial-context-document-read";

    public PendingDocumentConsent {
        if (sourceOrdinal < 0 || sourceOrdinal >= InitialContext.MAX_SOURCES) {
            throw new IllegalArgumentException("Pending document source ordinal is outside context limits");
        }
        if (requestId == null || !requestId.matches("document-request-[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Pending document request ID must be canonical");
        }
        Objects.requireNonNull(canonicalPath, "canonical path");
        String canonicalPathValue = canonicalPath.toString();
        if (!canonicalPath.isAbsolute()
                || !canonicalPath.normalize().equals(canonicalPath)
                || canonicalPathValue.length() > ShipTreePolicy.MAX_PATH_CHARACTERS) {
            throw new IllegalArgumentException("Pending document path must be canonical and absolute");
        }
        ContextModelSupport.strictUtf8Length(
                canonicalPathValue,
                ShipTreePolicy.MAX_PATH_CHARACTERS * 4,
                "pending document path");
        if (!INITIAL_CONTEXT_PURPOSE.equals(purpose)) {
            throw new IllegalArgumentException("Pending document purpose is not initial context");
        }
        Objects.requireNonNull(candidateIdentity, "candidate identity");
        if (!digest(sourceOrdinal, requestId, canonicalPath, purpose, candidateIdentity)
                .equals(subjectDigest)) {
            throw new IllegalArgumentException("Pending document subject digest does not match its metadata");
        }
    }

    static PendingDocumentConsent create(
            int sourceOrdinal, Path canonicalPath, CandidatePhysicalIdentity candidateIdentity) {
        Path canonical = Objects.requireNonNull(canonicalPath, "canonical path").toAbsolutePath().normalize();
        String requestId = "document-request-" + digestFields(
                "camel-kit.ship.document-request.v1",
                Integer.toString(sourceOrdinal),
                canonical.toString(),
                candidateIdentity.stableValue()).substring("sha256:".length());
        return new PendingDocumentConsent(
                sourceOrdinal,
                requestId,
                canonical,
                INITIAL_CONTEXT_PURPOSE,
                candidateIdentity,
                digest(sourceOrdinal, requestId, canonical, INITIAL_CONTEXT_PURPOSE, candidateIdentity));
    }

    private static String digest(
            int sourceOrdinal,
            String requestId,
            Path canonicalPath,
            String purpose,
            CandidatePhysicalIdentity identity) {
        return digestFields(
                "camel-kit.ship.pending-document-consent.v1",
                Integer.toString(sourceOrdinal),
                requestId,
                canonicalPath.toString(),
                purpose,
                identity.stableValue());
    }

    private static String digestFields(String... fields) {
        MessageDigest digest = messageDigest();
        for (String field : fields) {
            byte[] bytes = ContextModelSupport.encodeStrictUtf8(
                    field, InitialContext.MAX_TOTAL_BYTES, "pending document digest field");
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
            digest.update(bytes);
        }
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("The Java runtime does not provide SHA-256", e);
        }
    }

    @Override
    public String toString() {
        return "PendingDocumentConsent[sourceOrdinal=" + sourceOrdinal
               + ", requestId=" + requestId
               + ", purpose=" + purpose
               + ", subjectDigest=" + subjectDigest + ']';
    }

    public record CandidatePhysicalIdentity(
            long device,
            long inode,
            long byteSize,
            long lastModifiedNanos,
            long changeTimeNanos,
            int unixMode,
            String posixPermissions,
            long hardLinkCount) {

        public CandidatePhysicalIdentity {
            if (byteSize < 1 || byteSize > InitialContext.MAX_DOCUMENT_BYTES) {
                throw new IllegalArgumentException("External document candidate size is outside context limits");
            }
            if (posixPermissions == null
                    || !posixPermissions.matches("[r-][w-][x-][r-][w-][x-][r-][w-][x-]")) {
                throw new IllegalArgumentException("External document permissions must be canonical POSIX permissions");
            }
            if ((unixMode & 0170000) != 0100000 || (unixMode & 0777) != permissionBits(posixPermissions)) {
                throw new IllegalArgumentException("External document mode must describe the same regular file");
            }
            if (hardLinkCount != 1) {
                throw new IllegalArgumentException("External document candidate must have exactly one hard link");
            }
        }

        String stableValue() {
            return Long.toUnsignedString(device)
                   + ':' + Long.toUnsignedString(inode)
                   + ':' + byteSize
                   + ':' + lastModifiedNanos
                   + ':' + changeTimeNanos
                   + ':' + Integer.toUnsignedString(unixMode)
                   + ':' + posixPermissions
                   + ':' + hardLinkCount;
        }

        @Override
        public String toString() {
            return "CandidatePhysicalIdentity[redacted]";
        }

        private static int permissionBits(String permissions) {
            int bits = 0;
            int[] masks = {0400, 0200, 0100, 0040, 0020, 0010, 0004, 0002, 0001};
            for (int index = 0; index < masks.length; index++) {
                if (permissions.charAt(index) != '-') {
                    bits |= masks[index];
                }
            }
            return bits;
        }
    }
}
