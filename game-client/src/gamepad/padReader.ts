/**
 * Lectura del mando para la navegación de la UI.
 *
 * Reusa `readStandardPad` / `WIRE_BTN` de `webrtc/gamepad.ts` en vez de duplicar
 * la máscara: los dos lectores pueden convivir porque `navigator.getGamepads()`
 * es sin estado. La lógica de flancos, repetición y scroll está en `padMachine.ts`.
 *
 * Stick izquierdo y D-pad mueven el foco; stick derecho hace scroll.
 */

import { listConnectedGamepads, readStandardPad, WIRE_BTN } from '@/webrtc/gamepad.ts';
import { createPadMachine, type PadSnapshot, type UiPadEvent } from './padMachine.ts';

export type { UiPadEvent };

/** Hasta cuatro mandos: el que tiene el usuario puede no ser el primero (pads virtuales de Steam/DS4Windows). */
const MAX_PADS = 4;
const AXIS_MAX = 32767;

/** Ejes 6/7: D-pad de los mandos sin mapeo estándar (Firefox en Linux, por ejemplo). */
const AXIS_DPAD_X = 6;
const AXIS_DPAD_Y = 7;
const AXIS_DPAD_ON = 0.5;
/** En reposo un D-pad por ejes vale 0; si arranca en otro valor esos ejes son otra cosa (gatillos). */
const AXIS_DPAD_REST = 0.1;

function padKey(pad: Gamepad): string {
    return `${pad.index}:${pad.id}`;
}

/**
 * El D-pad por ejes como bits del wire. Solo se lee si en el primer cuadro en
 * que se vio el mando los dos ejes estaban en reposo: así un gatillo que
 * descansa en -1 no queda leído como "arriba" para siempre.
 */
function axisDpad(pad: Gamepad, usable: Map<string, boolean>): number {
    if (pad.mapping === 'standard' || pad.axes.length <= AXIS_DPAD_Y) return 0;
    const x = pad.axes[AXIS_DPAD_X] ?? 0;
    const y = pad.axes[AXIS_DPAD_Y] ?? 0;
    const key = padKey(pad);
    if (!usable.has(key)) usable.set(key, Math.abs(x) < AXIS_DPAD_REST && Math.abs(y) < AXIS_DPAD_REST);
    if (!usable.get(key)) return 0;

    let buttons = 0;
    if (x <= -AXIS_DPAD_ON) buttons |= 1 << WIRE_BTN.DP_LEFT;
    if (x >= AXIS_DPAD_ON) buttons |= 1 << WIRE_BTN.DP_RIGHT;
    if (y <= -AXIS_DPAD_ON) buttons |= 1 << WIRE_BTN.DP_UP;
    if (y >= AXIS_DPAD_ON) buttons |= 1 << WIRE_BTN.DP_DOWN;
    return buttons;
}

/** Todos los mandos juntos: botones sumados, y de cada stick el más inclinado. */
function snapshot(usable: Map<string, boolean>): PadSnapshot | null {
    const pads = listConnectedGamepads(MAX_PADS);
    if (pads.length === 0) return null;

    const merged: PadSnapshot = { buttons: 0, lx: 0, ly: 0, ry: 0 };
    for (const pad of pads) {
        const { buttons, axes } = readStandardPad(pad);
        merged.buttons |= buttons | axisDpad(pad, usable);
        const lx = (axes[0] ?? 0) / AXIS_MAX;
        const ly = (axes[1] ?? 0) / AXIS_MAX;
        if (Math.hypot(lx, ly) > Math.hypot(merged.lx, merged.ly)) {
            merged.lx = lx;
            merged.ly = ly;
        }
        const ry = (axes[3] ?? 0) / AXIS_MAX;
        if (Math.abs(ry) > Math.abs(merged.ry)) merged.ry = ry;
    }
    return merged;
}

export function startUiPadReader(opts: {
    onEvent: (event: UiPadEvent) => void;
}): () => void {
    const machine = createPadMachine();
    const usable = new Map<string, boolean>();
    let frame = 0;

    const tick = (now: number) => {
        frame = requestAnimationFrame(tick);
        const pad = snapshot(usable);
        if (!pad) usable.clear();
        for (const event of machine.step(pad, now)) opts.onEvent(event);
    };

    frame = requestAnimationFrame(tick);

    return () => {
        cancelAnimationFrame(frame);
        machine.reset();
    };
}
