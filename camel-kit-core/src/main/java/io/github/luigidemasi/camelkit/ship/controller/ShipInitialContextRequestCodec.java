package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.context.InitialContext;
import io.github.luigidemasi.camelkit.ship.context.InitialContextException;
import io.github.luigidemasi.camelkit.ship.context.InitialContextRequest;
import io.github.luigidemasi.camelkit.ship.context.InitialContextRequest.DocumentReference;
import io.github.luigidemasi.camelkit.ship.context.InitialContextRequest.UserText;
import io.github.luigidemasi.camelkit.ship.context.InitialContextResolver;

/** Canonical durable shape for the typed request retained before context resolution. */
final class ShipInitialContextRequestCodec {

    private ShipInitialContextRequestCodec() {
    }

    static void validate(InitialContextRequest request) throws IOException {
        if (request == null) {
            throw new IllegalArgumentException("Initial-context request is required");
        }
        try {
            InitialContextResolver.validateRequestSyntax(request);
        } catch (InitialContextException e) {
            throw new IOException("Initial-context request is not durably resolvable: " + e.getMessage(), e);
        }
    }

    static byte[] encode(InitialContextRequest request) throws IOException {
        validate(request);
        String kind = kind(request);
        List<Source> sources = request.sources().stream()
                .map(source -> source instanceof UserText text
                        ? new Source("text", text.value())
                        : new Source("document", ((DocumentReference) source).value()))
                .toList();
        return ShipJson.mapper().writeValueAsBytes(new StoredRequest(kind, sources));
    }

    static InitialContextRequest decode(byte[] encoded) throws IOException {
        if (encoded == null || encoded.length == 0 || encoded.length > ShipJson.MAX_DOCUMENT_BYTES) {
            throw new IOException("Initial-context request bytes are invalid");
        }
        StoredRequest stored = ShipJson.mapper().readValue(encoded, StoredRequest.class);
        List<InitialContextRequest.Source> sources
                = stored.sources().stream().<InitialContextRequest.Source>map(source -> switch (source.kind()) {
                    case "text" -> new UserText(source.value());
                    case "document" -> new DocumentReference(source.value());
                    default -> throw new IllegalArgumentException(
                            "Unknown initial-context source kind: " + source.kind());
                })
                        .toList();
        InitialContextRequest restored = switch (stored.kind()) {
            case "none" -> {
                if (!sources.isEmpty()) {
                    throw new IllegalArgumentException("None context cannot contain sources");
                }
                yield new InitialContextRequest.None();
            }
            case "text" -> new InitialContextRequest.Text(
                    sources.stream().map(UserText.class::cast).toList());
            case "documents" -> new InitialContextRequest.Documents(
                    sources.stream().map(DocumentReference.class::cast).toList());
            case "composite" -> new InitialContextRequest.Composite(sources);
            default -> throw new IllegalArgumentException(
                    "Unknown initial-context request kind: " + stored.kind());
        };
        byte[] canonical = encode(restored);
        if (!java.util.Arrays.equals(encoded, canonical)) {
            throw new IOException("Initial-context request is not canonical");
        }
        return restored;
    }

    private static String kind(InitialContextRequest request) {
        String kind = request instanceof InitialContextRequest.None ? "none"
                : request instanceof InitialContextRequest.Text ? "text"
                : request instanceof InitialContextRequest.Documents ? "documents"
                : request instanceof InitialContextRequest.Composite ? "composite" : null;
        if (kind == null) {
            throw new IllegalArgumentException("Unknown initial-context request type");
        }
        return kind;
    }

    private record StoredRequest(String kind, List<Source> sources) {

        private StoredRequest {
            if (kind == null || sources == null || sources.size() > InitialContext.MAX_SOURCES) {
                throw new IllegalArgumentException("Invalid stored initial-context request");
            }
            sources = List.copyOf(sources);
        }
    }

    private record Source(String kind, String value) {

        private Source {
            if (kind == null || value == null) {
                throw new IllegalArgumentException("Invalid stored initial-context source");
            }
        }
    }
}
