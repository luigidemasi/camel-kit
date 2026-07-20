package io.github.luigidemasi.camelkit.ship.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.luigidemasi.camelkit.ship.catalog.CatalogEvidenceSet.SubjectEvidence;

/**
 * Bounded, descriptive option metadata. This caller-constructible record is not proof of provenance; only an opaque
 * {@link ShipCatalogService.Snapshot} binds models to the artifact bytes it verified and froze.
 */
public record CatalogComponentModel(
        SubjectEvidence evidence,
        String syntax,
        boolean lenientProperties,
        List<Option> options) {

    static final int MAX_OPTIONS = 4_096;
    static final int MAX_ENUM_VALUES = 4_096;

    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9+._-]{0,127}");
    private static final Comparator<Option> OPTION_ORDER = Comparator.comparing(Option::scope)
            .thenComparing(Option::kind)
            .thenComparingInt(Option::index)
            .thenComparing(Option::name);

    public CatalogComponentModel {
        Objects.requireNonNull(evidence, "evidence must not be null");
        if (evidence.subject().kind() != CatalogSubject.Kind.COMPONENT) {
            throw new IllegalArgumentException("Component metadata requires component evidence");
        }
        if (!safeText(syntax, 1_024) || !syntax.startsWith(evidence.subject().name() + ':')) {
            throw new IllegalArgumentException("Component syntax is invalid");
        }
        options = boundedCopy(options);
        Set<String> normalized = new HashSet<>();
        for (int index = 0; index < options.size(); index++) {
            Option option = options.get(index);
            if (index > 0 && OPTION_ORDER.compare(options.get(index - 1), option) >= 0) {
                throw new IllegalArgumentException("Component options are not canonical and unique");
            }
            String key = option.scope() + ":" + option.name().replace("-", "").replace("_", "")
                    .toLowerCase(Locale.ROOT);
            if (!normalized.add(key)) {
                throw new IllegalArgumentException("Component options have ambiguous normalized names");
            }
        }
    }

    @Override
    public String toString() {
        return "CatalogComponentModel[evidence=" + evidence
               + ", syntax=<redacted>, lenientProperties=" + lenientProperties
               + ", options=<" + options.size() + " entries>]";
    }

    public record Option(
            String name,
            Scope scope,
            Kind kind,
            int index,
            String type,
            String javaType,
            boolean required,
            boolean multiValue,
            String prefix,
            String optionalPrefix,
            List<String> enumValues) {

        public Option {
            if (name == null || !SAFE_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Component option name is invalid");
            }
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(kind, "kind must not be null");
            if (scope == Scope.COMPONENT ? kind != Kind.PROPERTY : kind == Kind.PROPERTY) {
                throw new IllegalArgumentException("Component option kind is inconsistent with its scope");
            }
            if (index < 0 || !safeText(type, 512) || !safeText(javaType, 512)) {
                throw new IllegalArgumentException("Component option metadata is invalid");
            }
            if (prefix != null && !SAFE_NAME.matcher(prefix).matches()) {
                throw new IllegalArgumentException("Component option prefix is invalid");
            }
            if (optionalPrefix != null && !SAFE_NAME.matcher(optionalPrefix).matches()) {
                throw new IllegalArgumentException("Component option optional prefix is invalid");
            }
            enumValues = boundedEnums(enumValues);
        }

        @Override
        public String toString() {
            return "Option[name=" + name
                   + ", scope=" + scope
                   + ", kind=" + kind
                   + ", index=" + index
                   + ", type=" + type
                   + ", javaType=" + javaType
                   + ", required=" + required
                   + ", multiValue=" + multiValue
                   + ", prefix=" + prefix
                   + ", optionalPrefix=" + optionalPrefix
                   + ", enumValues=<" + enumValues.size() + " entries>]";
        }
    }

    public enum Scope {
        COMPONENT,
        ENDPOINT
    }

    public enum Kind {
        PROPERTY,
        PATH,
        PARAMETER
    }

    private static List<Option> boundedCopy(List<Option> values) {
        Objects.requireNonNull(values, "options must not be null");
        List<Option> copy = new ArrayList<>();
        for (Option value : values) {
            if (copy.size() == MAX_OPTIONS) {
                throw new IllegalArgumentException("Component options exceed their fixed bound");
            }
            copy.add(Objects.requireNonNull(value, "options must not contain null"));
        }
        return List.copyOf(copy);
    }

    private static List<String> boundedEnums(List<String> values) {
        Objects.requireNonNull(values, "enumValues must not be null");
        List<String> copy = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (String value : values) {
            if (copy.size() == MAX_ENUM_VALUES || !safeText(value, 512) || !unique.add(value)) {
                throw new IllegalArgumentException("Component option enum is invalid or excessive");
            }
            copy.add(value);
        }
        return List.copyOf(copy);
    }

    private static boolean safeText(String value, int maximum) {
        return value != null && !value.isBlank() && value.length() <= maximum
                && value.chars().noneMatch(Character::isISOControl);
    }
}
