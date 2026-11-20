package com.cloudgame.station.infrastructure;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Contrato binario con {@code libgameinput.so}: el slot 1 se lee en LE en
 * los mismos offsets que {@code pad_off_*} de {@code gameinput.c}
 * (magic en 64 + slot * 32, botones +4, ejes int16 +8, activo +20).
 */

class PadFeedTest {

    @Test
    void writesTheLayoutLibgameinputReads() throws Exception {
        PadFeed feed = new PadFeed();
        feed.open();
        try {
            feed.publishSlot(1, 0x10201, new int[]{-32767, 100, 0, 0, 32767, 5});
            ByteBuffer page = ByteBuffer.wrap(Files.readAllBytes(LookFeed.path())).order(ByteOrder.LITTLE_ENDIAN);
            int base = 64 + 32;
            assertEquals(0x50414430, page.getInt(base));
            assertEquals(0x10201, page.getInt(base + 4));
            assertEquals(-32767, page.getShort(base + 8));
            assertEquals(100, page.getShort(base + 10));
            assertEquals(32767, page.getShort(base + 16));
            assertEquals(1, page.getShort(base + 20));
        } finally {
            feed.close();
        }
    }
}
