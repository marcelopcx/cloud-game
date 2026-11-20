package com.cloudgame.loader.service;

import com.cloudgame.loader.config.LoaderProperties;
import com.cloudgame.loader.exception.LoaderException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Reparto de slots: el libre más bajo, huecos que se reutilizan,
 * {@code CAPACITY_EXCEEDED} al llenarse y puerto/display derivados.
 */
class SlotAllocatorTest {

    private final SlotAllocator slots = new SlotAllocator(props());

    private static LoaderProperties props() {
        LoaderProperties p = new LoaderProperties();
        p.setMaxContainers(3);
        p.setBasePort(8200);
        p.setBaseDisplay(200);
        return p;
    }

    @Test
    void takesTheLowestFreeSlot() {
        assertEquals(0, slots.allocate(List.of()));
        assertEquals(1, slots.allocate(List.of(0)));
    }

    @Test
    void reusesGaps() {
        assertEquals(1, slots.allocate(List.of(0, 2)));
    }

    @Test
    void failsWhenFull() {
        LoaderException ex = assertThrows(LoaderException.class, () -> slots.allocate(List.of(0, 1, 2)));
        assertEquals("CAPACITY_EXCEEDED", ex.code());
        assertEquals(409, ex.status());
    }

    @Test
    void derivesPortAndDisplay() {
        assertEquals(8202, slots.port(2));
        assertEquals(":202", slots.display(2));
    }
}
