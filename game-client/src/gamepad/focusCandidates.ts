/**
 * Elementos que el foco por mando puede visitar.
 *
 * No hay que registrar ningún componente: todo lo que ya es enfocable con el
 * teclado entra en el recorrido (botones, `CardGame` con `role="button"`, los
 * chips del filtro, las flechas del carrusel, el logo). Los subárboles `inert`
 * quedan fuera solos, así que el `Modal` ya acota el recorrido sin código extra.
 */

/**
 * El input del buscador sí se enfoca con el mando —hay que llegar a él para
 * buscar— pero escribir sigue necesitando teclado físico, porque la Gamepad API
 * no trae entrada de texto. Los demás campos de texto quedan fuera: llegar a
 * ellos sin poder escribir sería un callejón sin salida.
 */
const FOCUSABLE = [
    'a[href]',
    'button:not([disabled])',
    'input[type="search"]:not([disabled])',
    '[role="button"]',
    '[tabindex]:not([tabindex="-1"])',
].join(',');

const TEXT_ENTRY = 'input:not([type="search"]), textarea, select, [contenteditable]';

/**
 * Zonas que no le competen al mando. Se sacan solo del recorrido del mando:
 * siguen siendo clicables y tabulables con ratón y teclado físico.
 * - `footer`: enlaces sociales, legales y de contacto; son del sitio, no del juego.
 */
const NOT_MINE = 'footer';

/**
 * Enfocables que no se activan. `<video>` y el contenedor del player son
 * `tabindex="0"` a pantalla completa (para el foco del juego y el pointer lock):
 * son un anillo enorme sobre algo sin acción. El input del juego va por el
 * DataChannel, no por el foco del DOM, así que excluirlos no le quita nada.
 */
const NOT_ACTIONABLE = 'video, .game-view';

/** `DOMRectList` no es un array: se recorre por índice. */
function hasArea(element: Element): boolean {
    const rects = element.getClientRects();
    for (let index = 0; index < rects.length; index += 1) {
        if (rects[index].width > 0 && rects[index].height > 0) return true;
    }
    return false;
}

export function isNavTarget(element: Element): boolean {
    if (element.matches(TEXT_ENTRY)) return false;
    if (element.matches(NOT_MINE)) return false;
    if (element.matches(NOT_ACTIONABLE)) return false;
    if (element.hasAttribute('disabled')) return false;
    // `inert` lo usa Modal para el fondo y MenuOptionGame para la lista cerrada.
    if (element.closest('[inert]')) return false;
    if (element.closest('[aria-hidden="true"]')) return false;
    // Un ancestro de las zonas anteriores también queda fuera.
    if (element.closest(NOT_MINE)) return false;

    // Fuera del viewport sí entra: su rect es válido y es lo que permite bajar
    // al grid sin scrollear antes. Lo que no tiene área no se puede ver ni enfocar.
    if (!hasArea(element)) return false;

    // `visibility: hidden` conserva el rectángulo, así que hay que mirarlo aparte.
    const view = element.ownerDocument.defaultView;
    return view?.getComputedStyle(element).visibility !== 'hidden';
}

export function collectFocusables(root: ParentNode = document): HTMLElement[] {
    return Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(isNavTarget);
}

/** Holgura para los bordes redondeados de subpíxel. */
const CLIP_TOLERANCE = 1;

function clips(element: Element, cache: Map<Element, boolean>): boolean {
    let value = cache.get(element);
    if (value === undefined) {
        const style = getComputedStyle(element);
        value = style.overflowX !== 'visible' || style.overflowY !== 'visible';
        cache.set(element, value);
    }
    return value;
}

/**
 * Contenedor con overflow (el track del carrusel, el filtro de géneros) que
 * deja al elemento total o parcialmente fuera de su área visible, o `null`.
 *
 * Esos elementos tienen un rect válido pero no se ven: solo se puede llegar a
 * ellos desde dentro del mismo contenedor (pasar a la tarjeta siguiente del
 * carrusel), nunca desde otra sección de la página.
 *
 * `cache` evita repetir `getComputedStyle` sobre los mismos ancestros cuando se
 * recorren todos los candidatos de una vez.
 */
export function clippingContainer(
    element: Element,
    cache: Map<Element, boolean> = new Map(),
): Element | null {
    const rect = element.getBoundingClientRect();
    const { body, documentElement } = element.ownerDocument;
    for (let parent = element.parentElement; parent; parent = parent.parentElement) {
        if (parent === body || parent === documentElement) return null;
        if (!clips(parent, cache)) continue;
        const box = parent.getBoundingClientRect();
        if (
            rect.left < box.left - CLIP_TOLERANCE ||
            rect.top < box.top - CLIP_TOLERANCE ||
            rect.right > box.right + CLIP_TOLERANCE ||
            rect.bottom > box.bottom + CLIP_TOLERANCE
        ) {
            return parent;
        }
    }
    return null;
}

function scrollsY(element: Element): boolean {
    const { overflowY } = getComputedStyle(element);
    return (overflowY === 'auto' || overflowY === 'scroll') && element.scrollHeight > element.clientHeight + 1;
}

/**
 * Lo que mueve el stick derecho: el primer ancestro del foco con scroll vertical;
 * si hay un diálogo abierto, el área con scroll del diálogo aunque el foco esté
 * en su botón de cerrar (la tabla de controles no tiene nada enfocable). Si no
 * hay nada de eso, la página; con un diálogo abierto, nada (`null`): el
 * `overflow: hidden` del fondo no frena el scroll programático y se correría la
 * página tapada.
 */
export function verticalScroller(from: Element | null): Element | null {
    const dialogs = document.querySelectorAll('[role="dialog"]:not(.is-closing)');
    const dialog = dialogs[dialogs.length - 1] ?? null;
    for (let parent = from; parent; parent = parent.parentElement) {
        if (parent === document.body) break;
        if (scrollsY(parent)) return parent;
    }
    if (dialog) {
        if (scrollsY(dialog)) return dialog;
        return Array.from(dialog.querySelectorAll('*')).find(scrollsY) ?? null;
    }
    return document.scrollingElement ?? document.documentElement;
}