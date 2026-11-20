package com.cloudgame.station.service;

import com.cloudgame.station.model.GameManifest;
import com.cloudgame.station.config.StationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import com.cloudgame.station.infrastructure.DatagramParser;
import com.cloudgame.station.infrastructure.LookFeed;
import com.cloudgame.station.infrastructure.PadFeed;
import com.cloudgame.station.infrastructure.UinputPad;
import com.cloudgame.station.infrastructure.X11Injector;

/**
 * Sink del DataChannel {@code input}.
 *
 * <p>Misma arquitectura que {@code game-station/controller/input/sink.py}: el
 * hilo del DataChannel solo encola; pads e X11 se aplican en hilos dedicados
 * con un solo flush XTEST por lote. Con {@code needs.relativeMouse}, el giro
 * va a {@link LookFeed}; teclado, botones y rueda siguen por XTEST.
 */

@Component
public class InputSink {

    private static final Logger log = LoggerFactory.getLogger(InputSink.class);
    private static final int[] REST = {0, 0, 0, 0, 0, 0};

    private final StationProperties settings;
    private final List<UinputPad> pads = new ArrayList<>();
    private final LookFeed look = new LookFeed();
    private final PadFeed padFeed = new PadFeed();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition cond = lock.newCondition();

    private X11Injector x11;
    private boolean keyboard;
    private boolean mouse;
    private boolean relativeMouse;
    private int lastAbsX = Integer.MIN_VALUE;
    private int lastAbsY = Integer.MIN_VALUE;
    private int appliedSeq = -1;
    /** Último input real (ver {@link #active}); los datagramas en reposo no lo mueven. */
    private volatile long lastInputMillis;
    private int seenAbsX = Integer.MIN_VALUE;
    private int seenAbsY = Integer.MIN_VALUE;
    private boolean loggedKeys;
    private boolean loggedMouseButtons;
    private boolean loggedMouseMotion;
    private boolean loggedMouseSample;
    private boolean loggedPadMotion;
    private boolean padsUseUinput;
    private int padSlotCount;
    private volatile boolean stopWorkers;

    private Thread padThread;
    private Thread x11Thread;

    private Map<Integer, DatagramParser.PadSnapshot> padJob;
    private final Set<Integer> padLive = new HashSet<>();
    private Set<Integer> keysJob;
    private Integer buttonsJob;
    private int[] absJob;
    private int relXJob;
    private int relYJob;
    private int wheelJob;
    private boolean x11Dirty;

    public InputSink(StationProperties settings) {
        this.settings = settings;
    }

    public long lastInputMillis() {
        return lastInputMillis;
    }

    /** {@code true} si los pads virtuales escriben en {@code /dev/uinput}. */
    public boolean padsUseUinput() {
        return padsUseUinput;
    }

    public void open(GameManifest manifest) {
        close();
        GameManifest.Needs needs = manifest.getNeeds();
        keyboard = needs.isKeyboard();
        mouse = needs.isMouse();
        relativeMouse = needs.isRelativeMouse();
        lastAbsX = Integer.MIN_VALUE;
        lastAbsY = Integer.MIN_VALUE;
        int count = Math.max(0, Math.min(4, needs.getGamepad()));
        padSlotCount = count;
        padsUseUinput = false;
        pads.clear();
        boolean uinputRequested = uinputRequested(manifest);
        String uinputEnv = manifest.getEnv() == null ? null : manifest.getEnv().get("CLOUDGAME_INPUT_UINPUT");
        log.info(
                "input uinput policy game={} CLOUDGAME_INPUT_UINPUT={} useUinput={}",
                manifest.getId(),
                uinputEnv == null ? "(unset)" : uinputEnv,
                uinputRequested
        );
        if (uinputRequested) {
            for (int i = 0; i < count; i++) {
                UinputPad pad = new UinputPad(i);
                if (pad.open()) {
                    padsUseUinput = true;
                }
                pads.add(pad);
            }
        }
        if (count > 0) {
            padFeed.open();
            if (!uinputRequested) {
                log.info("input_backend=sdl_preload pads={} (CLOUDGAME_INPUT_UINPUT=false)", count);
            } else if (!padsUseUinput) {
                log.info("input_backend=sdl_preload (gamepad vía libgameinput.so, sin uinput)");
            } else {
                log.info("input_backend=uinput+padfeed pads={}", count);
            }
        }
        if (relativeMouse) {
            look.open();
        }
        if (keyboard || mouse) {
            x11 = new X11Injector(settings.getDisplay(), settings.width(), settings.height());
        }
        resetJobs();
        stopWorkers = false;
        if (padSlotCount > 0) {
            padThread = Thread.ofPlatform().daemon().name("input-pads").start(this::runPads);
        }
        if (x11 != null) {
            x11Thread = Thread.ofPlatform().daemon().name("input-x11").start(this::runX11);
        }
        log.info(
                "input open protocol=udp-latest pads={} keyboard={} mouse={} relativeMouse={} display={}",
                count, keyboard, mouse, relativeMouse, settings.getDisplay()
        );
    }

    public void close() {
        stopWorkers = true;
        lock.lock();
        try {
            cond.signalAll();
        } finally {
            lock.unlock();
        }
        joinWorker(padThread);
        joinWorker(x11Thread);
        padThread = null;
        x11Thread = null;
        for (UinputPad pad : pads) {
            pad.apply(0, REST);
            pad.close();
        }
        pads.clear();
        padLive.clear();
        look.close();
        padFeed.close();
        if (x11 != null) {
            x11.close();
            x11 = null;
        }
        keyboard = false;
        mouse = false;
        relativeMouse = false;
        lastAbsX = Integer.MIN_VALUE;
        lastAbsY = Integer.MIN_VALUE;
        appliedSeq = -1;
        lastInputMillis = 0;
        seenAbsX = Integer.MIN_VALUE;
        seenAbsY = Integer.MIN_VALUE;
        loggedKeys = false;
        loggedMouseButtons = false;
        loggedMouseMotion = false;
        loggedMouseSample = false;
        loggedPadMotion = false;
        resetJobs();
    }

    public void handle(byte[] data) {
        List<DatagramParser.Snapshot> snaps = DatagramParser.parse(data);
        if (snaps == null || snaps.isEmpty()) {
            log.warn("input: datagrama inválido len={}", data == null ? 0 : data.length);
            return;
        }
        DatagramParser.Snapshot latest = snaps.get(snaps.size() - 1);
        if (appliedSeq >= 0 && !DatagramParser.newer(latest.seq(), appliedSeq)) {
            return;
        }
        List<DatagramParser.Snapshot> pending = new ArrayList<>();
        int cursor = appliedSeq;
        for (DatagramParser.Snapshot snap : snaps) {
            if (cursor < 0 || DatagramParser.newer(snap.seq(), cursor)) {
                pending.add(snap);
                cursor = snap.seq();
            }
        }
        if (pending.isEmpty()) {
            return;
        }
        latest = pending.get(pending.size() - 1);
        int relX = 0;
        int relY = 0;
        int wheel = 0;
        int lookX = 0;
        int lookY = 0;
        for (DatagramParser.Snapshot snap : pending) {
            if (!snap.hasMouse()) {
                continue;
            }
            if (!loggedMouseSample && (snap.mouseX() != 0 || snap.mouseY() != 0)) {
                loggedMouseSample = true;
                log.info("input mouse sample abs={} x={} y={}", snap.mouseAbs(), snap.mouseX(), snap.mouseY());
            }
            if (!snap.mouseAbs()) {
                relX += snap.mouseX();
                relY += snap.mouseY();
                lookX += snap.mouseX();
                lookY += snap.mouseY();
            }
            wheel += snap.wheel();
        }
        if (mouse && relativeMouse) {
            int prevX = lastAbsX;
            int prevY = lastAbsY;
            for (DatagramParser.Snapshot snap : pending) {
                if (!snap.hasMouse() || !snap.mouseAbs()) {
                    continue;
                }
                if (prevX != Integer.MIN_VALUE) {
                    int dx = snap.mouseX() - prevX;
                    int dy = snap.mouseY() - prevY;
                    lookX += dx;
                    lookY += dy;
                }
                prevX = snap.mouseX();
                prevY = snap.mouseY();
            }
            if (prevX != Integer.MIN_VALUE) {
                lastAbsX = prevX;
                lastAbsY = prevY;
            }
            look.add(lookX, lookY);
            if (!loggedMouseMotion && (relX != 0 || relY != 0)) {
                loggedMouseMotion = true;
                log.info("input mouse motion look dx={} dy={} (rel batch={},{})", lookX, lookY, relX, relY);
            }
        } else if (mouse && !loggedMouseMotion && (relX != 0 || relY != 0)) {
            loggedMouseMotion = true;
            log.info("input mouse motion rel dx={} dy={}", relX, relY);
        }
        // El cliente manda un datagrama cada 4 ms aunque nadie toque nada: solo cuenta como
        // actividad (inactividad del Dispatcher) un control presionado o un cambio.
        boolean absMoved = false;
        if (latest.hasMouse() && latest.mouseAbs()) {
            absMoved = seenAbsX != Integer.MIN_VALUE
                    && (latest.mouseX() != seenAbsX || latest.mouseY() != seenAbsY);
            seenAbsX = latest.mouseX();
            seenAbsY = latest.mouseY();
        }
        boolean active = active(latest, relX, relY, wheel, absMoved);
        lock.lock();
        try {
            appliedSeq = latest.seq();
            if (active) {
                lastInputMillis = System.currentTimeMillis();
            }
            if (latest.hasPads()) {
                padJob = latest.pads();
            }
            if (latest.hasKeyboard() && keyboard) {
                keysJob = new HashSet<>(latest.keys());
                if (!loggedKeys && !keysJob.isEmpty()) {
                    loggedKeys = true;
                    log.info("input keys first count={} codes={}", keysJob.size(), keysJob);
                }
                x11Dirty = true;
            }
            if (latest.hasMouse() && mouse) {
                if (latest.mouseAbs() && !relativeMouse) {
                    absJob = new int[]{latest.mouseX(), latest.mouseY()};
                }
                if (relativeMouse) {
                    relXJob += relX;
                    relYJob += relY;
                } else {
                    relXJob += relX;
                    relYJob += relY;
                }
                wheelJob += wheel;
                buttonsJob = latest.mouseButtons();
                if (!loggedMouseButtons && buttonsJob != null && buttonsJob != 0) {
                    loggedMouseButtons = true;
                    log.info("input mouse buttons mask={}", buttonsJob);
                }
                x11Dirty = true;
            }
            cond.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /** Desvío de stick o gatillo por debajo de esto es ruido (el cliente ya aplica deadzone). */
    static final int AXIS_ACTIVE = 1024;

    /**
     * Hay alguien jugando: tecla, botón de mouse o de mando presionado, stick
     * o gatillo desviado, movimiento o rueda del mouse. Un snapshot en reposo
     * (lo que el cliente manda 250 veces por segundo sin input) no cuenta.
     */
    static boolean active(DatagramParser.Snapshot latest, int relX, int relY, int wheel, boolean absMoved) {
        if (relX != 0 || relY != 0 || wheel != 0 || absMoved) {
            return true;
        }
        if (latest.hasMouse() && latest.mouseButtons() != 0) {
            return true;
        }
        if (latest.hasKeyboard() && latest.keys() != null && !latest.keys().isEmpty()) {
            return true;
        }
        if (latest.hasPads() && latest.pads() != null) {
            for (DatagramParser.PadSnapshot pad : latest.pads().values()) {
                if (pad.buttons() != 0) {
                    return true;
                }
                for (int axis : pad.axes()) {
                    if (Math.abs(axis) > AXIS_ACTIVE) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void resetJobs() {
        padJob = null;
        keysJob = null;
        buttonsJob = null;
        absJob = null;
        relXJob = 0;
        relYJob = 0;
        wheelJob = 0;
        x11Dirty = false;
    }

    private void runPads() {
        while (!stopWorkers) {
            Map<Integer, DatagramParser.PadSnapshot> job = null;
            lock.lock();
            try {
                while (padJob == null && !stopWorkers) {
                    await();
                }
                job = padJob;
                padJob = null;
            } finally {
                lock.unlock();
            }
            if (job != null) {
                emitPads(job);
            }
        }
    }

    private void emitPads(Map<Integer, DatagramParser.PadSnapshot> padsState) {
        Set<Integer> live = new HashSet<>();
        for (DatagramParser.PadSnapshot snap : padsState.values()) {
            if (snap.slot() >= 0 && snap.slot() < pads.size()) {
                pads.get(snap.slot()).apply(snap.buttons(), snap.axes());
                live.add(snap.slot());
            }
            if (!loggedPadMotion && snap.slot() >= 0 && snap.slot() < padSlotCount
                    && (snap.buttons() != 0 || axisActive(snap.axes()))) {
                loggedPadMotion = true;
                log.info("input pad apply slot={} buttons=0x{} axes=[{},{},{},{},{},{}]",
                        snap.slot(),
                        Integer.toHexString(snap.buttons()),
                        snap.axes()[0], snap.axes()[1], snap.axes()[2],
                        snap.axes()[3], snap.axes()[4], snap.axes()[5]);
            }
        }
        for (int slot : new HashSet<>(padLive)) {
            if (!live.contains(slot) && slot < pads.size()) {
                pads.get(slot).apply(0, REST);
            }
        }
        padLive.clear();
        padLive.addAll(live);

        for (int slot = 0; slot < padSlotCount; slot++) {
            DatagramParser.PadSnapshot snap = padsState.get(slot);
            if (snap != null) {
                padFeed.publishSlot(slot, snap.buttons(), snap.axes());
            } else {
                padFeed.clearSlot(slot);
            }
        }
    }

    private static boolean axisActive(int[] axes) {
        if (axes == null) {
            return false;
        }
        for (int v : axes) {
            if (v != 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean uinputRequested(GameManifest manifest) {
        if (manifest == null || manifest.getEnv() == null) {
            return true;
        }
        String raw = manifest.getEnv().get("CLOUDGAME_INPUT_UINPUT");
        if (raw == null || raw.isBlank()) {
            return true;
        }
        return !(raw.equalsIgnoreCase("0") || raw.equalsIgnoreCase("false") || raw.equalsIgnoreCase("no"));
    }

    private void runX11() {
        while (!stopWorkers) {
            int[] abs = null;
            int relX = 0;
            int relY = 0;
            int wheel = 0;
            Integer buttons = null;
            Set<Integer> keys = null;
            lock.lock();
            try {
                while (!x11Dirty && !stopWorkers) {
                    await();
                }
                if (!x11Dirty) {
                    continue;
                }
                abs = absJob;
                relX = relXJob;
                relY = relYJob;
                wheel = wheelJob;
                buttons = buttonsJob;
                keys = keysJob;
                absJob = null;
                relXJob = 0;
                relYJob = 0;
                wheelJob = 0;
                x11Dirty = false;
            } finally {
                lock.unlock();
            }
            X11Injector injector = x11;
            if (injector == null) {
                continue;
            }
            injector.inject(
                    mouse && !relativeMouse ? abs : null,
                    // Con relativeMouse el giro va solo por LookFeed; por XTEST se sumaría dos veces.
                    mouse && !relativeMouse ? relX : 0,
                    mouse && !relativeMouse ? relY : 0,
                    mouse ? wheel : 0,
                    mouse ? buttons : null,
                    keyboard ? keys : null
            );
        }
    }

    private void await() {
        try {
            cond.awaitNanos(250_000_000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void joinWorker(Thread thread) {
        if (thread == null || !thread.isAlive()) {
            return;
        }
        try {
            thread.join(2000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
