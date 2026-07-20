package io.github.luigidemasi.camelkit.ship.catalog;

import java.util.Objects;
import java.util.regex.Pattern;

/** A named Camel catalog item that a design or generated route intends to use. */
public record CatalogSubject(Kind kind, String name) implements Comparable<CatalogSubject> {

    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9+._-]{0,127}");

    public CatalogSubject {
        Objects.requireNonNull(kind, "kind must not be null");
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Catalog subject name is invalid");
        }
    }

    @Override
    public int compareTo(CatalogSubject other) {
        int kindOrder = kind.compareTo(other.kind);
        return kindOrder != 0 ? kindOrder : name.compareTo(other.name);
    }

    public enum Kind {
        COMPONENT,
        EIP,
        DATAFORMAT,
        LANGUAGE
    }
}
