/**
 * Del estado del mando a eventos de la UI: flancos, repetición, arranque con
 * botones ya apretados, histéresis del stick y scroll. Correr con `npm test`.
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
    createPadMachine,
    REPEAT_DELAY_MS,
    REPEAT_INTERVAL_MS,
    SCROLL_PX_PER_MS,
    stickDir,
    type PadSnapshot,
} from '../src/gamepad/padMachine.ts';
import { WIRE_BTN } from '../src/webrtc/gamepad.ts';

const A = 1 << WIRE_BTN.A;
const B = 1 << WIRE_BTN.B;
const HOME = 1 << WIRE_BTN.GUIDE;
const RIGHT = 1 << WIRE_BTN.DP_RIGHT;

function pad(partial: Partial<PadSnapshot> = {}): PadSnapshot {
    return { buttons: 0, lx: 0, ly: 0, ry: 0, ...partial };
}

/** Máquina ya cebada con el mando en reposo en t=16. */
function primed() {
    const machine = createPadMachine();
    machine.step(pad(), 16);
    return machine;
}

test('A dispara una sola vez por pulsación (flanco de subida)', () => {
    const machine = primed();
    assert.deepEqual(machine.step(pad({ buttons: A }), 32), [{ type: 'activate' }]);
    assert.deepEqual(machine.step(pad({ buttons: A }), 48), []);
    assert.deepEqual(machine.step(pad(), 64), []);
    assert.deepEqual(machine.step(pad({ buttons: A }), 80), [{ type: 'activate' }]);
});

test('Home dispara una vez por pulsación', () => {
    const machine = primed();
    assert.deepEqual(machine.step(pad({ buttons: HOME }), 32), [{ type: 'home' }]);
    assert.deepEqual(machine.step(pad({ buttons: HOME }), 48), []);
    assert.deepEqual(machine.step(pad(), 64), []);
    assert.deepEqual(machine.step(pad({ buttons: HOME }), 80), [{ type: 'home' }]);
});

test('un botón que ya estaba apretado al arrancar no dispara', () => {
    // El juego suelta el mando con A mantenido: ese A no puede activar la UI.
    const machine = createPadMachine();
    assert.deepEqual(machine.step(pad({ buttons: A }), 16), []);
    assert.deepEqual(machine.step(pad({ buttons: A }), 32), []);
    assert.deepEqual(machine.step(pad(), 48), []);
    assert.deepEqual(machine.step(pad({ buttons: A }), 64), [{ type: 'activate' }]);
});

test('una dirección mantenida desde el arranque no se repite hasta soltarla', () => {
    const machine = createPadMachine();
    machine.step(pad({ buttons: RIGHT }), 16);
    assert.deepEqual(machine.step(pad({ buttons: RIGHT }), 16 + REPEAT_DELAY_MS * 3), []);
    machine.step(pad(), 2000);
    assert.deepEqual(machine.step(pad({ buttons: RIGHT }), 2016), [{ type: 'dir', dir: 'right' }]);
});

test('al reconectar el mando se vuelve a cebar', () => {
    const machine = primed();
    machine.step(null, 32);
    assert.deepEqual(machine.step(pad({ buttons: B }), 48), []);
});

test('dirección mantenida: espera el retardo y después repite con el intervalo', () => {
    const machine = primed();
    assert.deepEqual(machine.step(pad({ buttons: RIGHT }), 100), [{ type: 'dir', dir: 'right' }]);
    assert.deepEqual(machine.step(pad({ buttons: RIGHT }), 100 + REPEAT_DELAY_MS - 1), []);
    assert.deepEqual(machine.step(pad({ buttons: RIGHT }), 100 + REPEAT_DELAY_MS), [{ type: 'dir', dir: 'right' }]);
    assert.deepEqual(
        machine.step(pad({ buttons: RIGHT }), 100 + REPEAT_DELAY_MS + REPEAT_INTERVAL_MS),
        [{ type: 'dir', dir: 'right' }],
    );
});

test('stick cerca de 45°: no alterna entre dos direcciones', () => {
    // Mantenido a la derecha, el eje vertical pasa al horizontal pero el
    // horizontal sigue por encima del umbral de salida: sigue siendo "right".
    assert.equal(stickDir(pad({ lx: 0.7, ly: 0.2 }), null), 'right');
    assert.equal(stickDir(pad({ lx: 0.6, ly: 0.65 }), 'right'), 'right');
    assert.equal(stickDir(pad({ lx: 0.5, ly: 0.7 }), 'right'), 'right');
    // Recién cuando el horizontal cae por debajo del umbral de salida cambia.
    assert.equal(stickDir(pad({ lx: 0.2, ly: 0.7 }), 'right'), 'down');
});

test('stick: histéresis de magnitud', () => {
    assert.equal(stickDir(pad({ lx: 0.4 }), null), null);
    assert.equal(stickDir(pad({ lx: 0.4 }), 'right'), 'right');
    assert.equal(stickDir(pad({ lx: 0.3 }), 'right'), null);
});

test('stick derecho: scroll continuo proporcional al tiempo del cuadro', () => {
    const machine = primed();
    assert.deepEqual(machine.step(pad({ ry: 1 }), 32), [{ type: 'scroll', px: SCROLL_PX_PER_MS * 16 }]);
    assert.deepEqual(machine.step(pad({ ry: -0.5 }), 48), [{ type: 'scroll', px: -0.5 * SCROLL_PX_PER_MS * 16 }]);
});

test('stick derecho: zona muerta con histéresis', () => {
    const machine = primed();
    assert.deepEqual(machine.step(pad({ ry: 0.2 }), 32), []);
    machine.step(pad({ ry: 0.3 }), 48);
    assert.equal(machine.step(pad({ ry: 0.2 }), 64).length, 1);
    assert.deepEqual(machine.step(pad({ ry: 0.1 }), 80), []);
});

test('con una dirección mantenida el stick derecho no scrollea', () => {
    const machine = primed();
    assert.deepEqual(machine.step(pad({ buttons: RIGHT, ry: 1 }), 32), [{ type: 'dir', dir: 'right' }]);
    assert.deepEqual(machine.step(pad({ buttons: RIGHT, ry: 1 }), 48), []);
});

test('un dt enorme (pestaña oculta) se topa', () => {
    const machine = primed();
    const [event] = machine.step(pad({ ry: 1 }), 10_000);
    assert.deepEqual(event, { type: 'scroll', px: SCROLL_PX_PER_MS * 50 });
});
