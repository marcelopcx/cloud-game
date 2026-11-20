package com.cloudgame.station.infrastructure;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Contrato binario con {@code libgameinput.so}: {@code struct Look} guarda
 * dx en el byte 4 y dy en el 8 (int32 LE), y los deltas se acumulan.
 */

class LookFeedTest {

    @Test
    void accumulatesDeltasWhereLibgameinputReads() throws Exception {
        LookFeed feed = new LookFeed();
        feed.open();
        try {
            feed.add(3, -2);
            feed.add(4, 1);
            ByteBuffer page = ByteBuffer.wrap(Files.readAllBytes(LookFeed.path())).order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(7, page.getInt(4));
            assertEquals(-1, page.getInt(8));
        } finally {
            feed.close();
        }
    }
}
