package io.github.luigidemasi.camelkit.ship.schema;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogComponentModel;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogEvidenceSet;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogSubject;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogTarget;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogUsageRecord;
import io.github.luigidemasi.camelkit.ship.context.InitialContext;
import io.github.luigidemasi.camelkit.ship.controller.ShipEventType;
import io.github.luigidemasi.camelkit.ship.controller.ShipInteractionBundle;
import io.github.luigidemasi.camelkit.ship.controller.ShipStamp;
import io.github.luigidemasi.camelkit.ship.controller.ShipState;
import io.github.luigidemasi.camelkit.ship.evidence.CommandEvidence;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger;
import io.github.luigidemasi.camelkit.ship.ledger.LedgerStatus;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction;
import io.github.luigidemasi.camelkit.ship.protocol.ProducedArtifact;
import io.github.luigidemasi.camelkit.ship.protocol.ShipStage;
import io.github.luigidemasi.camelkit.ship.protocol.StageCapability;
import io.github.luigidemasi.camelkit.ship.protocol.StageRequest;
import io.github.luigidemasi.camelkit.ship.protocol.StageResult;
import io.github.luigidemasi.camelkit.ship.resolver.MavenCoordinate;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Draft202012;
import com.networknt.schema.resource.SchemaLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipSchemaResourceTest {

    private static final String DRAFT_2020_12 = "https://json-schema.org/draft/2020-12/schema";
    private static final String ID_BASE = "https://github.com/luigidemasi/camel-kit/schemas/ship/v1/";
    private static final String RESOURCE_BASE = "ship/schema/";
    private static final List<String> FILES = List.of(
            "adapter-conformance-evidence.schema.json",
            "artifact-manifest.schema.json",
            "catalog-evidence.schema.json",
            "catalog-usage.schema.json",
            "command-evidence.schema.json",
            "decision-ledger.schema.json",
            "initial-context.schema.json",
            "interaction-bundle.schema.json",
            "interaction.schema.json",
            "ship-event.schema.json",
            "ship-stamp.schema.json",
            "stage-request.schema.json",
            "stage-result.schema.json");
    private static final List<String> WORKERS = List.of(
            "discovery", "review", "design", "plan", "execute", "validate");
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    @Test
    void schemasAreClosedVersionedDraft202012Documents() throws Exception {
        for (String file : FILES) {
            JsonNode schema = readSchema(file);
            assertEquals(DRAFT_2020_12, schema.path("$schema").asText(), file);
            assertEquals(ID_BASE + file, schema.path("$id").asText(), file);
            assertStrictObjectSchemas(schema, file);
        }
    }

    @Test
    void schemaShapesMatchCurrentWireRecords() throws Exception {
        JsonNode context = readSchema("initial-context.schema.json");
        assertEquals(
                Set.of("kind", "sources", "digest"),
                fieldNames(context.path("properties")));
        assertRecordShape(context, "/$defs/source", InitialContext.Source.class);

        JsonNode ledger = readSchema("decision-ledger.schema.json");
        assertRecordShape(ledger, "", DecisionLedger.class);
        assertRecordShape(ledger, "/$defs/sourceRef", DecisionLedger.SourceRef.class);
        assertRecordShape(ledger, "/$defs/entry", DecisionLedger.Entry.class);
        assertRecordShape(ledger, "/$defs/claim", DecisionLedger.Claim.class);
        assertRecordShape(ledger, "/$defs/conflict", DecisionLedger.Conflict.class);
        assertRecordShape(ledger, "/$defs/assumption", DecisionLedger.Assumption.class);
        assertRecordShape(ledger, "/$defs/catalogEvidence", DecisionLedger.CatalogEvidence.class);
        assertRecordShape(ledger, "/$defs/question", DecisionLedger.Question.class);
        assertRecordShape(ledger, "/$defs/category", DecisionLedger.Category.class);
        assertRecordShape(ledger, "/$defs/requirementsPolicy", DecisionLedger.RequirementsPolicy.class);
        assertRecordShape(ledger, "/$defs/routeContract", DecisionLedger.RouteContract.class);

        JsonNode catalogEvidence = readSchema("catalog-evidence.schema.json");
        assertEquals(
                Set.of(
                        "schemaVersion",
                        "runId",
                        "prerequisiteLedgerRevision",
                        "prerequisiteLedgerDigest",
                        "requestAttemptId",
                        "requestPolicyDigest",
                        "requestedSubjects",
                        "evidence"),
                fieldNames(catalogEvidence.path("properties")));
        assertRecordShape(catalogEvidence, "/$defs/catalogSubject", CatalogSubject.class);
        assertRecordShape(catalogEvidence, "/$defs/target", CatalogTarget.class);
        assertRecordShape(catalogEvidence, "/$defs/coordinate", MavenCoordinate.class);
        assertRecordShape(catalogEvidence, "/$defs/evidenceSet", CatalogEvidenceSet.class);
        assertRecordShape(
                catalogEvidence,
                "/$defs/artifactEvidence",
                CatalogEvidenceSet.ArtifactEvidence.class);
        assertRecordShape(
                catalogEvidence,
                "/$defs/subjectEvidence",
                CatalogEvidenceSet.SubjectEvidence.class);

        JsonNode usage = readSchema("catalog-usage.schema.json");
        assertRecordShape(usage, "", CatalogUsageRecord.class);
        assertRecordShape(usage, "/$defs/routeUsage", CatalogUsageRecord.RouteUsage.class);
        assertRecordShape(usage, "/$defs/endpointUsage", CatalogUsageRecord.EndpointUsage.class);
        assertRecordShape(usage, "/$defs/componentModel", CatalogComponentModel.class);
        assertRecordShape(usage, "/$defs/componentOption", CatalogComponentModel.Option.class);
        assertRecordShape(usage, "/$defs/runtimeDependency", CatalogUsageRecord.RuntimeDependency.class);

        JsonNode request = readSchema("stage-request.schema.json");
        assertRecordShape(request, "", StageRequest.class);
        assertRecordShape(request, "/$defs/capability", StageCapability.class);
        JsonNode result = readSchema("stage-result.schema.json");
        assertRecordShape(result, "", StageResult.class);
        assertRecordShape(result, "/$defs/catalogSubject", CatalogSubject.class);
        assertRecordShape(result, "/$defs/producedArtifact", ProducedArtifact.class);

        JsonNode interaction = readSchema("interaction.schema.json");
        assertRecordShape(interaction, "/$defs/discoveryChallenge", Interaction.DiscoveryChallenge.class);
        assertRecordShape(interaction, "/$defs/discoveryAnswer", Interaction.DiscoveryAnswer.class);
        assertRecordShape(
                interaction,
                "/$defs/documentConsentChallenge",
                Interaction.DocumentConsentChallenge.class);
        assertRecordShape(
                interaction,
                "/$defs/documentConsentResponse",
                Interaction.DocumentConsentResponse.class);
        assertEquals(
                Integer.MAX_VALUE,
                interaction.at("/$defs/documentConsentChallenge/properties/sourceOrdinal/maximum").longValue());
        assertEquals(
                Integer.MAX_VALUE,
                interaction.at("/$defs/documentConsentResponse/properties/sourceOrdinal/maximum").longValue());
        assertRecordShape(
                interaction,
                "/$defs/remoteProviderConsentChallenge",
                Interaction.RemoteProviderConsentChallenge.class);
        assertRecordShape(
                interaction,
                "/$defs/remoteProviderConsentResponse",
                Interaction.RemoteProviderConsentResponse.class);
        assertRecordShape(interaction, "/$defs/designChallenge", Interaction.DesignChallenge.class);
        assertRecordShape(interaction, "/$defs/designResponse", Interaction.DesignResponse.class);
        assertRecordShape(interaction, "/$defs/planChallenge", Interaction.PlanChallenge.class);
        assertRecordShape(interaction, "/$defs/planResponse", Interaction.PlanResponse.class);
        assertRecordShape(interaction, "/$defs/waiverChallenge", Interaction.WaiverChallenge.class);
        assertRecordShape(interaction, "/$defs/waiverResponse", Interaction.WaiverResponse.class);

        JsonNode bundle = readSchema("interaction-bundle.schema.json");
        assertRecordShape(bundle, "", ShipInteractionBundle.class);
        assertRecordShape(bundle, "/$defs/exchange", ShipInteractionBundle.Exchange.class);
        assertRecordShape(bundle, "/$defs/discoveryPair", ShipInteractionBundle.DiscoveryPair.class);
        assertRecordShape(
                bundle,
                "/$defs/documentConsentPair",
                ShipInteractionBundle.DocumentConsentPair.class);
        assertRecordShape(
                bundle,
                "/$defs/remoteProviderConsentPair",
                ShipInteractionBundle.RemoteProviderConsentPair.class);
        assertRecordShape(bundle, "/$defs/designPair", ShipInteractionBundle.DesignPair.class);
        assertRecordShape(bundle, "/$defs/planPair", ShipInteractionBundle.PlanPair.class);
        assertRecordShape(bundle, "/$defs/waiverPair", ShipInteractionBundle.WaiverPair.class);

        JsonNode manifest = readSchema("artifact-manifest.schema.json");
        assertRecordShape(manifest, "", ArtifactManifest.class);
        assertRecordShape(manifest, "/$defs/routeArtifact", ArtifactManifest.RouteArtifact.class);
        assertRecordShape(manifest, "/$defs/testArtifact", ArtifactManifest.TestArtifact.class);
        assertRecordShape(manifest, "/$defs/declaredArtifact", ArtifactManifest.DeclaredArtifact.class);

        JsonNode command = readSchema("command-evidence.schema.json");
        assertRecordShape(command, "", CommandEvidence.class);
        assertRecordShape(command, "/$defs/sandboxIdentity", CommandEvidence.SandboxIdentity.class);
        JsonNode stamp = readSchema("ship-stamp.schema.json");
        assertRecordShape(stamp, "", ShipStamp.class);
        assertRecordShape(stamp, "/$defs/check", ShipStamp.Check.class);
        assertRecordShape(stamp, "/$defs/waiver", ShipStamp.Waiver.class);
    }

    @Test
    void schemaEnumsUseCurrentStableVocabulary() throws Exception {
        JsonNode context = readSchema("initial-context.schema.json");
        assertEnum(
                context.at("/properties/kind"),
                Stream.of(InitialContext.Kind.values()).map(InitialContext.Kind::id));
        assertEnum(
                context.at("/$defs/source/properties/kind"),
                Stream.of(InitialContext.SourceKind.values()).map(InitialContext.SourceKind::id));

        JsonNode ledger = readSchema("decision-ledger.schema.json");
        assertEnum(ledger.at("/$defs/status"), Stream.of(LedgerStatus.values()).map(Enum::name));
        assertEnum(
                ledger.at("/properties/gapReviewStatus"),
                Stream.of(DecisionLedger.GapReviewStatus.values()).map(Enum::name));

        JsonNode request = readSchema("stage-request.schema.json");
        assertEnum(request.at("/$defs/stage"), Stream.of(ShipStage.values()).map(Enum::name));
        assertEnum(
                request.at("/$defs/capability/properties/repositoryAccess"),
                Stream.of(StageCapability.RepositoryAccess.values()).map(Enum::name));
        assertEnum(
                request.at("/$defs/capability/properties/allowedOperations/items"),
                Stream.of(StageCapability.Operation.values()).map(Enum::name));

        JsonNode result = readSchema("stage-result.schema.json");
        assertEnum(result.at("/properties/outcome"), Stream.of(StageResult.Outcome.values()).map(Enum::name));

        JsonNode interaction = readSchema("interaction.schema.json");
        assertEnum(
                interaction.at("/$defs/documentConsentResponse/properties/decision"),
                Stream.of(Interaction.ConsentDecision.values()).map(Enum::name));
        assertEnum(
                interaction.at("/$defs/designResponse/properties/decision"),
                Stream.of(Interaction.DesignDecision.values()).map(Enum::name));
        assertEnum(
                interaction.at("/$defs/planResponse/properties/decision"),
                Stream.of(Interaction.PlanDecision.values()).map(Enum::name));
        assertEnum(
                interaction.at("/$defs/waiverResponse/properties/decision"),
                Stream.of(Interaction.WaiverDecision.values()).map(Enum::name));

        JsonNode event = readSchema("ship-event.schema.json");
        assertEnum(
                event.at("/properties/type"),
                Stream.of(ShipEventType.values()).map(ShipEventType::stableId));
        assertEnum(
                event.at("/$defs/state"),
                Stream.of(ShipState.values()).map(ShipState::stableId));

        JsonNode stamp = readSchema("ship-stamp.schema.json");
        assertEnum(stamp.at("/properties/status"), Stream.of(ShipStamp.Status.values()).map(Enum::name));
        assertEquals("^[a-z][a-z0-9-]{0,63}$", stamp.at("/properties/adapterId/pattern").asText());
    }

    @Test
    void everyReferenceResolvesInsideThePackagedSet() throws Exception {
        Map<URI, JsonNode> schemas = new LinkedHashMap<>();
        for (String file : FILES) {
            JsonNode schema = readSchema(file);
            assertNull(schemas.put(URI.create(schema.path("$id").asText()), schema), file);
        }

        AtomicInteger references = new AtomicInteger();
        for (Map.Entry<URI, JsonNode> entry : schemas.entrySet()) {
            assertReferencesResolve(
                    entry.getValue(), entry.getKey(), schemas, references, entry.getKey().toString());
        }
        assertTrue(references.get() > 20, "Expected a meaningful closed reference graph");
    }

    @Test
    void everySchemaCompilesWithoutNetworkResolution() throws Exception {
        Map<String, String> resources = new LinkedHashMap<>();
        for (String file : FILES) {
            resources.put(ID_BASE + file, readText(RESOURCE_BASE + file));
        }
        Set<String> allowed = Set.copyOf(resources.keySet());
        SchemaLoader loader = SchemaLoader.builder()
                .resourceLoaders(loaders -> loaders.resources(resources))
                .allow(iri -> allowed.contains(iri.toString()))
                .build();
        SchemaRegistry registry = SchemaRegistry.withDialect(
                Draft202012.getInstance(), builder -> builder.schemaLoader(loader));
        for (String id : allowed) {
            Schema schema = registry.getSchema(SchemaLocation.of(id));
            assertNotNull(schema, id);
            assertFalse(schema.validate(MAPPER.createObjectNode()).isEmpty(), id);
        }
    }

    @Test
    void successfulRunnerEvidenceWithSeparatelyRetainedExecutablesMatchesTheWireSchema()
            throws Exception {
        String digest = "sha256:" + "0".repeat(64);
        JsonNode evidence = MAPPER.readTree("""
                {
                  "schemaVersion": 1,
                  "commandId": "main-runtime",
                  "sandbox": {
                    "provider": "bubblewrap",
                    "executable": "/usr/bin/bwrap",
                    "executableDigest": "$DIGEST",
                    "postExecutableDigest": "$DIGEST",
                    "executableSnapshot": null,
                    "profileDigest": "$DIGEST"
                  },
                  "toolchainDigest": "$DIGEST",
                  "postToolchainDigest": "$DIGEST",
                  "toolchainSnapshot": "/state/toolchain.tar",
                  "toolchainSnapshotDigest": "$DIGEST",
                  "executable": "/jdk/bin/java",
                  "executableDigest": "$DIGEST",
                  "postExecutableDigest": "$DIGEST",
                  "executableSnapshot": null,
                  "executableVersion": "Apache Camel 4.21.0",
                  "arguments": ["/jdk/bin/java", "-version"],
                  "workingDirectory": "/project",
                  "controlledEnvironment": {
                    "CAMEL_KIT_JDK_DIGEST": "$DIGEST",
                    "HOME": "/sandbox/home",
                    "JAVA_HOME": "/jdk",
                    "JAVA_TOOL_OPTIONS": "-Duser.home=/sandbox/home",
                    "LANG": "C",
                    "LC_ALL": "C",
                    "TMPDIR": "/sandbox/tmp"
                  },
                  "startedAt": "2026-07-21T12:00:00Z",
                  "endedAt": "2026-07-21T12:00:01Z",
                  "launched": true,
                  "timedOut": false,
                  "exitCode": 0,
                  "launchError": null,
                  "stdoutLog": "/evidence/stdout.log",
                  "stdoutDigest": "$DIGEST",
                  "stderrLog": "/evidence/stderr.log",
                  "stderrDigest": "$DIGEST",
                  "inputDigests": ["$DIGEST"]
                }
                """.replace("$DIGEST", digest));

        List<com.networknt.schema.Error> errors = compiledSchema("command-evidence.schema.json")
                .validate(evidence);
        assertTrue(errors.isEmpty(), errors::toString);
    }

    @Test
    void catalogCoordinateSchemaRejectsNonCanonicalOrMutableIdentities() throws Exception {
        Schema coordinate = compiledSchema("catalog-evidence.schema.json#/$defs/coordinate");
        ObjectNode valid = (ObjectNode) MAPPER.readTree("""
                {
                  "groupId": "org.apache.camel",
                  "artifactId": "camel-catalog",
                  "extension": "jar",
                  "classifier": "",
                  "version": "4.21.0"
                }
                """);
        assertTrue(coordinate.validate(valid).isEmpty());

        for (String invalidGroup : List.of(".org.apache.camel", "org.apache.camel.", "org..apache.camel")) {
            ObjectNode invalid = valid.deepCopy();
            invalid.put("groupId", invalidGroup);
            assertFalse(coordinate.validate(invalid).isEmpty(), invalidGroup);
        }
        for (String invalidVersion : List.of(
                ".4.21.0", "4..21.0", "4.21.0.", "4.21.0-SNAPSHOT", "LATEST", "RELEASE",
                "4.21-20260721.120000-1")) {
            ObjectNode invalid = valid.deepCopy();
            invalid.put("version", invalidVersion);
            assertFalse(coordinate.validate(invalid).isEmpty(), invalidVersion);
        }
    }

    @Test
    void archivalAdapterSchemaCannotBeMistakenForRuntimeAdmission() throws Exception {
        JsonNode schema = readSchema("adapter-conformance-evidence.schema.json");
        assertTrue(schema.path("description").asText().contains("evidence-v2"));
        Set<String> fields = fieldNames(schema.path("properties"));
        assertFalse(fields.stream().anyMatch(field -> field.endsWith("RealPath")));
        assertTrue(fields.containsAll(Set.of(
                "ingressMode",
                "interactionProfile",
                "launcherProfile",
                "sandboxProfile",
                "providerProfile",
                "protocolProfile",
                "evidenceProfile")));
    }

    @Test
    void workerContractsAreTypedStageScopedAndNonChaining() throws Exception {
        for (String worker : WORKERS) {
            String content = readText("ship/workers/" + worker + ".md");
            assertFalse(content.isBlank(), worker);
            assertTrue(content.contains("stage-result.schema.json"), worker);
            assertTrue(content.toLowerCase(Locale.ROOT).contains("controller"), worker);
            assertTrue(
                    content.contains("Never invoke")
                            || content.matches("(?s).*Do not[^\\n]*invoke.*"),
                    worker);
        }
        String discovery = readText("ship/workers/discovery.md");
        assertTrue(discovery.contains("Analyze all supplied sources before proposing a question."));
        assertTrue(discovery.contains("Return at most one material question."));
        assertTrue(discovery.contains("Never expose or reconstruct a host canonical path."));
        assertTrue(readText("skills/shared/discovery-completeness.md")
                .contains("Ask exactly one highest-priority blocking question at a time."));
        assertTrue(readText("skills/camel-brainstorm/SKILL.md")
                .contains("shared/discovery-completeness.md"));
    }

    private static JsonNode readSchema(String file) throws IOException {
        return MAPPER.readTree(readText(RESOURCE_BASE + file));
    }

    private static Schema compiledSchema(String location) throws IOException {
        Map<String, String> resources = new LinkedHashMap<>();
        for (String file : FILES) {
            resources.put(ID_BASE + file, readText(RESOURCE_BASE + file));
        }
        Set<String> allowed = Set.copyOf(resources.keySet());
        SchemaLoader loader = SchemaLoader.builder()
                .resourceLoaders(loaders -> loaders.resources(resources))
                .allow(iri -> allowed.contains(iri.toString()))
                .build();
        SchemaRegistry registry = SchemaRegistry.withDialect(
                Draft202012.getInstance(), builder -> builder.schemaLoader(loader));
        return registry.getSchema(SchemaLocation.of(ID_BASE + location));
    }

    private static String readText(String resource) throws IOException {
        try (InputStream input = ShipSchemaResourceTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            byte[] bytes = input.readNBytes(16 * 1024 * 1024 + 1);
            assertTrue(bytes.length <= 16 * 1024 * 1024, resource);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static void assertStrictObjectSchemas(JsonNode node, String path) {
        if (node.isObject()) {
            if ("object".equals(node.path("type").asText())) {
                assertEquals(
                        false,
                        node.path("additionalProperties").asBoolean(true),
                        path + " must reject unknown fields");
                assertEquals(
                        fieldNames(node.path("properties")),
                        textValues(node.path("required")),
                        path + " must require every declared wire field");
            }
            node.fields().forEachRemaining(
                    entry -> assertStrictObjectSchemas(entry.getValue(), path + "/" + entry.getKey()));
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                assertStrictObjectSchemas(node.get(index), path + "/" + index);
            }
        }
    }

    private static void assertRecordShape(JsonNode schema, String pointer, Class<?> recordType) {
        JsonNode object = pointer.isEmpty() ? schema : schema.at(pointer);
        assertFalse(object.isMissingNode(), pointer);
        assertTrue(recordType.isRecord(), recordType.getName());
        Set<String> expected = Stream.of(recordType.getRecordComponents())
                .map(component -> component.getName())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertEquals(expected, fieldNames(object.path("properties")), recordType.getSimpleName());
        assertEquals(expected, textValues(object.path("required")), recordType.getSimpleName());
    }

    private static void assertEnum(JsonNode schema, Stream<String> expectedValues) {
        assertEquals(expectedValues.collect(java.util.stream.Collectors.toSet()), textValues(schema.path("enum")));
    }

    private static Set<String> fieldNames(JsonNode object) {
        Set<String> fields = new LinkedHashSet<>();
        object.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private static Set<String> textValues(JsonNode array) {
        Set<String> values = new LinkedHashSet<>();
        if (array.isArray()) {
            array.forEach(value -> values.add(value.asText()));
        }
        return values;
    }

    private static void assertReferencesResolve(
            JsonNode node,
            URI base,
            Map<URI, JsonNode> schemas,
            AtomicInteger references,
            String path) {
        if (node.isObject()) {
            JsonNode reference = node.get("$ref");
            if (reference != null) {
                references.incrementAndGet();
                URI resolved = base.resolve(reference.asText());
                URI document = withoutFragment(resolved);
                JsonNode target = schemas.get(document);
                assertNotNull(target, path + " resolves outside the packaged schema set: " + resolved);
                if (resolved.getFragment() != null && !resolved.getFragment().isEmpty()) {
                    String pointer = resolved.getFragment();
                    assertTrue(pointer.startsWith("/"), resolved.toString());
                    assertFalse(target.at(pointer).isMissingNode(), resolved.toString());
                }
            }
            node.fields().forEachRemaining(entry -> assertReferencesResolve(
                    entry.getValue(), base, schemas, references, path + "/" + entry.getKey()));
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                assertReferencesResolve(node.get(index), base, schemas, references, path + "/" + index);
            }
        }
    }

    private static URI withoutFragment(URI value) {
        return URI.create(value.toString().split("#", 2)[0]);
    }
}
