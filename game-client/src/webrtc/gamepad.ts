/**
 * Gamepad remoto → datagrama (máscara + 6 ejes) alineado con {@code UinputPad} /
 * W3C Standard Gamepad (Xbox). Slot 0 = mando cloud principal.
 */

/** Bits del wire format (0..16); 6–7 reservados (LT/RT van en ejes). */
export const WIRE_BTN = {
    A: 0,
    B: 1,
    X: 2,
    Y: 3,
    LB: 4,
    RB: 5,
    VIEW: 8,
    MENU: 9,
    LS: 10,
    RS: 11,
    DP_UP: 12,
    DP_DOWN: 13,
    DP_LEFT: 14,
    DP_RIGHT: 15,
    GUIDE: 16,
} as const;

export type PadSample = {
    slot: number;
    buttons: number;
    axes: number[];
};

const INNER_DEADZONE = 0.08;
const TRIGGER_GATE = 0.04;
const AXIS_MAX = 32767;

function clamp1(v: number): number {
    return Math.max(-1, Math.min(1, v));
}

function axisInt(v: number): number {
    return Math.round(clamp1(v) * AXIS_MAX);
}

function triggerInt(v: number): number {
    let n = v;
    if (n < -0.5) {
        n = (n + 1) / 2;
    }
    n = Math.max(0, Math.min(1, n));
    if (n < TRIGGER_GATE) return 0;
    return Math.round(n * AXIS_MAX);
}

function stickPair(x: number, y: number): [number, number] {
    const mag = Math.hypot(x, y);
    if (mag < INNER_DEADZONE || isNaN(mag)) return [0, 0];
    const scaled = (mag - INNER_DEADZONE) / (1 - INNER_DEADZONE);
    const s = scaled / mag;
    return [clamp1(x * s), clamp1(y * s)];
}

function buttonDown(button: GamepadButton | undefined): boolean {
    if (!button) return false;
    return button.pressed || button.value > 0.55;
}

/** Mapeo Standard Gamepad → máscara wire (sin LT/RT en bits 6–7). */
export function readStandardPad(pad: Gamepad): Pick<PadSample, 'buttons' | 'axes'> {
    const b = pad.buttons;
    let buttons = 0;
    const set = (wireBit: number, btnIndex: number) => {
        if (buttonDown(b[btnIndex])) buttons |= 1 << wireBit;
    };

    set(WIRE_BTN.A, 0);
    set(WIRE_BTN.B, 1);
    set(WIRE_BTN.X, 2);
    set(WIRE_BTN.Y, 3);
    set(WIRE_BTN.LB, 4);
    set(WIRE_BTN.RB, 5);
    set(WIRE_BTN.VIEW, 8);
    set(WIRE_BTN.MENU, 9);
    set(WIRE_BTN.LS, 10);
    set(WIRE_BTN.RS, 11);
    set(WIRE_BTN.DP_UP, 12);
    set(WIRE_BTN.DP_DOWN, 13);
    set(WIRE_BTN.DP_LEFT, 14);
    set(WIRE_BTN.DP_RIGHT, 15);
    set(WIRE_BTN.GUIDE, 16);

    const ax0 = pad.axes[0] ?? 0;
    const ax1 = pad.axes[1] ?? 0;
    let ax2 = pad.axes[2] ?? 0;
    let ax3 = pad.axes[3] ?? 0;
    let ltRaw = b[6]?.value ?? pad.axes[4] ?? 0;
    let rtRaw = b[7]?.value ?? pad.axes[5] ?? 0;

    // Manejo de mapeo no estándar (ej. Xbox Wireless Controller por Bluetooth en macOS/Chrome)
    if (pad.mapping !== 'standard') {
        const idLower = pad.id.toLowerCase();
        if (idLower.includes('xbox') || idLower.includes('045e')) {
            if (pad.axes.length >= 6) {
                // axes[2] es LT (-1..1), axes[3] es RX, axes[4] es RY, axes[5] es RT (-1..1)
                ax2 = pad.axes[3] ?? 0;
                ax3 = pad.axes[4] ?? 0;
                ltRaw = pad.axes[2] ?? b[6]?.value ?? 0;
                rtRaw = pad.axes[5] ?? b[7]?.value ?? 0;
            }
        }
    }

    const [lx, ly] = stickPair(ax0, ax1);
    const [rx, ry] = stickPair(ax2, ax3);
    const lt = triggerInt(ltRaw);
    const rt = triggerInt(rtRaw);

    return {
        buttons,
        axes: [axisInt(lx), axisInt(ly), axisInt(rx), axisInt(ry), lt, rt],
    };
}

function padKey(pad: Gamepad): string {
    const id = pad.id.trim() || 'gamepad';
    return `${pad.index}:${id}`;
}

/** Mandos conectados, orden estable por {@code index} (hasta {@code maxSlots}). */
export function listConnectedGamepads(maxSlots: number): Gamepad[] {
    const raw = [...navigator.getGamepads()].filter((p): p is Gamepad => p != null);
    const standard = raw.filter((p) => p.mapping === 'standard');
    const pool = (standard.length > 0 ? standard : raw).sort((a, b) => a.index - b.index);
    const seen = new Set<number>();
    const out: Gamepad[] = [];
    for (const pad of pool) {
        if (seen.has(pad.index)) continue;
        seen.add(pad.index);
        out.push(pad);
        if (out.length >= maxSlots) break;
    }
    return out;
}

export function attachGamepad(opts: {
    maxPads: 0 | 1 | 2 | 3 | 4;
    onPads?: (ids: string[]) => void;
}) {
    const maxPads = opts.maxPads;
    if (maxPads === 0) {
        return {
            sample: (): PadSample[] => [],
            stop: () => undefined,
        };
    }

    const roster: (string | null)[] = Array.from({ length: maxPads }, () => null);

    const emitRoster = () => {
        opts.onPads?.(roster.filter((id): id is string => Boolean(id)));
    };

    const sample = (): PadSample[] => {
        void navigator.getGamepads();
        const connected = listConnectedGamepads(maxPads);
        let changed = false;
        const samples: PadSample[] = [];

        for (let slot = 0; slot < maxPads; slot++) {
            const pad = connected[slot];
            if (!pad) {
                if (roster[slot]) {
                    roster[slot] = null;
                    changed = true;
                }
                continue;
            }
            const label = padKey(pad);
            if (roster[slot] !== label) {
                roster[slot] = label;
                changed = true;
            }
            const { buttons, axes } = readStandardPad(pad);
            samples.push({ slot, buttons, axes });
        }

        if (changed) emitRoster();
        return samples;
    };

    const onGamepadEvent = () => {
        sample();
    };

    window.addEventListener('gamepadconnected', onGamepadEvent);
    window.addEventListener('gamepaddisconnected', onGamepadEvent);
    sample();

    return {
        sample,
        stop: () => {
            window.removeEventListener('gamepadconnected', onGamepadEvent);
            window.removeEventListener('gamepaddisconnected', onGamepadEvent);
            roster.fill(null);
            emitRoster();
        },
    };
}
