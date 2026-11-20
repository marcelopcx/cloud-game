/**
 * Navegación espacial del foco: la dirección elige el candidato más cercano y
 * alineado, sin depender del orden del DOM. Correr con `npm test`
 * (node --test, TypeScript nativo).
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { pickInDirection, type Box } from '../src/gamepad/geometry.ts';

type Card = Box & { name: string };

/** Grid de 3 columnas x 3 filas, tarjetas de 200x100 con 20 px de separación. */
function grid(): Card[] {
    const names = 'ABCDEFGHI'.split('');
    return names.map((name, index) => ({
        name,
        left: (index % 3) * 220,
        top: Math.floor(index / 3) * 120,
        width: 200,
        height: 100,
    }));
}

function at(gridCards: Card[], name: string): Box {
    const found = gridCards.find((card) => card.name === name);
    assert.ok(found, `falta la tarjeta ${name}`);
    return found;
}

test('derecha avanza una columna dentro de la misma fila', () => {
    const cards = grid();
    assert.equal(pickInDirection(at(cards, 'A'), 'right', cards)?.name, 'B');
    assert.equal(pickInDirection(at(cards, 'B'), 'right', cards)?.name, 'C');
});

test('abajo baja una fila conservando la columna', () => {
    const cards = grid();
    assert.equal(pickInDirection(at(cards, 'A'), 'down', cards)?.name, 'D');
    assert.equal(pickInDirection(at(cards, 'C'), 'down', cards)?.name, 'F');
});

test('izquierda y arriba son el camino inverso', () => {
    const cards = grid();
    assert.equal(pickInDirection(at(cards, 'C'), 'left', cards)?.name, 'B');
    assert.equal(pickInDirection(at(cards, 'F'), 'up', cards)?.name, 'C');
});

test('en el borde no hay nada: la última columna no tiene nada a la derecha', () => {
    const cards = grid();
    assert.equal(pickInDirection(at(cards, 'C'), 'right', cards), null);
    assert.equal(pickInDirection(at(cards, 'I'), 'down', cards), null);
    assert.equal(pickInDirection(at(cards, 'A'), 'up', cards), null);
});

test('no hace wrap: al final de una fila se baja, no se vuelve al principio', () => {
    const cards = grid();
    assert.equal(pickInDirection(at(cards, 'C'), 'right', cards), null);
    assert.equal(pickInDirection(at(cards, 'C'), 'down', cards)?.name, 'F');
});

test('grilla escalonada: gana la más solapada', () => {
    const origin: Box = { left: 0, top: 0, width: 200, height: 100 };
    const near: Card = { name: 'near', left: 220, top: 40, width: 200, height: 100 };
    const far: Card = { name: 'far', left: 220, top: 300, width: 200, height: 100 };
    assert.equal(pickInDirection(origin, 'right', [near, far])?.name, 'near');
});

test('sin solapamiento acepta un vecino apenas desalineado (dentro del cono)', () => {
    const origin: Box = { left: 0, top: 0, width: 100, height: 40 };
    const offset: Card = { name: 'offset', left: 150, top: 50, width: 100, height: 40 };
    assert.equal(pickInDirection(origin, 'right', [offset])?.name, 'offset');
});

test('desde el botón Jugar del banner se sale hacia abajo, no hacia la derecha', () => {
    // La sección siguiente está mucho más abajo que a la derecha: es "abajo".
    const origin: Box = { left: 60, top: 300, width: 160, height: 48 };
    const nextSection: Card = { name: 'carousel', left: 80, top: 900, width: 240, height: 360 };
    assert.equal(pickInDirection(origin, 'right', [nextSection]), null);
    assert.equal(pickInDirection(origin, 'down', [nextSection])?.name, 'carousel');
});

test('derecha al final de una fila del grid no salta a una sección de arriba', () => {
    // Bug real: la tarjeta recortada del carrusel (fuera del viewport, arriba a
    // la derecha) era el "más cercano" sin banda y la página saltaba hacia arriba.
    const lastColumn: Card = { name: 'last', left: 1180, top: 300, width: 200, height: 280 };
    const previous: Card = { name: 'prev', left: 960, top: 300, width: 200, height: 280 };
    const nextRow: Card = { name: 'next-row', left: 300, top: 620, width: 200, height: 280 };
    const carouselHidden: Card = { name: 'carousel', left: 1500, top: -900, width: 220, height: 300 };
    const cards = [lastColumn, previous, nextRow, carouselHidden];
    assert.equal(pickInDirection(lastColumn, 'right', cards), null);
});

test('un contenedor grande no se traga a los vecinos chicos de su propia fila', () => {
    // Banner de pantalla completa con el logo y el botón de búsqueda arriba.
    const banner: Card = { name: 'banner', left: 0, top: 0, width: 1920, height: 800 };
    const logo: Card = { name: 'logo', left: 40, top: 24, width: 180, height: 48 };
    const search: Card = { name: 'search', left: 260, top: 24, width: 180, height: 48 };
    const cards = [banner, logo, search];

    assert.equal(pickInDirection(logo, 'right', cards)?.name, 'search');
    assert.equal(pickInDirection(search, 'left', cards)?.name, 'logo');
});

test('gana el más cercano, no el mejor alineado a la distancia', () => {
    // El vecino pegado está desalineado; el de la derecha es casi dos filas más
    // abajo pero cae justo en la misma banda vertical.
    const origin: Box = { left: 0, top: 0, width: 100, height: 200 };
    const near: Card = { name: 'near', left: 120, top: 190, width: 100, height: 40 };
    const far: Card = { name: 'far', left: 170, top: 80, width: 100, height: 40 };
    assert.equal(pickInDirection(origin, 'right', [far, near])?.name, 'near');
});

test('un chip bajo y angosto también es navegable', () => {
    const origin: Box = { left: 0, top: 0, width: 120, height: 40 };
    const chip: Card = { name: 'chip', left: 140, top: 4, width: 60, height: 32 };
    assert.equal(pickInDirection(origin, 'right', [chip]), chip);
});

test('la tarjeta del origen no se elige a sí misma', () => {
    const cards = grid();
    assert.equal(pickInDirection(at(cards, 'B'), 'right', cards)?.name, 'C');
});

test('el origen puede venir aparte de la lista de candidatos', () => {
    const cards = grid();
    assert.equal(pickInDirection(at(cards, 'A'), 'down', cards.slice(1))?.name, 'D');
});

test('a igual distancia gana el primero de la lista', () => {
    const origin: Box = { left: 0, top: 0, width: 200, height: 100 };
    const first: Card = { name: 'first', left: 220, top: 0, width: 200, height: 100 };
    const second: Card = { name: 'second', left: 220, top: 0, width: 200, height: 100 };
    assert.equal(pickInDirection(origin, 'right', [first, second]), first);
});

test('sin candidatos devuelve null en vez de lanzar', () => {
    const origin: Box = { left: 0, top: 0, width: 200, height: 100 };
    assert.equal(pickInDirection(origin, 'down', []), null);
});