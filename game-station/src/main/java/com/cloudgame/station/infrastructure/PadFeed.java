package com.cloudgame.station.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Estado de hasta 4 gamepads remotos en {@code /tmp/game-input}. Lo lee
 * {@code libgameinput.so} cuando el juego no debe usar pads {@code uinput}
 * (p. ej. SuperTuxKart).
 */
public final class PadFeed {

    private static final Logger log = LoggerFactory.getLogger(PadFeed.class);
    private static final int MAGIC = 0x50414430; // "PAD0"
    private static final int SLOT_STRIDE = 32;
    private static final int SLOT0 = 64;
    private static final int MAX_SLOTS = 4;

    private MappedByteBuffer buffer;
    private boolean logged;

    private static int offMagic(int slot) {
        return SLOT0 + slot * SLOT_STRIDE;
    }

    private static int offButtons(int slot) {
        return offMagic(slot) + 4;
    }

    private static int offAxes(int slot) {
        return offMagic(slot) + 8;
    }

    private static int offActive(int slot) {
        return offMagic(slot) + 20;
    }

    public void open() {
        LookFeed.prepare();
        try (RandomAccessFile file = new RandomAccessFile(LookFeed.path().toFile(), "rw")) {
            buffer = file.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, 4096);
            // libgameinput.so lee uint32/int16 nativos (LE); MappedByteBuffer arranca en BE.
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            clearAll();
            log.info("pad feed=sdl path={} slots={}", LookFeed.path(), MAX_SLOTS);
        } catch (IOException ex) {
            buffer = null;
            log.warn("pad feed failed {}", ex.toString());
        }
    }

    public void publishSlot(int slot, int buttons, int[] axes) {
        if (slot < 0 || slot >= MAX_SLOTS) {
            return;
        }
        MappedByteBuffer page = buffer;
        if (page == null) {
            return;
        }
        page.putInt(offMagic(slot), MAGIC);
        page.putInt(offButtons(slot), buttons);
        for (int i = 0; i < 6; i++) {
            int v = axes != null && i < axes.length ? axes[i] : 0;
            if (v > 32767) {
                v = 32767;
            } else if (v < -32768) {
                v = -32768;
            }
            page.putShort(offAxes(slot) + i * 2, (short) v);
        }
        page.putShort(offActive(slot), (short) 1);
        if (!logged) {
            logged = true;
            log.info("pad feed publish slot={} buttons=0x{}", slot, Integer.toHexString(buttons));
        }
    }

    public void clearSlot(int slot) {
        if (slot < 0 || slot >= MAX_SLOTS) {
            return;
        }
        MappedByteBuffer page = buffer;
        if (page == null) {
            return;
        }
        page.putInt(offMagic(slot), 0);
        page.putInt(offButtons(slot), 0);
        page.putShort(offActive(slot), (short) 0);
        for (int i = 0; i < 6; i++) {
            page.putShort(offAxes(slot) + i * 2, (short) 0);
        }
    }

    public void clearAll() {
        for (int slot = 0; slot < MAX_SLOTS; slot++) {
            clearSlot(slot);
        }
    }

    public void close() {
        clearAll();
        buffer = null;
        logged = false;
    }
}
