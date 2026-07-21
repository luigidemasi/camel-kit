package io.github.luigidemasi.camelkit.ship;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Content-address validation shared by Ship's controller-neutral wire records. */
public final class ShipDigest {

    private ShipDigest() {
    }

    public static String sha256(byte[] value) {
        return "sha256:" + HexFormat.of().formatHex(messageDigest().digest(value));
    }

    public static boolean isSha256(String value) {
        return value != null && value.matches("sha256:[0-9a-f]{64}");
    }

    public static boolean isHmacSha256(String value) {
        return value != null && value.matches("hmac-sha256:[0-9a-f]{64}");
    }

    private static MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
