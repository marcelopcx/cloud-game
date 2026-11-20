package com.cloudgame.station.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code STATION_STREAM_SIZE}: vacío o inválido deja el stream igual al juego;
 * un valor válido queda par (I420) y nunca por encima de {@code STATION_SIZE}.
 */
class StationPropertiesTest {

    private static StationProperties props(String size, String streamSize) {
        StationProperties props = new StationProperties();
        props.setSize(size);
        props.setStreamSize(streamSize);
        return props;
    }

    @Test
    void emptyStreamSizeKeepsGameSize() {
        StationProperties props = props("1280x720", "");
        assertEquals(1280, props.streamWidth());
        assertEquals(720, props.streamHeight());
        assertFalse(props.scalesStream());
    }

    @Test
    void smallerStreamSizeScales() {
        StationProperties props = props("1280x720", "854x480");
        assertEquals(854, props.streamWidth());
        assertEquals(480, props.streamHeight());
        assertTrue(props.scalesStream());
    }

    @Test
    void oddDimensionsRoundDownToEven() {
        StationProperties props = props("1280x720", "853x479");
        assertEquals(852, props.streamWidth());
        assertEquals(478, props.streamHeight());
    }

    @Test
    void streamNeverExceedsGame() {
        StationProperties props = props("1280x720", "1920x1080");
        assertEquals(1280, props.streamWidth());
        assertEquals(720, props.streamHeight());
        assertFalse(props.scalesStream());
    }

    @Test
    void garbageFallsBackToGameSize() {
        StationProperties props = props("1280x720", "hd");
        assertEquals(1280, props.streamWidth());
        assertEquals(720, props.streamHeight());
    }

    @Test
    void unknownScalerFallsBackToBicubic() {
        StationProperties props = props("1280x720", "960x540");
        props.setStreamScaler("lanczos");
        assertEquals("lanczos", props.streamScalerName());
        props.setStreamScaler("scale=1:1,drawtext");
        assertEquals("bicubic", props.streamScalerName());
    }
}
