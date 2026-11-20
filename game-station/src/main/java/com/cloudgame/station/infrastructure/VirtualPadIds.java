package com.cloudgame.station.infrastructure;

/**
 * IDs USB del pad virtual Cloud Game (no usar {@code 045e:028e}: colisiona con Xbox
 * físicos en el host cuando {@code /dev/input} está montado en el contenedor).
 */
public final class VirtualPadIds {

    /** {@code "At"} en hex — vendor propio. */
    public static final int VENDOR = 0x4174;
    /** Producto base; slot {@code n} usa {@code BASE + n}. */
    public static final int PRODUCT_BASE = 0xC000;
    public static final int VERSION = 0x0100;

    private VirtualPadIds() {
    }

    public static int productForSlot(int slot) {
        return PRODUCT_BASE + Math.max(0, Math.min(3, slot));
    }
}
