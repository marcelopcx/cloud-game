/** Tick 250 Hz: el más nuevo gana. Un solo datagrama en vuelo. */

import { attachGamepad, WIRE_BTN, type PadSample } from './gamepad.ts';
import { attachKeyboard } from './keyboard.ts';
import { attachPointer } from './pointer.ts';
import { attachFrameClock } from './frame.ts';
import type { InputNeeds } from './input.ts';
import type { GameSize, MouseHud } from './pointer.ts';
import { HISTORY, encodeDatagram, sendDatagram, type Snapshot } from './snapshot.ts';

const TICK_MS = 4;
/** Home abre el menú de opciones del player: es del cliente, no viaja al juego. */
const CLIENT_BUTTONS = 1 << WIRE_BTN.GUIDE;
const PAD_REST_AXES = [0, 0, 0, 0, 0, 0];

type WireSnapshot = Snapshot & { tx: boolean };

function foldMouse(into: Snapshot, dropped: Snapshot) {
    const a = dropped.mouse;
    const b = into.mouse;
    if (!a || !b || a.abs || b.abs) return;
    b.x += a.x;
    b.y += a.y;
    b.wheel += a.wheel;
}

export function startInput(opts: {
    channel: RTCDataChannel;
    video: HTMLVideoElement;
    needs: InputNeeds;
    gameSize?: () => GameSize | null;
    /** Toggle del menú: en false el mouse deja de viajar por el datagrama. */
    mouseEnabled?: () => boolean;
    /** Menú de opciones abierto: en false el pad viaja en reposo (lo usa la UI). */
    gamepadEnabled?: () => boolean;
    onPads?: (ids: string[]) => void;
    onKeys?: (held: string[]) => void;
    onMouse?: (state: MouseHud | null) => void;
    onLock?: (locked: boolean) => void;
    onLog?: (line: string) => void;
}) {
    const { channel, needs } = opts;
    let seq = 0;
    let stopped = false;
    const history: WireSnapshot[] = [];
    const frames = attachFrameClock(opts.video);
    const runtime = { pulse: () => undefined as void };
    // Menú de opciones: mouse off saca el puntero del wire sin demontar la captura.
    const mouseWanted = () => opts.mouseEnabled?.() ?? true;
    const padsWanted = () => opts.gamepadEnabled?.() ?? true;
    let padsWereWanted = true;
    /**
     * Botones apretados cuando el pad vuelve al juego (la B o el Home que cerró el
     * menú), por slot: no llegan al juego hasta que se suelten.
     */
    const padsLatched = new Map<number, number>();

    const gatePads = (pads: PadSample[]): PadSample[] => {
        if (!padsWanted()) {
            padsWereWanted = false;
            return pads.map(({ slot }) => ({ slot, buttons: 0, axes: PAD_REST_AXES }));
        }
        if (!padsWereWanted) {
            padsWereWanted = true;
            padsLatched.clear();
            for (const pad of pads) padsLatched.set(pad.slot, pad.buttons);
        }
        return pads.map((pad) => {
            const latched = (padsLatched.get(pad.slot) ?? 0) & pad.buttons;
            padsLatched.set(pad.slot, latched);
            return { ...pad, buttons: pad.buttons & ~latched & ~CLIENT_BUTTONS };
        });
    };

    const keyboard = needs.keyboard
        ? attachKeyboard({
            onKeys: opts.onKeys,
            onChange: () => runtime.pulse(),
            onLog: opts.onLog,
        })
        : null;
    const pointer = needs.mouse
        ? attachPointer({
            video: opts.video,
            lookMode: Boolean(needs.relativeMouse),
            gameSize: opts.gameSize,
            enabled: mouseWanted,
            onMouse: opts.onMouse,
            onLock: opts.onLock,
            onChange: () => runtime.pulse(),
            onLog: opts.onLog,
        })
        : null;
    const gamepad = needs.gamepad > 0
        ? attachGamepad({
            maxPads: needs.gamepad,
            onPads: opts.onPads,
        })
        : null;

    if (!needs.keyboard) opts.onLog?.('teclado desactivado por needs.keyboard=false');
    if (!needs.mouse) opts.onLog?.('mouse desactivado por needs.mouse=false');
    if (needs.gamepad === 0) opts.onLog?.('gamepad desactivado por needs.gamepad=0');
    opts.onLog?.(
        `input udp tick=250Hz latest-wins history=${HISTORY} pads=${needs.gamepad} keyboard=${needs.keyboard} mouse=${needs.mouse} relativeMouse=${Boolean(needs.relativeMouse)}`,
    );

    const sample = (): Snapshot => {
        seq = (seq + 1) & 0xffff;
        const mouseOk = needs.mouse && mouseWanted();
        // Siempre drena los deltas: con mouse off se descartan para que al
        // reactivarlo no entre un salto acumulado de cámara.
        const mouse = pointer?.takeDelta();
        if (!mouseOk) pointer?.exitLock();
        const keys = (keyboard?.codes() ?? []).slice().sort((a, b) => a - b);
        const pads: PadSample[] = gatePads(gamepad?.sample() ?? []);
        return {
            seq,
            frameId: frames.current(),
            mouse: mouseOk && mouse
                ? {
                    abs: mouse.abs,
                    x: mouse.x,
                    y: mouse.y,
                    buttons: mouse.buttons,
                    wheel: mouse.wheel,
                }
                : undefined,
            keys: needs.keyboard ? keys : undefined,
            pads: needs.gamepad > 0 ? pads : undefined,
        };
    };

    const flush = (): boolean => {
        if (!sendDatagram(channel, encodeDatagram(history))) {
            pointer?.restoreLastDelta();
            if (history.length > 0) history.pop();
            return false;
        }
        for (const snap of history) snap.tx = true;
        return true;
    };

    const push = (snap: Snapshot) => {
        const item: WireSnapshot = { ...snap, tx: false };
        if (history.length >= HISTORY) {
            const dropped = history.shift();
            if (dropped && !dropped.tx) foldMouse(item, dropped);
        }
        history.push(item);
    };

    const tick = () => {
        if (stopped || channel.readyState !== 'open') return;
        push(sample());
        flush();
    };

    runtime.pulse = () => tick();

    const rest = (): Snapshot => {
        seq = (seq + 1) & 0xffff;
        return {
            seq,
            frameId: frames.current(),
            mouse: needs.mouse ? { abs: true, x: 0, y: 0, buttons: 0, wheel: 0 } : undefined,
            keys: needs.keyboard ? [] : undefined,
            pads: needs.gamepad > 0 ? [] : undefined,
        };
    };

    const onLow = () => {
        if (stopped || channel.readyState !== 'open' || history.length === 0) return;
        flush();
    };

    tick();
    const timer = window.setInterval(tick, TICK_MS);
    channel.addEventListener('bufferedamountlow', onLow);

    return () => {
        stopped = true;
        window.clearInterval(timer);
        channel.removeEventListener('bufferedamountlow', onLow);
        if (channel.readyState === 'open') {
            sendDatagram(channel, encodeDatagram([rest()]));
        }
        frames.stop();
        keyboard?.stop();
        pointer?.stop();
        gamepad?.stop();
    };
}
