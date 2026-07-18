package io.github.luigidemasi.camelkit.ship.context;

import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.context.InitialContext.SourceKind;
import io.github.luigidemasi.camelkit.ship.context.InitialContextRequest.DocumentReference;
import io.github.luigidemasi.camelkit.ship.context.InitialContextRequest.UserText;
import io.github.luigidemasi.camelkit.ship.context.PendingDocumentConsent.CandidatePhysicalIdentity;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class InitialContextModelTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void hasFourClosedTypedRequestFormsAndPreservesLiteralDocumentSigils() throws Exception {
        InitialContextRequest.None none = new InitialContextRequest.None();
        InitialContextRequest.Text explicitLiteral = new InitialContextRequest.Text("@literal");
        InitialContextRequest.Documents documents = new InitialContextRequest.Documents("requirements.md");
        InitialContextRequest.Composite composite = new InitialContextRequest.Composite(
                List.of(
                        new DocumentReference("requirements.md"), new UserText("qualification")));

        assertTrue(none.sources().isEmpty());
        assertEquals("@literal", explicitLiteral.sources().get(0).value());
        assertEquals("requirements.md", documents.sources().get(0).value());
        assertEquals(List.of(DocumentReference.class, UserText.class),
                composite.sources().stream().map(Object::getClass).toList());

        assertInstanceOf(InitialContextRequest.None.class,
                InitialContextRequest.fromConvenienceTokens(List.of()));
        assertInstanceOf(InitialContextRequest.Text.class,
                InitialContextRequest.fromConvenienceTokens(List.of("requirements")));
        assertInstanceOf(InitialContextRequest.Documents.class,
                InitialContextRequest.fromConvenienceTokens(List.of("@one.md", "@two.md")));
        InitialContextRequest.Composite escaped = assertInstanceOf(
                InitialContextRequest.Composite.class,
                InitialContextRequest.fromConvenienceTokens(List.of("@one.md", "@@literal")));
        assertEquals("@literal", ((UserText) escaped.sources().get(1)).value());

        InitialContextException malformed = assertThrows(
                InitialContextException.class,
                () -> InitialContextRequest.fromConvenienceTokens(List.of("@")));
        assertEquals(InitialContextException.Reason.MALFORMED_DOCUMENT_REFERENCE, malformed.reason());
        InitialContextException oversized = assertThrows(
                InitialContextException.class,
                () -> InitialContextRequest.fromConvenienceTokens(List.of(
                        "@" + "a".repeat(ShipTreePolicy.MAX_PATH_CHARACTERS + 1))));
        assertEquals(InitialContextException.Reason.MALFORMED_DOCUMENT_REFERENCE, oversized.reason());
        assertEquals("<oversized>", oversized.input());
        InitialContextException unsafeBlank = assertThrows(
                InitialContextException.class,
                () -> InitialContextRequest.fromConvenienceTokens(List.of("@\n")));
        assertEquals(InitialContextException.Reason.MALFORMED_DOCUMENT_REFERENCE, unsafeBlank.reason());
        assertEquals("<invalid-document-reference>", unsafeBlank.input());
        assertNull(unsafeBlank.getCause());
        assertFalse(unsafeBlank.getMessage().contains("\n"));
        assertFalse(unsafeBlank.toString().contains("\n"));
    }

    @Test
    void hasFourTypedContentAddressedContextForms() {
        InitialContext none = InitialContext.none();
        InitialContext emptyText = InitialContext.fromSources(List.of(InitialContext.userText("")));
        InitialContext documents = InitialContext.fromSources(List.of(
                InitialContext.projectDocument("requirements/one.md", "one"),
                InitialContext.projectDocument("requirements/two.md", "two")));
        InitialContext composite = InitialContext.fromSources(List.of(
                InitialContext.projectDocument("requirements.md", "document"),
                InitialContext.userText("Prefer YAML/Simple")));

        assertInstanceOf(InitialContext.None.class, none);
        assertInstanceOf(InitialContext.Text.class, emptyText);
        assertInstanceOf(InitialContext.Documents.class, documents);
        assertInstanceOf(InitialContext.Composite.class, composite);
        assertNotEquals(none.digest(), emptyText.digest());
        assertTrue(List.of(none, emptyText, documents, composite).stream()
                .allMatch(context -> context.digest().matches("sha256:[0-9a-f]{64}")));
    }

    @Test
    void sourceIdentityIsDeterministicUniqueForOccurrencesAndOrderSensitive() {
        InitialContext.Source document = InitialContext.projectDocument("requirements.md", "same");
        InitialContext first = InitialContext.fromSources(List.of(
                document, document, InitialContext.userText("qualification")));
        InitialContext second = InitialContext.fromSources(List.of(
                document, document, InitialContext.userText("qualification")));
        InitialContext reordered = InitialContext.fromSources(List.of(
                InitialContext.userText("qualification"), document, document));

        assertEquals(first, second);
        assertEquals(first.sources().get(0).id() + "-2", first.sources().get(1).id());
        assertNotEquals(first.digest(), reordered.digest());
        assertEquals(List.of(SourceKind.DOCUMENT, SourceKind.DOCUMENT, SourceKind.USER_TEXT),
                first.sources().stream().map(InitialContext.Source::kind).toList());
        assertThrows(IllegalArgumentException.class, () -> new InitialContext.Source(
                document.id() + "-257",
                document.kind(),
                document.provenance(),
                document.content(),
                document.byteSize(),
                document.digest()));
    }

    @Test
    void boundedCopiesNeverTrustCallerListSizeOrTraversePastTheSourceLimit() throws Exception {
        InitialContextRequest parsed = InitialContextRequest.fromConvenienceTokens(
                iteratorOnly(List.of("qualification")));
        InitialContextRequest.Text request = new InitialContextRequest.Text(
                iteratorOnly(List.of(new UserText("qualification"))));
        InitialContext.Source source = InitialContext.userText("qualification");
        InitialContext context = InitialContext.fromSources(iteratorOnly(List.of(source)));
        CandidatePhysicalIdentity identity = new CandidatePhysicalIdentity(
                17, 23, 12, 42, 43, 0100644, "rw-r--r--", 1);
        PendingDocumentConsent consent = PendingDocumentConsent.create(
                0, temporaryDirectory.resolve("external.md").toAbsolutePath().normalize(), identity);
        ContextResolution.Pending pending = new ContextResolution.Pending(
                iteratorOnly(List.of(consent)));

        assertInstanceOf(InitialContextRequest.Text.class, parsed);
        assertEquals(List.of(new UserText("qualification")), request.sources());
        assertEquals(List.of(source), context.sources());
        assertEquals(List.of(consent), pending.consents());

        List<UserText> exactRequestLimit = Collections.nCopies(
                InitialContext.MAX_SOURCES, new UserText(""));
        List<InitialContext.Source> exactContextLimit = Collections.nCopies(
                InitialContext.MAX_SOURCES, InitialContext.userText(""));
        assertEquals(
                InitialContext.MAX_SOURCES,
                new InitialContextRequest.Text(exactRequestLimit).sources().size());
        assertEquals(
                InitialContext.MAX_SOURCES,
                InitialContext.fromSources(exactContextLimit).sources().size());

        List<String> tooManyTokens = overLimitWithoutExtraRead("qualification");
        InitialContextException tokenFailure = assertThrows(
                InitialContextException.class,
                () -> InitialContextRequest.fromConvenienceTokens(tooManyTokens));
        assertEquals(InitialContextException.Reason.CONTEXT_TOO_LARGE, tokenFailure.reason());
        assertThrows(
                IllegalArgumentException.class,
                () -> new InitialContextRequest.Text(
                        overLimitWithoutExtraRead(
                                new UserText("qualification"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> InitialContext.fromSources(overLimitWithoutExtraRead(source)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ContextResolution.Pending(overLimitWithoutExtraRead(consent)));
    }

    @Test
    void rejectsMalformedSurrogatesInsteadOfHashingLossyReplacementBytes() {
        InitialContext.Source questionMark = InitialContext.userText("?");

        ContextModelSupport.StrictUtf8Exception highSurrogate = assertThrows(
                ContextModelSupport.StrictUtf8Exception.class,
                () -> InitialContext.userText("\ud800"));
        ContextModelSupport.StrictUtf8Exception lowSurrogate = assertThrows(
                ContextModelSupport.StrictUtf8Exception.class,
                () -> InitialContext.userText("\udc00"));
        assertEquals(ContextModelSupport.Utf8Failure.MALFORMED_SCALAR, highSurrogate.failure());
        assertEquals(ContextModelSupport.Utf8Failure.MALFORMED_SCALAR, lowSurrogate.failure());
        assertNotNull(questionMark.digest());
        assertArrayEquals(
                "A\u20ac\ud83d\ude00".getBytes(StandardCharsets.UTF_8),
                ContextModelSupport.encodeStrictUtf8("A\u20ac\ud83d\ude00", 8, "test value"));
        ContextModelSupport.StrictUtf8Exception byteLimit = assertThrows(
                ContextModelSupport.StrictUtf8Exception.class,
                () -> ContextModelSupport.encodeStrictUtf8("\ud83d\ude00", 3, "test value"));
        assertEquals(ContextModelSupport.Utf8Failure.BYTE_LIMIT_EXCEEDED, byteLimit.failure());
        assertThrows(
                ContextModelSupport.StrictUtf8Exception.class,
                () -> InitialContext.projectDocument("docs/requirements.md", "\ud800"));
    }

    @Test
    void workerContextContainsOnlyLogicalAndSanitizedProvenance() {
        String hostPath = temporaryDirectory.toAbsolutePath().toString();
        String secretContent = "sentinel-context-content";
        InitialContext context = InitialContext.fromSources(List.of(
                InitialContext.projectDocument("docs/requirements.md", secretContent),
                InitialContext.userText(secretContent)));

        assertFalse(context.toString().contains(hostPath));
        assertFalse(context.toString().contains(secretContent));
        assertFalse(context.sources().get(0).toString().contains(secretContent));
        assertFalse(new UserText(secretContent).toString().contains(secretContent));
        assertFalse(new DocumentReference(hostPath).toString().contains(hostPath));
        assertEquals("project:docs/requirements.md", context.sources().get(0).provenance());
        assertTrue(context.sources().stream().allMatch(source -> source.id().startsWith("source-")));
        assertFalse(List.of(InitialContext.Source.class.getRecordComponents()).stream()
                .anyMatch(component -> component.getType() == Path.class || component.getType() == URI.class));
        assertThrows(IllegalArgumentException.class,
                () -> InitialContext.projectDocument(hostPath + "/requirements.md", "requirements"));
        assertThrows(IllegalArgumentException.class,
                () -> InitialContext.projectDocument("../requirements.md", "requirements"));
        assertThrows(IllegalArgumentException.class,
                () -> InitialContext.projectDocument("C:/requirements.md", "requirements"));
        assertThrows(IllegalArgumentException.class,
                () -> InitialContext.projectDocument("docs/unsafe\nname.md", "requirements"));
        assertThrows(IllegalArgumentException.class,
                () -> InitialContext.projectDocument("docs/unsafe\u202ename.md", "requirements"));
        assertThrows(IllegalArgumentException.class,
                () -> InitialContext.projectDocument("x".repeat(256) + ".md", "requirements"));
    }

    @Test
    void projectProvenanceAdmitsTheCompleteCanonicalPathLimit() {
        String maxPath = ("a".repeat(255) + '/').repeat(15) + "a".repeat(254) + "/a";

        assertEquals(4_096, maxPath.length());
        InitialContext.Source source = InitialContext.projectDocument(maxPath, "requirements");
        assertEquals(InitialContext.MAX_PROVENANCE_CHARACTERS, source.provenance().length());
        assertEquals("project:" + maxPath, source.provenance());
    }

    @Test
    void pendingExternalDocumentIsDescriptiveAndSubjectBound() {
        Path external = temporaryDirectory.resolve("external.md").toAbsolutePath().normalize();
        CandidatePhysicalIdentity identity = new CandidatePhysicalIdentity(
                17, 23, 12, 42, 43, 0100644, "rw-r--r--", 1);
        PendingDocumentConsent consent = PendingDocumentConsent.create(2, external, identity);

        ContextResolution.Pending pending = new ContextResolution.Pending(consent);
        ContextResolution.Resolved resolved = new ContextResolution.Resolved(InitialContext.none());

        assertSame(consent, pending.consents().get(0));
        assertInstanceOf(InitialContext.None.class, resolved.context());
        assertEquals(2, consent.sourceOrdinal());
        assertEquals(external, consent.canonicalPath());
        assertEquals(PendingDocumentConsent.INITIAL_CONTEXT_PURPOSE, consent.purpose());
        assertTrue(consent.requestId().matches("document-request-[0-9a-f]{64}"));
        assertTrue(consent.subjectDigest().matches("sha256:[0-9a-f]{64}"));
        assertFalse(consent.toString().contains(external.toString()));
        assertFalse(consent.toString().contains(identity.stableValue()));
        assertEquals("CandidatePhysicalIdentity[redacted]", identity.toString());
        assertThrows(IllegalArgumentException.class, () -> new PendingDocumentConsent(
                consent.sourceOrdinal(), consent.requestId(), consent.canonicalPath(), consent.purpose(),
                consent.candidateIdentity(), "sha256:" + "0".repeat(64)));
    }

    @Test
    void pendingResolutionPreservesEveryExternalConsentInSourceOrder() {
        CandidatePhysicalIdentity identity = new CandidatePhysicalIdentity(
                17, 23, 12, 42, 43, 0100644, "rw-r--r--", 1);
        PendingDocumentConsent first = PendingDocumentConsent.create(
                1, temporaryDirectory.resolve("one.md").toAbsolutePath().normalize(), identity);
        PendingDocumentConsent second = PendingDocumentConsent.create(
                4, temporaryDirectory.resolve("two.md").toAbsolutePath().normalize(), identity);
        ContextResolution.Pending pending = new ContextResolution.Pending(List.of(first, second));

        assertEquals(List.of(first, second), pending.consents());
        assertThrows(UnsupportedOperationException.class, () -> pending.consents().add(first));
        assertThrows(IllegalArgumentException.class, () -> new ContextResolution.Pending(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ContextResolution.Pending(List.of(second, first)));
        assertThrows(IllegalArgumentException.class,
                () -> new ContextResolution.Pending(List.of(first, first)));
    }

    @Test
    void doesNotExposeAnExternalSourceFactoryBeforeTheConsentConsumptionSlice() {
        List<String> publicFactories = Arrays.stream(InitialContext.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getReturnType() == InitialContext.Source.class)
                .map(method -> method.getName())
                .sorted()
                .toList();
        InitialContext.Source project = InitialContext.projectDocument("requirements.md", "requirements");
        IllegalArgumentException external = assertThrows(
                IllegalArgumentException.class,
                () -> new InitialContext.Source(
                        project.id(),
                        project.kind(),
                        "external:consent-bound-document",
                        project.content(),
                        project.byteSize(),
                        project.digest()));

        assertEquals(List.of("projectDocument", "userText"), publicFactories);
        assertEquals("Resolved document provenance must be project-relative", external.getMessage());
    }

    @Test
    void externalCandidateIdentityBindsChangeTimeModePermissionsAndLinkCount() {
        Path external = temporaryDirectory.resolve("external.md").toAbsolutePath().normalize();
        CandidatePhysicalIdentity baseline = new CandidatePhysicalIdentity(
                17, 23, 12, 42, 43, 0100644, "rw-r--r--", 1);
        CandidatePhysicalIdentity changedTime = new CandidatePhysicalIdentity(
                17, 23, 12, 42, 44, 0100644, "rw-r--r--", 1);
        CandidatePhysicalIdentity changedMode = new CandidatePhysicalIdentity(
                17, 23, 12, 42, 43, 0100600, "rw-------", 1);

        assertNotEquals(baseline.stableValue(), changedTime.stableValue());
        assertNotEquals(baseline.stableValue(), changedMode.stableValue());
        assertNotEquals(
                PendingDocumentConsent.create(0, external, baseline).requestId(),
                PendingDocumentConsent.create(0, external, changedTime).requestId());
        assertThrows(IllegalArgumentException.class,
                () -> new CandidatePhysicalIdentity(
                        17, 23, 12, 42, 43, 0100644, "rw-------", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new CandidatePhysicalIdentity(
                        17, 23, 12, 42, 43, 0040755, "rwxr-xr-x", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new CandidatePhysicalIdentity(
                        17, 23, 12, 42, 43, 0100644, "rw-r--r--", 2));
    }

    @Test
    void stableFailureReasonIdsRejectUnknownValues() {
        for (InitialContextException.Reason reason : InitialContextException.Reason.values()) {
            assertSame(reason, InitialContextException.Reason.fromId(reason.id()));
        }
        assertEquals(
                "content-invalid-utf8",
                InitialContextException.Reason.CONTENT_INVALID_UTF8.id());
        assertEquals(
                "protected-root-changed",
                InitialContextException.Reason.PROTECTED_ROOT_CHANGED.id());
        assertThrows(IllegalArgumentException.class,
                () -> InitialContextException.Reason.fromId("unknown"));
    }

    private static <T> List<T> iteratorOnly(List<T> values) {
        return new AbstractList<>() {
            @Override
            public T get(int index) {
                throw new AssertionError("bounded copies must use only the caller iterator");
            }

            @Override
            public int size() {
                throw new AssertionError("bounded copies must not trust caller-reported sizes");
            }

            @Override
            public Iterator<T> iterator() {
                return values.iterator();
            }
        };
    }

    private static <T> List<T> overLimitWithoutExtraRead(T value) {
        return new AbstractList<>() {
            @Override
            public T get(int index) {
                throw new AssertionError("bounded copies must use only the caller iterator");
            }

            @Override
            public int size() {
                throw new AssertionError("bounded copies must not trust caller-reported sizes");
            }

            @Override
            public Iterator<T> iterator() {
                return new Iterator<>() {
                    private int returned;

                    @Override
                    public boolean hasNext() {
                        return returned <= InitialContext.MAX_SOURCES;
                    }

                    @Override
                    public T next() {
                        if (returned == InitialContext.MAX_SOURCES) {
                            throw new AssertionError("bounded copies must not read source 257");
                        }
                        returned++;
                        return value;
                    }
                };
            }
        };
    }
}
