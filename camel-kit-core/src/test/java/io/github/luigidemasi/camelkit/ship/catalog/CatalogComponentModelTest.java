package io.github.luigidemasi.camelkit.ship.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import io.github.luigidemasi.camelkit.ship.catalog.CatalogComponentModel.Kind;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogComponentModel.Option;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogComponentModel.Scope;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogEvidenceSet.SubjectEvidence;
import io.github.luigidemasi.camelkit.ship.resolver.MavenCoordinate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CatalogComponentModelTest {

    @Test
    void modelIsDeeplyImmutableAndRequiresCanonicalOptions() {
        List<Option> source = new ArrayList<>(List.of(option("delay", 0)));
        CatalogComponentModel model = new CatalogComponentModel(evidence(), "timer:timerName", false, source);
        source.clear();

        assertEquals(1, model.options().size());
        assertThrows(UnsupportedOperationException.class, () -> model.options().clear());
        assertThrows(IllegalArgumentException.class, () -> new CatalogComponentModel(
                evidence(), "timer:timerName", false, List.of(option("z", 1), option("a", 0))));
    }

    @Test
    void normalizedOptionNamesAreUniqueWithinScope() {
        assertThrows(IllegalArgumentException.class, () -> new CatalogComponentModel(
                evidence(), "timer:timerName", false,
                List.of(option("foo-bar", 0), option("foo_bar", 1))));
    }

    @Test
    void scopeAndKindMustAgree() {
        assertThrows(IllegalArgumentException.class, () -> new Option(
                "delay", Scope.COMPONENT, Kind.PARAMETER, 0, "integer", "long",
                false, false, null, null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Option(
                "delay", Scope.ENDPOINT, Kind.PROPERTY, 0, "integer", "long",
                false, false, null, null, List.of()));
    }

    @Test
    void multiValueAndOptionalPrefixesRemainDistinct() {
        Option option = new Option(
                "additional", Scope.ENDPOINT, Kind.PARAMETER, 0, "object", "java.util.Map",
                false, true, "additional.", "consumer.", List.of());

        assertEquals("additional.", option.prefix());
        assertEquals("consumer.", option.optionalPrefix());
    }

    @Test
    void enumAndOptionQuotasAcceptTheLimitAndRejectOneOver() {
        List<String> enumLimit = IntStream.range(0, CatalogComponentModel.MAX_ENUM_VALUES)
                .mapToObj(index -> "v" + index).toList();
        assertEquals(CatalogComponentModel.MAX_ENUM_VALUES, new Option(
                "delay", Scope.ENDPOINT, Kind.PARAMETER, 0, "integer", "long",
                false, false, null, null, enumLimit).enumValues().size());
        List<String> excessiveEnum = new ArrayList<>(enumLimit);
        excessiveEnum.add("extra");
        assertThrows(IllegalArgumentException.class, () -> new Option(
                "delay", Scope.ENDPOINT, Kind.PARAMETER, 0, "integer", "long",
                false, false, null, null, excessiveEnum));

        List<Option> optionLimit = IntStream.range(0, CatalogComponentModel.MAX_OPTIONS)
                .mapToObj(index -> option("option" + index, index))
                .toList();
        assertEquals(CatalogComponentModel.MAX_OPTIONS,
                new CatalogComponentModel(evidence(), "timer:timerName", false, optionLimit).options().size());
        List<Option> excessiveOptions = new ArrayList<>(optionLimit);
        excessiveOptions.add(option("extra", CatalogComponentModel.MAX_OPTIONS));
        assertThrows(IllegalArgumentException.class,
                () -> new CatalogComponentModel(evidence(), "timer:timerName", false, excessiveOptions));
    }

    @Test
    void syntaxAcceptsTheLimitAndRejectsOneOver() {
        String atLimit = "timer:" + "a".repeat(1_018);
        assertEquals(1_024,
                new CatalogComponentModel(evidence(), atLimit, false, List.of()).syntax().length());
        assertThrows(IllegalArgumentException.class,
                () -> new CatalogComponentModel(evidence(), atLimit + 'a', false, List.of()));
    }

    @Test
    void diagnosticStringsDoNotDumpSyntaxOptionsOrEnumValues() {
        Option option = new Option(
                "delay", Scope.ENDPOINT, Kind.PARAMETER, 0, "string", "java.lang.String",
                false, false, null, null, List.of("sensitive-enum-value"));
        CatalogComponentModel model = new CatalogComponentModel(
                evidence(), "timer:sensitive-syntax", false, List.of(option));

        assertFalse(model.toString().contains("sensitive-syntax"));
        assertFalse(option.toString().contains("sensitive-enum-value"));
        assertTrue(model.toString().contains("options=<1 entries>"));
    }

    private static Option option(String name, int index) {
        return new Option(
                name, Scope.ENDPOINT, Kind.PARAMETER, index, "integer", "long",
                false, false, null, null, List.of());
    }

    private static SubjectEvidence evidence() {
        MavenCoordinate catalog = MavenCoordinate.jar("org.apache.camel", "camel-catalog", "4.21.0");
        return new SubjectEvidence(
                new CatalogSubject(CatalogSubject.Kind.COMPONENT, "timer"), catalog,
                "sha256:" + "a".repeat(64),
                "org/apache/camel/catalog/components/timer.json",
                "sha256:" + "b".repeat(64),
                "org.apache.camel", "camel-timer", "4.21.0", false);
    }
}
