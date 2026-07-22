package io.github.luigidemasi.camelkit.ship.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import io.github.luigidemasi.camelkit.ship.controller.ShipBlobStore.BlobReference;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DesignChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DesignResponse;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DiscoveryAnswer;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DiscoveryChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DocumentConsentChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DocumentConsentResponse;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.PlanChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.PlanResponse;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.RemoteProviderConsentChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.RemoteProviderConsentResponse;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.WaiverChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.WaiverResponse;

/**
 * Writes locally integrity-bound interaction-bundle revisions. A valid local MAC proves what this controller recorded;
 * record strings alone do not authenticate a principal or provide non-repudiation.
 */
public final class ShipInteractionBundleService {

    private static final String BLOB_KIND = "interaction-bundle";

    private final ShipInteractionSigner signer;

    ShipInteractionBundleService(ShipInteractionSigner signer) {
        this.signer = Objects.requireNonNull(signer, "interaction signer");
    }

    public BlobReference create(ShipBlobStore blobs, String runId, String contextDigest) throws IOException {
        requireRunBindings(blobs, runId);
        return write(blobs, new ShipInteractionBundle(
                ShipInteractionBundle.SCHEMA_VERSION, runId, 1, contextDigest, List.of()));
    }

    public BlobReference amend(ShipBlobStore blobs, BlobReference current, String contextDigest)
            throws IOException {
        ShipInteractionBundle previous = read(blobs, current);
        return write(blobs, new ShipInteractionBundle(
                ShipInteractionBundle.SCHEMA_VERSION,
                previous.runId(),
                Math.incrementExact(previous.contextRevision()),
                contextDigest,
                List.of()));
    }

    public BlobReference record(ShipBlobStore blobs, BlobReference current, DiscoveryChallenge challenge)
            throws IOException {
        return append(blobs, current, ordinal -> ShipInteractionBundle.Exchange.discovery(ordinal, challenge));
    }

    public BlobReference record(ShipBlobStore blobs, BlobReference current, DiscoveryAnswer answer)
            throws IOException {
        return complete(blobs, current, exchange -> exchange.answer(answer));
    }

    public BlobReference record(
            ShipBlobStore blobs, BlobReference current, DocumentConsentChallenge challenge)
            throws IOException {
        return append(
                blobs, current, ordinal -> ShipInteractionBundle.Exchange.documentConsent(ordinal, challenge));
    }

    public BlobReference record(
            ShipBlobStore blobs, BlobReference current, DocumentConsentResponse response)
            throws IOException {
        return complete(blobs, current, exchange -> exchange.respond(response));
    }

    public BlobReference record(
            ShipBlobStore blobs, BlobReference current, RemoteProviderConsentChallenge challenge)
            throws IOException {
        return append(
                blobs,
                current,
                ordinal -> ShipInteractionBundle.Exchange.remoteProviderConsent(ordinal, challenge));
    }

    public BlobReference record(
            ShipBlobStore blobs, BlobReference current, RemoteProviderConsentResponse response)
            throws IOException {
        return complete(blobs, current, exchange -> exchange.respond(response));
    }

    public BlobReference record(ShipBlobStore blobs, BlobReference current, DesignChallenge challenge)
            throws IOException {
        return append(blobs, current, ordinal -> ShipInteractionBundle.Exchange.design(ordinal, challenge));
    }

    public BlobReference record(ShipBlobStore blobs, BlobReference current, DesignResponse response)
            throws IOException {
        return complete(blobs, current, exchange -> exchange.respond(response));
    }

    public BlobReference record(ShipBlobStore blobs, BlobReference current, PlanChallenge challenge)
            throws IOException {
        return append(blobs, current, ordinal -> ShipInteractionBundle.Exchange.plan(ordinal, challenge));
    }

    public BlobReference record(ShipBlobStore blobs, BlobReference current, PlanResponse response)
            throws IOException {
        return complete(blobs, current, exchange -> exchange.respond(response));
    }

    public BlobReference record(ShipBlobStore blobs, BlobReference current, WaiverChallenge challenge)
            throws IOException {
        return append(blobs, current, ordinal -> ShipInteractionBundle.Exchange.waiver(ordinal, challenge));
    }

    public BlobReference record(ShipBlobStore blobs, BlobReference current, WaiverResponse response)
            throws IOException {
        return complete(blobs, current, exchange -> exchange.respond(response));
    }

    public ShipInteractionBundle read(ShipBlobStore blobs, BlobReference reference) throws IOException {
        return readBundle(blobs, reference);
    }

    ShipInteractionSigner signer() {
        return signer;
    }

    void preflightRecord(
            ShipBlobStore blobs, BlobReference current, DiscoveryChallenge challenge)
            throws IOException {
        ShipInteractionBundle bundle = appendable(blobs, current);
        List<ShipInteractionBundle.Exchange> exchanges = mutable(bundle);
        exchanges.add(ShipInteractionBundle.Exchange.discovery(
                exchanges.size() + 1, challenge));
        ShipInteractionBundle candidate = copy(bundle, exchanges);
        verifyMacs(candidate);
        encoded(candidate);
    }

    void preflightRecord(
            ShipBlobStore blobs,
            BlobReference current,
            DiscoveryChallenge challenge,
            DiscoveryAnswer answer)
            throws IOException {
        ShipInteractionBundle bundle = appendable(blobs, current);
        List<ShipInteractionBundle.Exchange> exchanges = mutable(bundle);
        exchanges.add(ShipInteractionBundle.Exchange.discovery(
                exchanges.size() + 1, challenge).answer(answer));
        ShipInteractionBundle candidate = copy(bundle, exchanges);
        verifyMacs(candidate);
        encoded(candidate);
    }

    void preflightRecord(
            ShipBlobStore blobs, BlobReference current, DiscoveryAnswer answer)
            throws IOException {
        ShipInteractionBundle bundle = readBundle(blobs, current);
        List<ShipInteractionBundle.Exchange> exchanges = mutable(bundle);
        int last = requireLast(exchanges);
        exchanges.set(last, exchanges.get(last).answer(answer));
        ShipInteractionBundle candidate = copy(bundle, exchanges);
        verifyMacs(candidate);
        encoded(candidate);
    }

    private ShipInteractionBundle readBundle(ShipBlobStore blobs, BlobReference reference)
            throws IOException {
        Objects.requireNonNull(blobs, "blobs");
        if (reference == null
                || !BLOB_KIND.equals(reference.kind())
                || reference.byteSize() > ShipInteractionBundle.MAX_SERIALIZED_BYTES) {
            throw new IOException("Expected a protected Ship interaction-bundle reference");
        }
        byte[] content = blobs.readBytes(reference, ShipInteractionBundle.MAX_SERIALIZED_BYTES);
        ShipInteractionBundle bundle = ShipJson.mapper().readValue(content, ShipInteractionBundle.class);
        requireRunBindings(blobs, bundle.runId());
        verifyMacs(bundle);
        return bundle;
    }

    private BlobReference append(
            ShipBlobStore blobs,
            BlobReference current,
            java.util.function.IntFunction<ShipInteractionBundle.Exchange> exchangeFactory)
            throws IOException {
        ShipInteractionBundle bundle = appendable(blobs, current);
        List<ShipInteractionBundle.Exchange> exchanges = mutable(bundle);
        exchanges.add(exchangeFactory.apply(exchanges.size() + 1));
        return write(blobs, copy(bundle, exchanges));
    }

    private ShipInteractionBundle appendable(
            ShipBlobStore blobs, BlobReference current)
            throws IOException {
        ShipInteractionBundle bundle = readBundle(blobs, current);
        requireNoPending(bundle);
        if (bundle.exchanges().size() >= ShipInteractionBundle.MAX_EXCHANGES) {
            throw new IOException(
                    "Interaction bundle reached its " + ShipInteractionBundle.MAX_EXCHANGES
                                  + " exchange limit");
        }
        return bundle;
    }

    private BlobReference complete(
            ShipBlobStore blobs,
            BlobReference current,
            Function<ShipInteractionBundle.Exchange, ShipInteractionBundle.Exchange> completion)
            throws IOException {
        ShipInteractionBundle bundle = readBundle(blobs, current);
        List<ShipInteractionBundle.Exchange> exchanges = mutable(bundle);
        int last = requireLast(exchanges);
        exchanges.set(last, completion.apply(exchanges.get(last)));
        return write(blobs, copy(bundle, exchanges));
    }

    private BlobReference write(ShipBlobStore blobs, ShipInteractionBundle bundle) throws IOException {
        Objects.requireNonNull(blobs, "blobs");
        requireRunBindings(blobs, bundle.runId());
        verifyMacs(bundle);
        return blobs.writeBytes(BLOB_KIND, encoded(bundle));
    }

    private void requireRunBindings(ShipBlobStore blobs, String runId) throws IOException {
        Objects.requireNonNull(blobs, "blobs");
        if (!signer.belongsTo(blobs, runId)) {
            throw new IOException("Ship interaction signer, blob store, and declaration must belong to one run");
        }
    }

    private void verifyMacs(ShipInteractionBundle bundle) throws IOException {
        for (ShipInteractionBundle.Exchange exchange : bundle.exchanges()) {
            boolean valid;
            if (exchange.discovery() != null) {
                DiscoveryChallenge challenge = exchange.discovery().challenge();
                valid = signer.verify(challenge.mac(), Interaction.discoveryChallengeMacFields(
                        challenge.runId(),
                        challenge.ledgerRevision(),
                        challenge.questionId(),
                        challenge.openItemId(),
                        challenge.prompt(),
                        challenge.options(),
                        challenge.recommendation(),
                        challenge.nonce()));
                DiscoveryAnswer answer = exchange.discovery().answer();
                valid &= answer == null
                        || signer.verify(answer.mac(), Interaction.discoveryAnswerMacFields(answer));
            } else if (exchange.documentConsent() != null) {
                DocumentConsentChallenge challenge = exchange.documentConsent().challenge();
                valid = signer.verify(
                        challenge.mac(), Interaction.documentConsentChallengeMacFields(challenge));
                DocumentConsentResponse response = exchange.documentConsent().response();
                valid &= response == null
                        || signer.verify(
                                response.mac(), Interaction.documentConsentResponseMacFields(response));
            } else if (exchange.remoteProviderConsent() != null) {
                RemoteProviderConsentChallenge challenge = exchange.remoteProviderConsent().challenge();
                valid = signer.verify(
                        challenge.mac(), Interaction.remoteProviderConsentChallengeMacFields(challenge));
                RemoteProviderConsentResponse response = exchange.remoteProviderConsent().response();
                valid &= response == null
                        || signer.verify(
                                response.mac(),
                                Interaction.remoteProviderConsentResponseMacFields(response));
            } else if (exchange.design() != null) {
                DesignChallenge challenge = exchange.design().challenge();
                valid = signer.verify(challenge.mac(), Interaction.designChallengeMacFields(challenge));
                DesignResponse response = exchange.design().response();
                valid &= response == null
                        || signer.verify(response.mac(), Interaction.designResponseMacFields(response));
            } else if (exchange.plan() != null) {
                PlanChallenge challenge = exchange.plan().challenge();
                valid = signer.verify(challenge.mac(), Interaction.planChallengeMacFields(challenge));
                PlanResponse response = exchange.plan().response();
                valid &= response == null
                        || signer.verify(response.mac(), Interaction.planResponseMacFields(response));
            } else {
                WaiverChallenge challenge = exchange.waiver().challenge();
                valid = signer.verify(challenge.mac(), Interaction.waiverChallengeMacFields(challenge));
                WaiverResponse response = exchange.waiver().response();
                valid &= response == null
                        || signer.verify(response.mac(), Interaction.waiverResponseMacFields(response));
            }
            if (!valid) {
                throw new IOException("Ship interaction bundle contains a record with an invalid local MAC");
            }
        }
    }

    static byte[] encoded(ShipInteractionBundle bundle) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ShipJson.mapper().writeValue(
                new BoundedOutputStream(bytes, ShipInteractionBundle.MAX_SERIALIZED_BYTES), bundle);
        return bytes.toByteArray();
    }

    private static ShipInteractionBundle copy(
            ShipInteractionBundle bundle, List<ShipInteractionBundle.Exchange> exchanges) {
        return new ShipInteractionBundle(
                bundle.schemaVersion(),
                bundle.runId(),
                bundle.contextRevision(),
                bundle.contextDigest(),
                exchanges);
    }

    private static List<ShipInteractionBundle.Exchange> mutable(ShipInteractionBundle bundle) {
        return new ArrayList<>(bundle.exchanges());
    }

    private static void requireNoPending(ShipInteractionBundle bundle) {
        List<ShipInteractionBundle.Exchange> exchanges = bundle.exchanges();
        if (!exchanges.isEmpty() && exchanges.get(exchanges.size() - 1).pending()) {
            throw new IllegalStateException("The current interaction challenge still needs a response");
        }
    }

    private static int requireLast(List<ShipInteractionBundle.Exchange> exchanges) {
        if (exchanges.isEmpty()) {
            throw new IllegalStateException("No interaction challenge is awaiting a response");
        }
        return exchanges.size() - 1;
    }

    private static final class BoundedOutputStream extends OutputStream {

        private final ByteArrayOutputStream delegate;
        private final int maximum;
        private int count;

        private BoundedOutputStream(ByteArrayOutputStream delegate, int maximum) {
            this.delegate = delegate;
            this.maximum = maximum;
        }

        @Override
        public void write(int value) throws IOException {
            reserve(1);
            delegate.write(value);
        }

        @Override
        public void write(byte[] value, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, value.length);
            reserve(length);
            delegate.write(value, offset, length);
        }

        private void reserve(int length) throws IOException {
            try {
                count = Math.addExact(count, length);
            } catch (ArithmeticException e) {
                throw new IOException("Ship interaction bundle size overflowed", e);
            }
            if (count > maximum) {
                throw new IOException("Ship interaction bundle exceeds " + maximum + " bytes");
            }
        }
    }
}
