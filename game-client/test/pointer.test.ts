/**
 * Mouse absoluto con stream reducido: el click tiene que caer en el mismo
 * punto del juego sin importar la resolución del video.
 * Correr con `npm test` (node --test, TypeScript nativo).
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mapContainPoint } from '../src/webrtc/pointer.ts';

const GAME = { width: 1280, height: 720 };
const rect16x9 = { left: 0, top: 0, width: 1920, height: 1080 };

test('stream 854x480 mapea al centro del juego 1280x720', () => {
    const pt = mapContainPoint(rect16x9, 854, 480, 960, 540, GAME);
    assert.deepEqual(pt, { x: 640, y: 360 });
});

test('stream reducido y stream completo dan el mismo píxel de juego', () => {
    const reduced = mapContainPoint(rect16x9, 960, 540, 1500, 300, GAME);
    const full = mapContainPoint(rect16x9, 1280, 720, 1500, 300, GAME);
    assert.deepEqual(reduced, full);
    assert.deepEqual(full, { x: 1000, y: 200 });
});

test('las esquinas quedan dentro del juego', () => {
    assert.deepEqual(mapContainPoint(rect16x9, 854, 480, 0, 0, GAME), { x: 0, y: 0 });
    assert.deepEqual(mapContainPoint(rect16x9, 854, 480, 1920, 1080, GAME), { x: 1279, y: 719 });
});

test('las bandas de object-fit: contain no desplazan el click', () => {
    // Contenedor 4:3 → video 16:9 con bandas arriba y abajo de 150 px.
    const rect = { left: 10, top: 20, width: 1600, height: 1200 };
    assert.deepEqual(mapContainPoint(rect, 854, 480, 10 + 800, 20 + 600, GAME), { x: 640, y: 360 });
    // Click en la banda superior: se clampa al borde del juego.
    assert.deepEqual(mapContainPoint(rect, 854, 480, 10 + 800, 20 + 50, GAME), { x: 640, y: 0 });
});

test('sin layout no hay coordenada', () => {
    assert.equal(mapContainPoint({ left: 0, top: 0, width: 0, height: 0 }, 854, 480, 1, 1, GAME), null);
});
