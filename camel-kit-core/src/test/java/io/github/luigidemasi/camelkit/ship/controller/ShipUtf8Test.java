package io.github.luigidemasi.camelkit.ship.controller;

import java.nio.charset.CharacterCodingException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShipUtf8Test {

    @Test
    void roundTripsUnicodeAndRejectsMalformedInput() throws Exception {
        String value = "Camel € 🚀";
        byte[] encoded = ShipUtf8.encode(value);

        assertEquals(value, ShipUtf8.decode(encoded));
        assertArrayEquals(new byte[]{'C', 'a', 'm', 'e', 'l', ' '}, ShipUtf8.encode("Camel "));
        assertThrows(CharacterCodingException.class, () -> ShipUtf8.encode("unpaired-\ud800"));
        assertThrows(CharacterCodingException.class, () -> ShipUtf8.decode(new byte[]{(byte) 0xc3, 0x28}));
    }
}
