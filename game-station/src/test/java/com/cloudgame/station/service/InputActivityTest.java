package com.cloudgame.station.service;

import com.cloudgame.station.infrastructure.DatagramParser;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InputSink#active}: un snapshot en reposo (lo que el cliente manda a
 * 250 Hz sin input) no cuenta como actividad; tecla, botón, stick o mouse sí.
 */
class InputActivityTest {

    private static DatagramParser.Snapshot snap(int buttons, Set<Integer> keys, DatagramParser.PadSnapshot pad) {
        return new DatagramParser.Snapshot(1, 0, true, true, 100, 100, buttons, 0,
                true, keys, true, pad == null ? Map.of() : Map.of(0, pad));
    }

    private static DatagramParser.PadSnapshot pad(int buttons, int... axes) {
        int[] six = new int[6];
        System.arraycopy(axes, 0, six, 0, axes.length);
        return new DatagramParser.PadSnapshot(0, buttons, six);
    }

    @Test
    void restingSnapshotIsNotActivity() {
        assertFalse(InputSink.active(snap(0, Set.of(), pad(0)), 0, 0, 0, false));
        assertFalse(InputSink.active(snap(0, Set.of(), pad(0, 300, -200)), 0, 0, 0, false));
    }

    @Test
    void heldControlsAndMotionAreActivity() {
        assertTrue(InputSink.active(snap(0, Set.of(30), null), 0, 0, 0, false));
        assertTrue(InputSink.active(snap(1, Set.of(), null), 0, 0, 0, false));
        assertTrue(InputSink.active(snap(0, Set.of(), pad(1)), 0, 0, 0, false));
        assertTrue(InputSink.active(snap(0, Set.of(), pad(0, 0, 20000)), 0, 0, 0, false));
        assertTrue(InputSink.active(snap(0, Set.of(), null), 3, 0, 0, false));
        assertTrue(InputSink.active(snap(0, Set.of(), null), 0, 0, 1, false));
        assertTrue(InputSink.active(snap(0, Set.of(), null), 0, 0, 0, true));
    }
}
