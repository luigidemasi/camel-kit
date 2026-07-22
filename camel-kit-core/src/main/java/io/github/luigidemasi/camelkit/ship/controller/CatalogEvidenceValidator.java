package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.RouteArtifact;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactValidator;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactValidator.CatalogDependencyBinding;
import io.github.luigidemasi.camelkit.ship.catalog.CamelYamlCatalogUsageExtractor;
import io.github.luigidemasi.camelkit.ship.catalog.CamelYamlCatalogUsageExtractor.Extraction;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogComponentModel;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogEvidenceSet;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogEvidenceSet.SubjectEvidence;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogSubject;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogTarget;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogUsageRecord;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogUsageRecord.EndpointUsage;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogUsageRecord.RouteUsage;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogUsageRecord.RuntimeDependency;
import io.github.luigidemasi.camelkit.ship.catalog.ShipCatalogService.Snapshot;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger;

/** Deterministically binds catalog evidence and accepted usage to one frozen catalog snapshot. */
final class CatalogEvidenceValidator {

    private static final int MAX_SUBJECTS = 512;
    private static final int MAX_INVENTORY_SUBJECTS = 8_192;
    private static final int MAX_ROUTES = 512;
    private static final int MAX_ROUTE_ENDPOINTS = 2_048;
    private static final int MAX_RUNTIME_DEPENDENCIES = 48;
    private static final Pattern RUN_ID = Pattern.compile("ship-[0-9a-f]{32}");
    private static final Pattern SAFE_MAVEN_TOKEN = Pattern.compile("[A-Za-z0-9_.-]{1,128}");
    private static final Pattern TIMESTAMPED_SNAPSHOT = Pattern.compile(".*-\\d{8}\\.\\d{6}-\\d+$");
    private static final Comparator<RouteUsage> ROUTE_ORDER = Comparator.comparing(RouteUsage::path)
            .thenComparing(RouteUsage::routeId);
    private static final Comparator<EndpointUsage> ENDPOINT_ORDER = Comparator
            .comparing((EndpointUsage value) -> value.component().name())
            .thenComparingInt(EndpointUsage::pathParameterCount)
            .thenComparing(value -> String.join("\u0000", value.componentOptions()))
            .thenComparing(value -> String.join("\u0000", value.endpointOptions()));

    private CatalogEvidenceValidator() {
    }

    /** Derives the exact route/catalog/dependency closure from one sealed candidate. */
    static CatalogUsageRecord deriveUsage(
            Snapshot snapshot,
            DecisionLedger ledger,
            CatalogEvidenceSet approvedEvidence,
            ArtifactManifest manifest,
            Path candidate,
            String runId,
            String catalogEvidenceDigest,
            String artifactManifestDigest,
            String candidateSnapshotDigest,
            String candidateContentDigest)
            throws IOException {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(ledger, "ledger must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");
        CatalogTarget target = snapshot.target();
        validateManifestIdentity(manifest, target);
        List<CatalogSubject> inventory = canonicalInventory(snapshot.availableSubjects());
        String inventoryDigest = ShipDigest.sha256(
                ShipJson.mapper().writeValueAsBytes(inventory));
        CamelYamlCatalogUsageExtractor extractor = new CamelYamlCatalogUsageExtractor();
        List<RouteArtifact> manifestRoutes = manifest.routes().stream()
                .sorted(Comparator.comparing(RouteArtifact::path)
                        .thenComparing(RouteArtifact::routeId))
                .toList();
        TreeSet<CatalogSubject> used = new TreeSet<>();
        for (RouteArtifact route : manifestRoutes) {
            Extraction extraction = extractor.extractBound(candidate.resolve(route.path()), inventory);
            if (!route.digest().equals(extraction.digest())) {
                reject("catalog-route-digest-mismatch",
                        "Catalog extraction did not read the accepted route bytes: " + route.path());
            }
            used.addAll(extraction.subjects());
        }
        CatalogEvidenceSet exactUsage = resolveEvidence(snapshot, target, used);
        List<CatalogSubject> components = used.stream()
                .filter(subject -> subject.kind() == CatalogSubject.Kind.COMPONENT)
                .toList();
        List<CatalogComponentModel> models = snapshot.componentModelsFor(components).stream()
                .sorted(Comparator.comparing(model -> model.evidence().subject()))
                .toList();
        List<RouteUsage> routes = new ArrayList<>();
        for (RouteArtifact route : manifestRoutes) {
            Extraction extraction = extractor.extractBound(
                    candidate.resolve(route.path()), inventory, models);
            if (!route.digest().equals(extraction.digest())) {
                reject("catalog-route-digest-mismatch",
                        "Catalog endpoint validation did not read the accepted route bytes: "
                                                        + route.path());
            }
            routes.add(new RouteUsage(
                    route.routeId(), route.path(), route.digest(),
                    extraction.subjects(), extraction.endpoints()));
        }
        CatalogDependencyBinding dependencies = ArtifactValidator.bindCatalogDependencies(
                candidate, ledger.requirementsPolicy(), exactUsage);
        if (!dependencies.validation().passed() || dependencies.pomDigest() == null) {
            reject("catalog-runtime-dependencies-invalid",
                    "Candidate runtime dependencies do not match frozen catalog usage");
        }
        CatalogUsageRecord usage = new CatalogUsageRecord(
                CatalogUsageRecord.SCHEMA_VERSION,
                runId,
                catalogEvidenceDigest,
                artifactManifestDigest,
                candidateSnapshotDigest,
                candidateContentDigest,
                dependencies.pomDigest(),
                inventoryDigest,
                routes,
                models,
                dependencies.runtimeDependencies(),
                exactUsage);
        validateUsage(
                snapshot,
                new UsageBinding(
                        runId, catalogEvidenceDigest, artifactManifestDigest,
                        candidateSnapshotDigest, candidateContentDigest,
                        dependencies.pomDigest()),
                approvedEvidence,
                manifest,
                usage);
        return usage;
    }

    /** Resolves exact evidence from the already-frozen snapshot for one canonical controller request. */
    static CatalogEvidenceSet resolveEvidence(
            Snapshot snapshot, CatalogTarget expectedTarget, Collection<CatalogSubject> requested)
            throws IOException {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(expectedTarget, "expectedTarget must not be null");
        if (!expectedTarget.equals(snapshot.target())) {
            reject("catalog-evidence-target-mismatch",
                    "Frozen catalog snapshot differs from the controller-approved target");
        }
        List<CatalogSubject> canonical = canonicalSubjects(requested, "catalog-evidence-subject-invalid");
        CatalogEvidenceSet evidence = snapshot.evidenceFor(canonical);
        validateEvidence(evidence, expectedTarget, canonical);
        return evidence;
    }

    /** Revalidates a persisted evidence value against its exact target and requested subject set. */
    static void validateEvidence(
            CatalogEvidenceSet evidence, CatalogTarget expectedTarget, Collection<CatalogSubject> requested) {
        Objects.requireNonNull(expectedTarget, "expectedTarget must not be null");
        List<CatalogSubject> canonical = canonicalSubjects(requested, "catalog-evidence-subject-invalid");
        if (evidence == null
                || evidence.schemaVersion() != CatalogEvidenceSet.SCHEMA_VERSION
                || !expectedTarget.equals(evidence.target())
                || !ShipDigest.isSha256(evidence.digest())) {
            reject("catalog-evidence-invalid", "Catalog evidence has an invalid target or identity");
        }
        List<CatalogSubject> observed = evidence.subjects().stream()
                .map(SubjectEvidence::subject)
                .toList();
        if (!canonical.equals(observed)) {
            reject("catalog-evidence-subject-mismatch",
                    "Catalog evidence differs from the controller-requested subject set");
        }
    }

    /**
     * Replays every provenance-bearing catalog value from the same frozen snapshot and checks usage closure.
     */
    static void validateUsage(
            Snapshot snapshot,
            UsageBinding expected,
            CatalogEvidenceSet approvedEvidence,
            ArtifactManifest manifest,
            CatalogUsageRecord usage)
            throws IOException {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(expected, "expected must not be null");
        if (usage == null
                || usage.schemaVersion() != CatalogUsageRecord.SCHEMA_VERSION
                || !expected.runId().equals(usage.runId())
                || !expected.catalogEvidenceDigest().equals(usage.catalogEvidenceDigest())
                || !expected.artifactManifestDigest().equals(usage.artifactManifestDigest())
                || !expected.candidateSnapshotDigest().equals(usage.candidateSnapshotDigest())
                || !expected.candidateContentDigest().equals(usage.candidateContentDigest())
                || !expected.pomDigest().equals(usage.pomDigest())
                || !ShipDigest.isSha256(usage.inventoryDigest())) {
            reject("catalog-usage-record-invalid", "Catalog usage has an invalid controller binding");
        }

        CatalogTarget target = snapshot.target();
        validateManifestIdentity(manifest, target);
        List<CatalogSubject> approvedSubjects = evidenceSubjects(approvedEvidence);
        CatalogEvidenceSet exactApproved = resolveEvidence(snapshot, target, approvedSubjects);
        if (!exactApproved.equals(approvedEvidence)) {
            reject("catalog-evidence-mismatch",
                    "Approved catalog evidence differs from the frozen snapshot");
        }

        List<CatalogSubject> inventory = canonicalInventory(snapshot.availableSubjects());
        String inventoryDigest = ShipDigest.sha256(ShipJson.mapper().writeValueAsBytes(inventory));
        if (!inventoryDigest.equals(usage.inventoryDigest())) {
            reject("catalog-inventory-mismatch",
                    "Catalog usage inventory differs from the frozen snapshot");
        }

        List<CatalogSubject> usedSubjects = validateRoutes(usage.routes());
        validateManifestRoutes(manifest.routes(), usage.routes());
        CatalogEvidenceSet exactUsage = resolveEvidence(snapshot, target, usedSubjects);
        if (!exactUsage.equals(usage.evidence())) {
            reject("catalog-usage-evidence-mismatch",
                    "Catalog usage evidence differs from the frozen snapshot");
        }
        requireApprovedSubset(approvedEvidence, exactUsage);

        List<CatalogSubject> components = usedSubjects.stream()
                .filter(subject -> subject.kind() == CatalogSubject.Kind.COMPONENT)
                .toList();
        if (components.isEmpty()) {
            reject("catalog-usage-record-invalid", "Catalog usage does not contain a classified endpoint");
        }
        List<CatalogComponentModel> exactModels = snapshot.componentModelsFor(components).stream()
                .sorted(Comparator.comparing(model -> model.evidence().subject()))
                .toList();
        if (!exactModels.equals(usage.componentModels())) {
            reject("catalog-component-metadata-mismatch",
                    "Catalog component metadata differs from the frozen snapshot");
        }
        validateEndpoints(usage.routes(), exactModels, components);
        validateRuntimeDependencies(usage.runtimeDependencies(), exactUsage);
    }

    private static void validateManifestIdentity(ArtifactManifest manifest, CatalogTarget target) {
        if (manifest == null
                || manifest.schemaVersion() != ArtifactManifest.SCHEMA_VERSION
                || !target.runtime().equals(manifest.runtime())
                || !target.camelVersion().equals(manifest.camelVersion())
                || !Objects.equals(target.platformVersion(), manifest.platformVersion())
                || !Objects.equals(target.springBootVersion(), manifest.springBootVersion())) {
            reject("catalog-usage-manifest-mismatch",
                    "Artifact manifest differs from the frozen catalog target");
        }
    }

    private static void validateManifestRoutes(List<RouteArtifact> manifestRoutes, List<RouteUsage> usageRoutes) {
        if (manifestRoutes == null
                || manifestRoutes.size() != usageRoutes.size()
                || manifestRoutes.stream().anyMatch(Objects::isNull)) {
            reject("catalog-usage-manifest-mismatch",
                    "Catalog usage does not cover the exact manifest route set");
        }
        Set<String> routeIds = new HashSet<>();
        Set<String> routePaths = new HashSet<>();
        for (RouteArtifact route : manifestRoutes) {
            if (!safeText(route.routeId(), 512)
                    || !safeText(route.path(), 4_096)
                    || !ShipDigest.isSha256(route.digest())
                    || !routeIds.add(route.routeId())
                    || !routePaths.add(route.path())) {
                reject("catalog-usage-manifest-mismatch",
                        "Artifact manifest contains an invalid or duplicate catalog route");
            }
        }
        List<RouteArtifact> expected = manifestRoutes.stream()
                .sorted(Comparator.comparing(RouteArtifact::path).thenComparing(RouteArtifact::routeId))
                .toList();
        for (int index = 0; index < expected.size(); index++) {
            RouteArtifact route = expected.get(index);
            RouteUsage usage = usageRoutes.get(index);
            if (!route.routeId().equals(usage.routeId())
                    || !route.path().equals(usage.path())
                    || !route.digest().equals(usage.routeDigest())) {
                reject("catalog-usage-manifest-mismatch",
                        "Catalog usage route identities differ from the exact artifact manifest");
            }
        }
    }

    /** Exact non-catalog identities that the controller has already authenticated. */
    record UsageBinding(
            String runId,
            String catalogEvidenceDigest,
            String artifactManifestDigest,
            String candidateSnapshotDigest,
            String candidateContentDigest,
            String pomDigest) {

        UsageBinding {
            if (runId == null || !RUN_ID.matcher(runId).matches()) {
                throw new IllegalArgumentException("Catalog usage binding requires a valid run ID");
            }
            requireDigest(catalogEvidenceDigest, "catalog evidence");
            requireDigest(artifactManifestDigest, "artifact manifest");
            requireDigest(candidateSnapshotDigest, "candidate snapshot");
            requireDigest(candidateContentDigest, "candidate content");
            requireDigest(pomDigest, "POM");
        }
    }

    private static List<CatalogSubject> validateRoutes(List<RouteUsage> routes) {
        if (routes == null || routes.isEmpty() || routes.size() > MAX_ROUTES
                || routes.stream().anyMatch(Objects::isNull)) {
            reject("catalog-usage-record-invalid", "Catalog usage routes are missing or excessive");
        }
        Set<String> routeIds = new HashSet<>();
        Set<String> routePaths = new HashSet<>();
        TreeSet<CatalogSubject> used = new TreeSet<>();
        for (RouteUsage route : routes) {
            if (!safeText(route.routeId(), 512)
                    || !safeText(route.path(), 4_096)
                    || !ShipDigest.isSha256(route.routeDigest())
                    || !routeIds.add(route.routeId())
                    || !routePaths.add(route.path())) {
                reject("catalog-usage-record-invalid", "Catalog usage contains an invalid or duplicate route");
            }
            List<CatalogSubject> canonical = canonicalSubjects(
                    route.subjects(), "catalog-usage-record-invalid");
            if (!canonical.equals(route.subjects())
                    || route.endpoints() == null
                    || route.endpoints().isEmpty()
                    || route.endpoints().size() > MAX_ROUTE_ENDPOINTS
                    || route.endpoints().stream().anyMatch(Objects::isNull)) {
                reject("catalog-usage-record-invalid", "Catalog route usage is not canonical and complete");
            }
            used.addAll(canonical);
        }
        if (!routes.equals(routes.stream().sorted(ROUTE_ORDER).toList())) {
            reject("catalog-usage-record-invalid", "Catalog usage routes are not canonically ordered");
        }
        return List.copyOf(used);
    }

    private static void validateEndpoints(
            List<RouteUsage> routes,
            List<CatalogComponentModel> models,
            List<CatalogSubject> expectedComponents) {
        Map<CatalogSubject, CatalogComponentModel> bySubject = new HashMap<>();
        for (CatalogComponentModel model : models) {
            if (model == null || bySubject.put(model.evidence().subject(), model) != null) {
                reject("catalog-component-metadata-mismatch",
                        "Catalog component metadata is duplicated or invalid");
            }
        }
        Set<CatalogSubject> observedComponents = new HashSet<>();
        for (RouteUsage route : routes) {
            for (EndpointUsage endpoint : route.endpoints()) {
                if (endpoint.component() == null
                        || endpoint.componentOptions() == null
                        || endpoint.endpointOptions() == null) {
                    reject("catalog-usage-record-invalid", "Catalog endpoint has an invalid component binding");
                }
            }
            if (!route.endpoints().equals(route.endpoints().stream().sorted(ENDPOINT_ORDER).toList())) {
                reject("catalog-usage-record-invalid", "Catalog endpoint usage is not canonically ordered");
            }
            Set<CatalogSubject> routeSubjects = Set.copyOf(route.subjects());
            for (EndpointUsage endpoint : route.endpoints()) {
                CatalogComponentModel model = bySubject.get(endpoint.component());
                if (endpoint.component().kind() != CatalogSubject.Kind.COMPONENT
                        || model == null
                        || !routeSubjects.contains(endpoint.component())
                        || endpoint.pathParameterCount() < 0
                        || endpoint.pathParameterCount() > 1) {
                    reject("catalog-usage-record-invalid", "Catalog endpoint has an invalid component binding");
                }
                validatePath(endpoint, model);
                validateOptionNames(endpoint.componentOptions(), model, CatalogComponentModel.Scope.COMPONENT);
                validateOptionNames(endpoint.endpointOptions(), model, CatalogComponentModel.Scope.ENDPOINT);
                observedComponents.add(endpoint.component());
            }
        }
        if (!observedComponents.equals(Set.copyOf(expectedComponents))) {
            reject("catalog-usage-record-invalid",
                    "Catalog component subjects do not have an exact endpoint usage");
        }
    }

    private static void validatePath(EndpointUsage endpoint, CatalogComponentModel model) {
        List<CatalogComponentModel.Option> paths = model.options().stream()
                .filter(option -> option.scope() == CatalogComponentModel.Scope.ENDPOINT
                        && option.kind() == CatalogComponentModel.Kind.PATH)
                .toList();
        if (paths.size() > 1
                || endpoint.pathParameterCount() > paths.size()
                || paths.size() == 1 && paths.get(0).required() && endpoint.pathParameterCount() != 1) {
            reject("catalog-usage-record-invalid", "Catalog endpoint path differs from frozen metadata");
        }
    }

    private static void validateOptionNames(
            List<String> names, CatalogComponentModel model, CatalogComponentModel.Scope scope) {
        if (names == null
                || names.size() > MAX_SUBJECTS
                || names.stream().anyMatch(value -> !safeText(value, 128))
                || !names.equals(names.stream().sorted().toList())
                || new HashSet<>(names).size() != names.size()) {
            reject("catalog-usage-record-invalid", "Catalog endpoint options are not canonical");
        }
        Set<String> allowed = new HashSet<>();
        for (CatalogComponentModel.Option option : model.options()) {
            if (option.scope() == scope && option.kind() != CatalogComponentModel.Kind.PATH) {
                allowed.add(option.name());
                if (option.required() && !names.contains(option.name())) {
                    reject("catalog-usage-record-invalid", "Catalog endpoint omits a required frozen option");
                }
            }
        }
        if (!allowed.containsAll(names)) {
            reject("catalog-usage-record-invalid", "Catalog endpoint uses an option outside frozen metadata");
        }
    }

    private static void validateRuntimeDependencies(
            List<RuntimeDependency> dependencies, CatalogEvidenceSet evidence) {
        if (dependencies == null
                || dependencies.isEmpty()
                || dependencies.size() > MAX_RUNTIME_DEPENDENCIES
                || dependencies.stream().anyMatch(Objects::isNull)) {
            reject("catalog-runtime-dependencies-invalid",
                    "Catalog runtime dependency roots are missing or excessive");
        }
        Map<String, RuntimeDependency> actual = new HashMap<>();
        for (RuntimeDependency dependency : dependencies) {
            String key = dependency.groupId() + ':' + dependency.artifactId();
            if (!safeMavenToken(dependency.groupId())
                    || !safeMavenToken(dependency.artifactId())
                    || !exactRelease(dependency.version())
                    || dependency.scope() == null
                    || !Set.of("compile", "runtime").contains(dependency.scope())
                    || actual.putIfAbsent(key, dependency) != null) {
                reject("catalog-runtime-dependencies-invalid",
                        "Catalog runtime dependency roots are unsafe or duplicated");
            }
        }
        List<RuntimeDependency> canonical = dependencies.stream()
                .sorted(Comparator.comparing(RuntimeDependency::coordinate)
                        .thenComparing(RuntimeDependency::scope))
                .toList();
        if (!dependencies.equals(canonical)) {
            reject("catalog-runtime-dependencies-invalid",
                    "Catalog runtime dependency roots are not canonically ordered");
        }
        for (SubjectEvidence subject : evidence.subjects()) {
            if (subject.subject().kind() == CatalogSubject.Kind.EIP) {
                continue;
            }
            RuntimeDependency dependency = actual.get(subject.groupId() + ':' + subject.artifactId());
            if (dependency == null || !subject.artifactVersion().equals(dependency.version())) {
                reject("catalog-runtime-dependencies-invalid",
                        "Catalog runtime dependency roots do not cover exact subject artifacts");
            }
        }
        if ("main".equals(evidence.target().runtime())) {
            Set<String> allowed = evidence.subjects().stream()
                    .filter(subject -> subject.subject().kind() != CatalogSubject.Kind.EIP)
                    .map(subject -> subject.groupId() + ':' + subject.artifactId())
                    .collect(java.util.stream.Collectors.toSet());
            allowed.add("org.apache.camel:camel-main");
            allowed.add("org.apache.camel:camel-yaml-dsl");
            if (!actual.keySet().equals(allowed)) {
                reject("catalog-runtime-dependencies-invalid",
                        "Camel Main dependency roots are not the exact catalog-approved set");
            }
            for (String base : List.of("org.apache.camel:camel-main", "org.apache.camel:camel-yaml-dsl")) {
                RuntimeDependency dependency = actual.get(base);
                if (dependency == null || !evidence.target().camelVersion().equals(dependency.version())) {
                    reject("catalog-runtime-dependencies-invalid",
                            "Camel Main dependency roots omit an exact controller base");
                }
            }
        }
    }

    private static void requireApprovedSubset(CatalogEvidenceSet approved, CatalogEvidenceSet used) {
        if (!approved.target().equals(used.target())
                || !approved.platformCoordinate().equals(used.platformCoordinate())
                || !approved.artifacts().equals(used.artifacts())) {
            reject("catalog-usage-evidence-mismatch",
                    "Catalog usage was derived from different catalog artifact bytes");
        }
        Map<CatalogSubject, SubjectEvidence> available = new HashMap<>();
        approved.subjects().forEach(value -> available.put(value.subject(), value));
        for (SubjectEvidence subject : used.subjects()) {
            if (!subject.equals(available.get(subject.subject()))) {
                reject("catalog-usage-evidence-mismatch",
                        "Catalog usage is not an exact subset of approved evidence");
            }
        }
    }

    private static List<CatalogSubject> evidenceSubjects(CatalogEvidenceSet evidence) {
        if (evidence == null) {
            reject("catalog-evidence-invalid", "Approved catalog evidence is missing");
        }
        return evidence.subjects().stream().map(SubjectEvidence::subject).toList();
    }

    private static List<CatalogSubject> canonicalSubjects(Collection<CatalogSubject> requested, String code) {
        if (requested == null) {
            reject(code, "Catalog subject request is missing");
        }
        TreeSet<CatalogSubject> canonical = new TreeSet<>();
        int count = 0;
        for (CatalogSubject subject : requested) {
            if (++count > MAX_SUBJECTS || subject == null || !canonical.add(subject)) {
                reject(code, "Catalog subject request is invalid, duplicate, or excessive");
            }
        }
        if (canonical.isEmpty()) {
            reject(code, "Catalog subject request is empty");
        }
        return List.copyOf(canonical);
    }

    private static List<CatalogSubject> canonicalInventory(List<CatalogSubject> inventory) {
        if (inventory == null || inventory.isEmpty() || inventory.size() > MAX_INVENTORY_SUBJECTS
                || inventory.stream().anyMatch(Objects::isNull)) {
            reject("catalog-inventory-invalid", "Frozen catalog inventory is missing or excessive");
        }
        List<CatalogSubject> canonical = new ArrayList<>(new TreeSet<>(inventory));
        if (canonical.size() != inventory.size()) {
            reject("catalog-inventory-invalid", "Frozen catalog inventory contains duplicate subjects");
        }
        return List.copyOf(canonical);
    }

    private static boolean safeMavenToken(String value) {
        return value != null
                && SAFE_MAVEN_TOKEN.matcher(value).matches()
                && !value.startsWith(".")
                && !value.endsWith(".")
                && !value.contains("..");
    }

    private static boolean exactRelease(String value) {
        if (!safeMavenToken(value) || TIMESTAMPED_SNAPSHOT.matcher(value).matches()) {
            return false;
        }
        String normalized = value.toUpperCase(java.util.Locale.ROOT);
        return !normalized.contains("SNAPSHOT")
                && !"LATEST".equals(normalized)
                && !"RELEASE".equals(normalized);
    }

    private static boolean safeText(String value, int maximum) {
        return value != null && !value.isBlank() && value.length() <= maximum
                && value.chars().noneMatch(Character::isISOControl);
    }

    private static void requireDigest(String value, String label) {
        if (!ShipDigest.isSha256(value)) {
            throw new IllegalArgumentException("Catalog usage binding requires a valid " + label + " digest");
        }
    }

    private static void reject(String code, String message) {
        throw new ShipControllerException(code, message);
    }
}
