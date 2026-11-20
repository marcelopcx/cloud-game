/**
 * Navegación espacial de foco: elige el elemento siguiente en una dirección.
 * Geometría pura, sin DOM ni `window`, para poder testearla con `node --test`.
 */

export type Dir = 'up' | 'down' | 'left' | 'right';

export type Box = {
    left: number;
    top: number;
    width: number;
    height: number;
};

/** Avance mínimo en el eje de la dirección para no "quedarse en el lugar". */
const ADVANCE_EPS = 0.5;
/** Las diagonales cuestan un poco más que ir en línea recta. */
const DIAGONAL_WEIGHT = 0.4;

function centerX(box: Box): number {
    return box.left + box.width / 2;
}

function centerY(box: Box): number {
    return box.top + box.height / 2;
}

/** Centro en el eje de la dirección (X para izquierda/derecha, Y para arriba/abajo). */
function primary(box: Box, dir: Dir): number {
    return dir === 'left' || dir === 'right' ? centerX(box) : centerY(box);
}

/** Recorrido del origen del candidato en el eje perpendicular a la dirección. */
function secondarySpan(box: Box, dir: Dir): [number, number] {
    return dir === 'left' || dir === 'right'
        ? [box.top, box.top + box.height]
        : [box.left, box.left + box.width];
}

function advancesFrom(origin: Box, box: Box, dir: Dir): boolean {
    const step = dir === 'right' || dir === 'down' ? 1 : -1;
    return step * (primary(box, dir) - primary(origin, dir)) > ADVANCE_EPS;
}

/**
 * Distancia rectangular (dead reckoning): manda la mayor de las dos ejes y la
 * menor entra como penalización. Así el vecino de al lado gana siempre al
 * equivalente de dos secciones más allá, aunque este último esté mejor alineado.
 */
function ringDistance(origin: Box, box: Box): number {
    const dx = Math.abs(centerX(box) - centerX(origin));
    const dy = Math.abs(centerY(box) - centerY(origin));
    return Math.max(dx, dy) + DIAGONAL_WEIGHT * Math.min(dx, dy);
}

/**
 * Separación entre los recorridos perpendiculares del origen y el candidato:
 * 0 si se solapan, y si no, el hueco que queda entre ellos.
 */
function secondaryGap(origin: Box, box: Box, dir: Dir): number {
    const [originStart, originEnd] = secondarySpan(origin, dir);
    const [boxStart, boxEnd] = secondarySpan(box, dir);
    return Math.max(originStart, boxStart) - Math.min(originEnd, boxEnd);
}

/** Comparte banda con el origen en el eje perpendicular: es "la misma fila". */
function sharesBand(origin: Box, box: Box, dir: Dir): boolean {
    return secondaryGap(origin, box, dir) < 0;
}

/**
 * Está dentro del cono de 45° de la dirección: se corre más hacia donde se
 * apretó que hacia el costado. Fuera del cono, apretar "derecha" llevaría a
 * algo que está, sobre todo, arriba o abajo.
 */
function inCone(origin: Box, box: Box, dir: Dir): boolean {
    const advance = Math.abs(primary(box, dir) - primary(origin, dir));
    return Math.max(0, secondaryGap(origin, box, dir)) <= advance;
}

/**
 * Elemento al que ir con `dir` desde `origin`, buscando por anillos: entre los
 * que avanzan en la dirección, gana el más cercano que además comparta banda
 * con el origen.
 *
 * Si ninguno comparte banda se acepta el más cercano dentro del cono de 45°
 * (un vecino apenas desalineado). Lo que queda fuera del cono no cuenta: desde
 * el final de una fila del grid, "derecha" no puede saltar a una sección que
 * está muy arriba. Para eso están "arriba" y "abajo".
 *
 * No hay wrap: `down` desde la última fila se queda quieto, que es lo que se
 * espera en un grid.
 *
 * Devuelve el mismo objeto que se le pasó (comparación por referencia), para
 * que el llamador pueda mapear el rectángulo al elemento del DOM.
 */
export function pickInDirection<T extends Box>(origin: Box, dir: Dir, candidates: T[]): T | null {
    const advanced = candidates.filter((box) => box !== origin && advancesFrom(origin, box, dir));
    if (advanced.length === 0) return null;

    const ordered = advanced
        .slice()
        .sort((a, b) => ringDistance(origin, a) - ringDistance(origin, b));

    return (
        ordered.find((box) => sharesBand(origin, box, dir)) ??
        ordered.find((box) => inCone(origin, box, dir)) ??
        null
    );
}