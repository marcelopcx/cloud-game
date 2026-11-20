/**
 * Navegación por mando de la interfaz.
 *
 * Se monta una sola vez en `app/App.tsx` y sirve para el home y para el player.
 * Mientras hay una partida con input en curso (`webrtc/session.ts`), se
 * desactiva sola: ahí el pad es del juego y la UI no se mueve. La excepción es
 * Home (Guide / PS): abre el menú de opciones del player, el mando lo navega
 * y otro Home (o B) lo cierra y le devuelve el mando al juego.
 *
 * No usa `:focus-visible` a propósito: el foco es programático y sin teclado de
 * por medio, así que ese selector no es confiable. El elemento enfocado se
 * marca con `data-gamepad-focus` y el anillo depende de `html[data-input]`.
 */

import { useCallback, useEffect, useRef, useSyncExternalStore, type ReactNode } from 'react';
import { clippingContainer, collectFocusables, isNavTarget, verticalScroller } from './focusCandidates.ts';
import { pickInDirection, type Box, type Dir } from './geometry.ts';
import { startUiPadReader, type UiPadEvent } from './padReader.ts';
import { isGamepadClaimedBySession, isGamepadOwnedByGame, subscribeGamepadClaim } from './sessionGate.ts';
import './gamepadNav.css';

const FOCUS_ATTR = 'data-gamepad-focus';
/** Altura de viewport que salta LB/RB cuando el foco no está en un carrusel o filtro. */
const PAGE_STEP = 0.9;
/** Holgura para que el anillo (outline + offset) no quede pegado al borde. */
const EDGE_MARGIN = 12;
/** Diálogo abierto (no el que está terminando su animación de salida). */
const OPEN_DIALOG = '[role="dialog"]:not(.is-closing)';
const OPTIONS_MENU = '.menu-option-game';

type Item = Box & { el: HTMLElement; clip: Element | null };

/** Candidatos con su rect y, si lo hay, el contenedor con overflow que los recorta. */
function collectItems(root?: ParentNode): Item[] {
    const cache = new Map<Element, boolean>();
    return collectFocusables(root).map((el) => {
        const rect = el.getBoundingClientRect();
        return {
            el,
            left: rect.left,
            top: rect.top,
            width: rect.width,
            height: rect.height,
            clip: clippingContainer(el, cache),
        };
    });
}

/** Entero en el viewport y sin recortar: se ve. */
function isFullyVisible(item: Item): boolean {
    return (
        !item.clip &&
        item.top >= 0 &&
        item.left >= 0 &&
        item.top + item.height <= window.innerHeight &&
        item.left + item.width <= window.innerWidth
    );
}

/** Al menos una parte en el viewport y sin recortar. */
function isOnScreen(item: Item): boolean {
    return (
        !item.clip &&
        item.top + item.height > 0 &&
        item.left + item.width > 0 &&
        item.top < window.innerHeight &&
        item.left < window.innerWidth
    );
}

/**
 * No queda nada de él a la vista: fuera del viewport o, si un contenedor lo
 * recorta, fuera del área de ese contenedor. Un elemento apenas recortado (el
 * snap del carrusel puede dejar un borde tapado) no cuenta como perdido.
 */
function isLost(item: Item): boolean {
    let { left, top } = item;
    let right = item.left + item.width;
    let bottom = item.top + item.height;
    if (item.clip) {
        const box = item.clip.getBoundingClientRect();
        left = Math.max(left, box.left);
        top = Math.max(top, box.top);
        right = Math.min(right, box.right);
        bottom = Math.min(bottom, box.bottom);
    }
    left = Math.max(left, 0);
    top = Math.max(top, 0);
    right = Math.min(right, window.innerWidth);
    bottom = Math.min(bottom, window.innerHeight);
    return right <= left || bottom <= top;
}

/** El candidato visible más cercano a un punto de la pantalla. */
function nearestVisible(items: Item[], x: number, y: number): Item | undefined {
    const visible = items.filter(isFullyVisible);
    const pool = visible.length > 0 ? visible : items.filter(isOnScreen);
    let best: Item | undefined;
    let bestDistance = Infinity;
    for (const item of pool) {
        const dx = item.left + item.width / 2 - x;
        const dy = item.top + item.height / 2 - y;
        const distance = Math.hypot(dx, dy);
        if (distance < bestDistance) {
            best = item;
            bestDistance = distance;
        }
    }
    return best;
}

function clamp(value: number, min: number, max: number): number {
    return Math.min(Math.max(value, min), max);
}

/**
 * Si el elemento necesita que se mueva algo para verse entero: la página o el
 * contenedor con overflow que lo recorta (una tarjeta del carrusel puede estar
 * dentro del viewport y aun así tapada por el borde del track).
 *
 * Hay que preguntarlo: llamar a `scrollIntoView` sobre un elemento que ya está a
 * la vista igual mueve algo, y el carrusel y el filtro de géneros tienen
 * `scroll-snap-type`, así que el navegador re-ancla después y la pantalla da
 * un tirón. Sumado al scroll del stick, eso se ve como una vibración.
 */
function needsScroll(el: HTMLElement): boolean {
    const rect = el.getBoundingClientRect();
    return (
        rect.top < EDGE_MARGIN ||
        rect.left < EDGE_MARGIN ||
        rect.bottom > window.innerHeight - EDGE_MARGIN ||
        rect.right > window.innerWidth - EDGE_MARGIN ||
        clippingContainer(el) !== null
    );
}

/** `body` no cuenta como foco: nadie lo navega. */
function focused(): HTMLElement | null {
    const active = document.activeElement;
    return active instanceof HTMLElement && active !== document.body ? active : null;
}

function ringVisible(): boolean {
    return document.documentElement.dataset.input === 'gamepad';
}

/** Posición del elemento en el árbol (índice de hijo en cada nivel desde `body`). */
function domPath(el: Element): number[] {
    const path: number[] = [];
    for (let node: Element | null = el; node && node !== document.body; node = node.parentElement) {
        const parent: Element | null = node.parentElement;
        if (!parent) return [];
        path.unshift(Array.prototype.indexOf.call(parent.children, node));
    }
    return path;
}

function resolvePath(path: number[]): Element | null {
    let node: Element | null = document.body;
    for (const index of path) {
        node = node?.children[index] ?? null;
        if (!node) return null;
    }
    return node;
}

/**
 * Banner que se cruza al ir de `origin` hacia arriba o abajo antes de llegar a
 * `target`, con su acción (el botón Jugar), o `undefined`.
 *
 * El banner es una sección de ancho completo con una sola acción, y en la
 * variante highlight esa acción está a la derecha: desde una tarjeta o un chip
 * de la izquierda queda fuera del cono de `pickInDirection` y el foco se lo
 * saltaba. Al cruzar un banner en vertical el foco se detiene en él.
 */
function bannerBetween(origin: Item, target: Item | null, dir: 'up' | 'down', items: Item[]): Item | undefined {
    const originY = origin.top + origin.height / 2;
    const targetY = target ? target.top + target.height / 2 : null;
    let best: { item: Item; distance: number } | undefined;
    for (const banner of document.querySelectorAll('.banner')) {
        if (banner.contains(origin.el)) continue;
        const rect = banner.getBoundingClientRect();
        const edge = dir === 'down' ? rect.top : rect.bottom;
        const distance = dir === 'down' ? edge - originY : originY - edge;
        if (distance < 0) continue;
        // La geometría ya llega a este banner, o el destino está antes que él.
        if (target && banner.contains(target.el)) continue;
        if (targetY !== null && (dir === 'down' ? targetY <= edge : targetY >= edge)) continue;
        const action = items.find((item) => !item.clip && banner.querySelector('.banner-body')?.contains(item.el));
        if (action && (!best || distance < best.distance)) best = { item: action, distance };
    }
    return best?.item;
}

/** Splash de entrada: mientras está, el home de abajo no se ve ni se navega. */
function splashElement(): HTMLElement | null {
    return document.querySelector<HTMLElement>('.splash-animation');
}

export const GamepadNav = ({ children }: { children?: ReactNode }) => {
    const claimedByGame = useSyncExternalStore(
        subscribeGamepadClaim,
        isGamepadOwnedByGame,
        isGamepadOwnedByGame,
    );
    const markedRef = useRef<HTMLElement | null>(null);
    /**
     * Último elemento enfocado fuera de un diálogo. Al volver del player el home
     * se monta de nuevo y el foco queda en `body`: con esto se retoma en la
     * tarjeta o el banner desde el que se lanzó el juego, no en el logo.
     */
    const lastPathRef = useRef<number[] | null>(null);

    const markFocused = useCallback((el: HTMLElement) => {
        if (markedRef.current !== el) {
            markedRef.current?.removeAttribute(FOCUS_ATTR);
            el.setAttribute(FOCUS_ATTR, '');
            markedRef.current = el;
        }
        if (!el.closest('[role="dialog"]')) lastPathRef.current = domPath(el);
    }, []);

    const unmark = useCallback(() => {
        markedRef.current?.removeAttribute(FOCUS_ATTR);
        markedRef.current = null;
    }, []);

    const focusItem = useCallback(
        (el: HTMLElement, inline: ScrollLogicalPosition = 'nearest') => {
            document.documentElement.dataset.input = 'gamepad';
            el.focus({ preventScroll: true });
            // 'instant' y no 'auto': `auto` respeta el `scroll-behavior: smooth` de
            // algunos contenedores (el filtro, el carrusel) y el ajuste se repartiría
            // en varios cuadros, que es justo lo que se ve como vibración.
            if (needsScroll(el)) {
                el.scrollIntoView({ block: 'nearest', inline, behavior: 'instant' });
            }
            markFocused(el);
        },
        [markFocused],
    );

    /**
     * Sin foco navegable (recién cargado, de vuelta del player): el elemento
     * recordado si sigue existiendo; si no, el primero que se ve en pantalla.
     */
    const focusStart = useCallback(
        (items: Item[]) => {
            const remembered = lastPathRef.current ? resolvePath(lastPathRef.current) : null;
            const restored = items.find((item) => item.el === remembered);
            const target = restored ?? items.find(isFullyVisible) ?? items.find((item) => !item.clip) ?? items[0];
            if (target) focusItem(target.el);
        },
        [focusItem],
    );

    const move = useCallback(
        (dir: Dir) => {
            const items = collectItems();
            if (items.length === 0) return;

            // El origen sale de la lista: si el foco está en algo que no se
            // navega (el video) la geometría sería de otro elemento.
            const active = focused();
            const origin = active ? items.find((item) => item.el === active) : undefined;
            if (!origin) {
                focusStart(items);
                return;
            }

            // Con el anillo oculto (se usó el mouse o el teclado) el primer toque
            // solo muestra dónde está el foco: moverlo a ciegas desorienta.
            if (!ringVisible()) {
                focusItem(origin.el);
                return;
            }

            // El foco quedó fuera de la vista (stick derecho, rueda del mouse,
            // carrusel corrido): se retoma desde lo que se ve, en el punto de la
            // pantalla más cercano a donde estaba, en vez de volver hacia atrás.
            if (isLost(origin)) {
                const x = clamp(origin.left + origin.width / 2, 0, window.innerWidth);
                const y = clamp(origin.top + origin.height / 2, 0, window.innerHeight);
                const anchor = nearestVisible(items, x, y);
                if (anchor) {
                    focusItem(anchor.el);
                    return;
                }
            }

            // Lo recortado por un contenedor (las tarjetas ocultas del carrusel)
            // solo se alcanza desde dentro de ese contenedor.
            const reachable = items.filter((item) => !item.clip || item.clip.contains(origin.el));
            const target = pickInDirection(origin, dir, reachable);
            const banner = dir === 'up' || dir === 'down' ? bannerBetween(origin, target, dir, items) : undefined;
            if (banner) focusItem(banner.el);
            else if (target) focusItem(target.el);
        },
        [focusItem, focusStart],
    );

    const activate = useCallback(() => {
        const active = focused();
        const items = collectItems();
        const current = active ? items.find((item) => item.el === active) : undefined;
        if (!current) {
            // Foco en nada navegable (body, el video, un enlace del footer
            // clicado con el mouse): A no lo activa, solo muestra el foco.
            focusStart(items);
            return;
        }
        // Con el anillo oculto A no activa algo que el usuario no ve enfocado.
        if (!ringVisible()) {
            focusItem(current.el);
            return;
        }
        current.el.click();
    }, [focusItem, focusStart]);

    /**
     * B / Circle: atrás. Por orden: cierra el diálogo abierto, cierra el menú
     * de opciones del player o usa el botón de volver de la pantalla (loader,
     * error de la estación). En el home no hace nada.
     */
    const back = useCallback(() => {
        const dialogs = document.querySelectorAll(OPEN_DIALOG);
        const dialog = dialogs[dialogs.length - 1];
        if (dialog) {
            dialog.querySelector<HTMLElement>('.modal-close')?.click();
            return;
        }
        const menuToggle = document.querySelector<HTMLElement>('.menu-option-game.is-open .menu-option-game__toggle');
        if (menuToggle) {
            menuToggle.click();
            return;
        }
        const backButton = Array.from(document.querySelectorAll<HTMLElement>('.back-button')).find(isNavTarget);
        backButton?.click();
    }, []);

    /**
     * Home / Guide: abre el menú de opciones del player con el foco en su primera
     * opción; con el menú abierto lo cierra, junto con el diálogo que se haya
     * abierto desde él (los controles). En el home no hay menú y no hace nada.
     */
    const home = useCallback(() => {
        const menu = document.querySelector<HTMLElement>(OPTIONS_MENU);
        const toggle = menu?.querySelector<HTMLElement>('.menu-option-game__toggle');
        if (!menu || !toggle) return;

        if (menu.classList.contains('is-open')) {
            document.querySelectorAll<HTMLElement>(`${OPEN_DIALOG} .modal-close`).forEach((close) => close.click());
            toggle.click();
            // De vuelta al juego el foco vuelve al video, como al arrancar el stream.
            if (isGamepadClaimedBySession()) {
                document.querySelector<HTMLElement>('.game-view video')?.focus({ preventScroll: true });
            }
            return;
        }

        toggle.click();
        // La lista deja de ser `inert` recién cuando React pinta el menú abierto.
        requestAnimationFrame(() => {
            const list = menu.querySelector('.menu-option-game__list');
            const first = list ? collectFocusables(list)[0] : undefined;
            focusItem(first ?? toggle);
        });
    }, [focusItem]);

    /**
     * LB / RB dentro de un carrusel o del filtro de géneros: el foco salta una
     * página de elementos y el contenedor la acompaña. Moviendo el foco (y no solo
     * el scroll) el siguiente ←/→ sigue desde la página nueva.
     *
     * En el borde no pasa nada: no hay página siguiente, y saltar a scrollear
     * toda la página sería un cambio de contexto que nadie pidió.
     */
    const pageWithin = useCallback(
        (group: Element, active: HTMLElement, delta: 1 | -1) => {
            const scroller = group.querySelector('.carousel-track, .category-filter');
            if (!scroller) return;

            const items = collectFocusables(scroller);
            const index = items.indexOf(active);
            if (index === -1) {
                // El foco está en una flecha: la flecha ya es "una página".
                const suffix = delta === 1 ? '--next' : '--prev';
                group.querySelector<HTMLElement>(`[class*="-arrow${suffix}"]:not([disabled])`)?.click();
                return;
            }

            const box = scroller.getBoundingClientRect();
            const perPage = Math.max(
                1,
                items.filter((el) => {
                    const rect = el.getBoundingClientRect();
                    return rect.left >= box.left - 1 && rect.right <= box.right + 1;
                }).length,
            );
            const target = items[clamp(index + delta * perPage, 0, items.length - 1)];
            if (target === active) return;
            focusItem(target, delta === 1 ? 'start' : 'end');
        },
        [focusItem],
    );

    /**
     * LB / RB fuera de un carrusel o filtro: una altura de viewport, y el foco
     * pasa al elemento visible más cercano al lugar de la pantalla donde estaba.
     */
    const page = useCallback(
        (delta: 1 | -1) => {
            const active = focused();
            const group = active?.closest('.carousel, .category-filter-container');
            if (active && group) {
                pageWithin(group, active, delta);
                return;
            }

            // La página, o con un diálogo abierto su área con scroll (nunca el fondo tapado).
            const view = verticalScroller(active);
            if (!view) return;
            const before = view.scrollTop;
            const anchor = active?.getBoundingClientRect();
            const height = view === document.scrollingElement ? window.innerHeight : view.clientHeight;
            view.scrollBy({ top: delta * height * PAGE_STEP, behavior: 'instant' });
            // Sin scroll (final de la página o del área) el foco no se toca.
            if (view.scrollTop === before) return;

            const x = anchor ? anchor.left + anchor.width / 2 : window.innerWidth / 2;
            const y = anchor ? clamp(anchor.top + anchor.height / 2, 0, window.innerHeight) : window.innerHeight / 2;
            const target = nearestVisible(collectItems(), x, y);
            if (target) focusItem(target.el);
        },
        [focusItem, pageWithin],
    );

    /** Stick derecho: scroll vertical continuo, de la página o del área del diálogo. */
    const scroll = useCallback((px: number) => {
        verticalScroller(focused())?.scrollBy({ top: px, behavior: 'instant' });
    }, []);

    const handleEvent = useCallback(
        (event: UiPadEvent) => {
            // Mientras está el splash el home de abajo no se ve: el mando solo
            // puede saltearlo (como el click), no navegar a ciegas por detrás.
            const splash = splashElement();
            if (event.type === 'home') {
                if (!splash) home();
                return;
            }
            // El mando es del juego: de la UI solo le queda Home.
            if (isGamepadOwnedByGame()) return;
            if (splash) {
                if (event.type === 'activate' || event.type === 'back') splash.click();
                return;
            }
            switch (event.type) {
                case 'dir':
                    move(event.dir);
                    return;
                case 'activate':
                    activate();
                    return;
                case 'back':
                    back();
                    return;
                case 'page':
                    page(event.delta);
                    return;
                case 'scroll':
                    scroll(event.px);
                    return;
            }
        },
        [move, activate, back, page, scroll, home],
    );

    // El lector corre siempre, también en partida: así ve el Home que abre el menú
    // y sigue los botones mantenidos, que al volver a la UI no disparan nada.
    useEffect(() => startUiPadReader({ onEvent: handleEvent }), [handleEvent]);

    useEffect(() => {
        if (!claimedByGame) return;
        // El mando pasa a ser del juego: el anillo se va con él y no vuelve por
        // un cambio de foco (el `video.focus()` del player) hasta que la UI lo
        // recupere y se vuelva a usar.
        unmark();
        delete document.documentElement.dataset.input;
    }, [claimedByGame, handleEvent, unmark]);

    // El foco puede cambiar sin pasar por el mando (al abrir un modal, `Modal`
    // enfoca su acción principal; al cerrarlo, devuelve el foco): el anillo tiene
    // que seguir al foco real o se queda en un elemento tapado.
    useEffect(() => {
        if (claimedByGame) return;
        const onFocusIn = (event: FocusEvent) => {
            if (!ringVisible()) return;
            const el = event.target;
            if (!(el instanceof HTMLElement)) return;
            // Lo que el mando no navega (el `<video>`, el footer) no lleva anillo.
            if (!isNavTarget(el)) {
                unmark();
                return;
            }
            markFocused(el);
        };
        document.addEventListener('focusin', onFocusIn);
        return () => document.removeEventListener('focusin', onFocusIn);
    }, [claimedByGame, markFocused, unmark]);

    // El anillo solo se dibuja mientras el mando es el dispositivo activo.
    useEffect(() => {
        const clear = () => {
            delete document.documentElement.dataset.input;
        };
        window.addEventListener('pointermove', clear, { passive: true });
        window.addEventListener('pointerdown', clear, { passive: true });
        window.addEventListener('keydown', clear);
        return () => {
            window.removeEventListener('pointermove', clear);
            window.removeEventListener('pointerdown', clear);
            window.removeEventListener('keydown', clear);
            delete document.documentElement.dataset.input;
        };
    }, []);

    return children;
};
