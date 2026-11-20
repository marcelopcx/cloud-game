/**
 * Del estado crudo del mando a eventos de la UI: flancos de botón, repetición de
 * dirección y scroll del stick derecho. Sin DOM ni `navigator`, para poder
 * testearla con `node --test`; el loop y la lectura viven en `padReader.ts`.
 */

import { WIRE_BTN } from '../webrtc/gamepad.ts';
import type { Dir } from './geometry.ts';

export type UiPadEvent =
    | { type: 'dir'; dir: Dir }
    | { type: 'activate' }
    | { type: 'back' }
    | { type: 'home' }
    | { type: 'page'; delta: 1 | -1 }
    | { type: 'scroll'; px: number };

/** Botones en máscara wire (con el D-pad ya resuelto) y ejes normalizados a -1..1. */
export type PadSnapshot = { buttons: number; lx: number; ly: number; ry: number };

/** Retardo antes de repetir una dirección mantenida, y periodo entre repeticiones. */
export const REPEAT_DELAY_MS = 400;
export const REPEAT_INTERVAL_MS = 120;

/** Stick izquierdo: mucho más cerrado que el del juego, para que el foco no derive. */
const STICK_ON = 0.5;
const STICK_OFF = 0.35;

/** Stick derecho: scroll. Velocidad máxima en px/ms, proporcional a la inclinación. */
const SCROLL_ON = 0.25;
const SCROLL_OFF = 0.15;
export const SCROLL_PX_PER_MS = 1.7;

/** Un `dt` mayor (pestaña oculta, frame perdido) se topa para que el scroll no salte. */
const MAX_DT_MS = 50;

const BUTTONS: { mask: number; event: UiPadEvent }[] = [
    { mask: 1 << WIRE_BTN.A, event: { type: 'activate' } },
    { mask: 1 << WIRE_BTN.B, event: { type: 'back' } },
    { mask: 1 << WIRE_BTN.GUIDE, event: { type: 'home' } },
    { mask: 1 << WIRE_BTN.LB, event: { type: 'page', delta: -1 } },
    { mask: 1 << WIRE_BTN.RB, event: { type: 'page', delta: 1 } },
];

const DPAD: { mask: number; dir: Dir }[] = [
    { mask: 1 << WIRE_BTN.DP_UP, dir: 'up' },
    { mask: 1 << WIRE_BTN.DP_DOWN, dir: 'down' },
    { mask: 1 << WIRE_BTN.DP_LEFT, dir: 'left' },
    { mask: 1 << WIRE_BTN.DP_RIGHT, dir: 'right' },
];

function isHorizontal(dir: Dir): boolean {
    return dir === 'left' || dir === 'right';
}

/**
 * Dirección del stick izquierdo, con histéresis en la magnitud y en el eje.
 *
 * Mientras el eje de la dirección mantenida siga por encima del umbral de
 * salida, la dirección no cambia aunque el otro eje lo supere: sin eso, el
 * stick cerca de 45° alterna entre dos direcciones y cada cambio mueve el foco
 * al instante, sin esperar la repetición.
 */
export function stickDir(pad: PadSnapshot, previous: Dir | null): Dir | null {
    if (previous) {
        const along = isHorizontal(previous) ? pad.lx : pad.ly;
        if (Math.abs(along) >= STICK_OFF) {
            if (isHorizontal(previous)) return along > 0 ? 'right' : 'left';
            return along > 0 ? 'down' : 'up';
        }
    }
    const gate = previous ? STICK_OFF : STICK_ON;
    const ax = Math.abs(pad.lx);
    const ay = Math.abs(pad.ly);
    if (ax < gate && ay < gate) return null;
    if (ax > ay) return pad.lx > 0 ? 'right' : 'left';
    return pad.ly > 0 ? 'down' : 'up';
}

function dpadDir(buttons: number): Dir | null {
    for (const { mask, dir } of DPAD) {
        if (buttons & mask) return dir;
    }
    return null;
}

export function createPadMachine() {
    /**
     * El primer cuadro con mando solo toma nota de lo que ya está apretado.
     * Si el juego suelta el mando con A mantenido (o se reconecta el pad con un
     * botón apretado), ese A no puede activar lo que esté enfocado.
     */
    let primed = false;
    let previousButtons = 0;
    let heldDir: Dir | null = null;
    let heldStickDir: Dir | null = null;
    let repeatAt = 0;
    let scrollActive = false;
    let lastNow = 0;

    const reset = () => {
        primed = false;
        previousButtons = 0;
        heldDir = null;
        heldStickDir = null;
        scrollActive = false;
    };

    const step = (pad: PadSnapshot | null, now: number): UiPadEvent[] => {
        const dtMs = lastNow === 0 ? 0 : Math.min(now - lastNow, MAX_DT_MS);
        lastNow = now;

        if (!pad) {
            reset();
            return [];
        }

        // Dirección: el D-pad manda; el stick izquierdo solo si no hay D-pad.
        const stick = stickDir(pad, heldStickDir);
        heldStickDir = stick;
        const dir = dpadDir(pad.buttons) ?? stick;

        if (!primed) {
            primed = true;
            previousButtons = pad.buttons;
            // Una dirección que ya venía mantenida no se repite hasta soltarla.
            heldDir = dir;
            repeatAt = Infinity;
            scrollActive = false;
            return [];
        }

        const events: UiPadEvent[] = [];

        // Botones: solo el flanco de subida, para no repetir el click.
        const pressed = pad.buttons & ~previousButtons;
        previousButtons = pad.buttons;
        for (const { mask, event } of BUTTONS) {
            if (pressed & mask) events.push(event);
        }

        if (!dir) {
            heldDir = null;
        } else if (dir !== heldDir) {
            heldDir = dir;
            repeatAt = now + REPEAT_DELAY_MS;
            events.push({ type: 'dir', dir });
        } else if (now >= repeatAt) {
            repeatAt = now + REPEAT_INTERVAL_MS;
            events.push({ type: 'dir', dir });
        }

        // Scroll: stick derecho, continuo (px proporcionales al tiempo del cuadro).
        // El foco manda: con una dirección mantenida no se scrollea, o el cuadro
        // siguiente desplazaría lo que se acaba de enfocar.
        scrollActive = Math.abs(pad.ry) >= (scrollActive ? SCROLL_OFF : SCROLL_ON);
        if (scrollActive && !dir && dtMs > 0) {
            events.push({ type: 'scroll', px: pad.ry * SCROLL_PX_PER_MS * dtMs });
        }

        return events;
    };

    return { step, reset };
}
