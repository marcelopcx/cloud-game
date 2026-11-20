/**
 * Captura de ratón para el datagrama de input WebRTC.
 *
 * Modo normal: posición absoluta en píxeles del juego; con pointer lock, deltas relativos.
 * Modo look (Xonotic / relativeMouse): siempre envía deltas relativos (abs=false).
 */

export type MouseHud = {
    x: number;
    y: number;
    buttons: number;
    locked: boolean;
};

export type MouseSample = {
    abs: boolean;
    x: number;
    y: number;
    buttons: number;
    wheel: number;
    locked: boolean;
};

/** Resolución del juego en la estación (STATE/health {@code gameWidth}/{@code gameHeight}). */
export type GameSize = { width: number; height: number };

/**
 * Punto del cliente → píxel del juego. El video se muestra con
 * {@code object-fit: contain} (con bandas), y el stream puede venir más chico
 * que el juego (STATION_STREAM_SIZE), así que se escala al tamaño del juego y
 * no a {@code videoWidth}/{@code videoHeight}.
 */
export function mapContainPoint(
    rect: { left: number; top: number; width: number; height: number },
    streamW: number,
    streamH: number,
    clientX: number,
    clientY: number,
    target: GameSize,
): { x: number; y: number } | null {
    if (!rect.width || !rect.height || !streamW || !streamH) return null;
    const scale = Math.min(rect.width / streamW, rect.height / streamH);
    const contentW = streamW * scale;
    const contentH = streamH * scale;
    const padX = (rect.width - contentW) / 2;
    const padY = (rect.height - contentH) / 2;
    const u = (clientX - rect.left - padX) / contentW;
    const v = (clientY - rect.top - padY) / contentH;
    const x = Math.floor(u * target.width);
    const y = Math.floor(v * target.height);
    return {
        x: Math.max(0, Math.min(target.width - 1, x)),
        y: Math.max(0, Math.min(target.height - 1, y)),
    };
}

export function mapVideoCoords(
    video: HTMLVideoElement,
    clientX: number,
    clientY: number,
    game?: GameSize | null,
): { x: number; y: number } | null {
    const vw = video.videoWidth || 1280;
    const vh = video.videoHeight || 720;
    // Sin tamaño de juego (estación vieja) se asume stream = juego, como antes.
    const target = game && game.width > 0 && game.height > 0 ? game : { width: vw, height: vh };
    return mapContainPoint(video.getBoundingClientRect(), vw, vh, clientX, clientY, target);
}

const LOCK_OPTS: PointerLockOptions = { unadjustedMovement: true };
const MAX_AXIS = 32767;

type LockDoc = Document & {
    mozPointerLockElement?: Element | null;
    webkitPointerLockElement?: Element | null;
};

function activeLockElement(): Element | null {
    const doc = document as LockDoc;
    return document.pointerLockElement ?? doc.mozPointerLockElement ?? doc.webkitPointerLockElement ?? null;
}

function isUiChrome(target: EventTarget | null): boolean {
    return (
        target instanceof Element
        && Boolean(target.closest('.game-view__bar, .game-view__unmute, .game-view__fs, .menu-option-game'))
    );
}

function clampAxis(v: number): number {
    const n = Math.round(v);
    return Math.max(-MAX_AXIS, Math.min(MAX_AXIS, n));
}

export function attachPointer(opts: {
    video: HTMLVideoElement;
    /** Xonotic y juegos con look por X11 / SDL relativo. */
    lookMode?: boolean;
    /** Tamaño del juego; puede llegar después del attach (evento STATE). */
    gameSize?: () => GameSize | null;
    /** Toggle del menú de video: en false la captura ignora el mouse. */
    enabled?: () => boolean;
    onMouse?: (state: MouseHud | null) => void;
    onLock?: (locked: boolean) => void;
    onChange?: () => void;
    onLog?: (line: string) => void;
}) {
    const lookMode = Boolean(opts.lookMode);
    const surface: HTMLElement = opts.video.closest('.game-view') ?? opts.video;
    const mouseWanted = () => opts.enabled?.() ?? true;

    let pendingX = 0;
    let pendingY = 0;
    let wheel = 0;
    let absX = 0;
    let absY = 0;
    let heldMask = 0;
    let engaged = false;
    let locked = false;
    let lastHud = 0;
    /** Último delta enviado al wire (para revertir si falla el datagrama). */
    let lastWireDx = 0;
    let lastWireDy = 0;
    let lastWireWheel = 0;
    /** Diagnóstico: una línea por evento, para ver dónde se corta el movimiento. */
    let loggedIdleMove = false;
    let loggedMove = false;
    let loggedWire = false;

    /** Fallback sin pointer lock: última posición en coords de video. */
    let trackX = 0;
    let trackY = 0;
    let tracking = false;

    const lockOnSurface = (): boolean => {
        const el = activeLockElement();
        if (!el) return false;
        return el === surface || el === opts.video || surface.contains(el);
    };

    const syncLock = () => {
        const now = lockOnSurface();
        if (now === locked) return;
        locked = now;
        opts.onLog?.(locked ? 'pointer lock ON' : 'pointer lock OFF');
        opts.onLock?.(locked);
        if (locked) {
            tracking = false;
        }
        pulseHud(true);
        opts.onChange?.();
    };

    const addPending = (dx: number, dy: number) => {
        if (!dx && !dy) return;
        pendingX += dx;
        pendingY += dy;
        pulseHud();
        opts.onChange?.();
    };

    const pulseHud = (force = false) => {
        const now = performance.now();
        if (!force && now - lastHud < 50) return;
        lastHud = now;
        opts.onMouse?.({
            x: lookMode || locked ? pendingX : absX,
            y: lookMode || locked ? pendingY : absY,
            buttons: heldMask,
            locked: lockOnSurface(),
        });
    };

    const requestLock = () => {
        if (lockOnSurface()) {
            syncLock();
            return;
        }
        const target = surface;
        target.focus();
        const run = () => {
            if (target.requestPointerLock) {
                return target.requestPointerLock(LOCK_OPTS);
            }
            const legacy = target as HTMLElement & {
                mozRequestPointerLock?: () => void;
                webkitRequestPointerLock?: () => void;
            };
            legacy.mozRequestPointerLock?.();
            legacy.webkitRequestPointerLock?.();
            return Promise.resolve();
        };
        void run()
            .then(() => {
                syncLock();
                if (lockOnSurface()) {
                    opts.onLog?.('pointer lock activo — mové el mouse');
                }
            })
            .catch((err: unknown) => {
                const msg = err instanceof Error ? err.message : String(err);
                opts.onLog?.(`pointer lock falló: ${msg} (seguí con modo relativo sin lock)`);
            });
    };

    const onDocumentMove = (ev: Event) => {
        if (!engaged) {
            if (!loggedIdleMove) {
                loggedIdleMove = true;
                opts.onLog?.('mouse move ignorado: falta click sobre el juego');
            }
            return;
        }
        const pe = ev as PointerEvent;
        if (!mouseWanted()) return;
        if (isUiChrome(pe.target)) return;

        syncLock();
        const isLocked = lockOnSurface();
        const mx = pe.movementX || 0;
        const my = pe.movementY || 0;
        if (!loggedMove) {
            loggedMove = true;
            const target = pe.target instanceof Element ? pe.target.className || pe.target.tagName : String(pe.target);
            opts.onLog?.(`mouse move locked=${isLocked} look=${lookMode} movement=${mx},${my} target=${target}`);
        }

        if (isLocked && (mx || my)) {
            addPending(mx, my);
            return;
        }

        if (lookMode) {
            if (mx || my) {
                addPending(mx, my);
                return;
            }
            const pt = mapVideoCoords(opts.video, pe.clientX, pe.clientY, opts.gameSize?.());
            if (!pt) return;
            absX = pt.x;
            absY = pt.y;
            if (tracking) {
                addPending(pt.x - trackX, pt.y - trackY);
            }
            trackX = pt.x;
            trackY = pt.y;
            tracking = true;
            return;
        }

        if (isLocked && (mx || my)) {
            addPending(mx, my);
            return;
        }

        const pt = mapVideoCoords(opts.video, pe.clientX, pe.clientY, opts.gameSize?.());
        if (!pt) return;
        absX = pt.x;
        absY = pt.y;
        pulseHud();
        opts.onChange?.();
    };

    const setButton = (button: number, down: boolean) => {
        const bit = 1 << button;
        if (down) heldMask |= bit;
        else heldMask &= ~bit;
    };

    const onPointerDown = (ev: PointerEvent) => {
        if (!mouseWanted() || isUiChrome(ev.target) || ev.button > 4) return;
        ev.preventDefault();
        engaged = true;
        surface.focus();
        opts.video.focus();
        setButton(ev.button, true);

        const pt = mapVideoCoords(opts.video, ev.clientX, ev.clientY, opts.gameSize?.());
        if (pt) {
            absX = pt.x;
            absY = pt.y;
            trackX = pt.x;
            trackY = pt.y;
            tracking = true;
        }

        if (lookMode && ev.button === 0) {
            requestLock();
        }

        pulseHud(true);
        opts.onChange?.();
    };

    const onPointerUp = (ev: PointerEvent) => {
        if (ev.button > 4) return;
        setButton(ev.button, false);
        pulseHud(true);
        opts.onChange?.();
    };

    const onWheel = (ev: WheelEvent) => {
        if (!mouseWanted() || isUiChrome(ev.target)) return;
        const ticks = ev.deltaY === 0 ? 0 : ev.deltaY < 0 ? 1 : -1;
        if (!ticks) return;
        wheel += ticks;
        opts.onChange?.();
        ev.preventDefault();
    };

    const onContextMenu = (ev: Event) => {
        if (!isUiChrome(ev.target)) ev.preventDefault();
    };

    const onLockChange = () => syncLock();

    document.addEventListener('pointermove', onDocumentMove, { capture: true, passive: true });
    surface.addEventListener('pointerdown', onPointerDown);
    window.addEventListener('pointerup', onPointerUp);
    surface.addEventListener('wheel', onWheel, { passive: false });
    surface.addEventListener('contextmenu', onContextMenu);
    document.addEventListener('pointerlockchange', onLockChange);
    document.addEventListener('mozpointerlockchange', onLockChange);
    document.addEventListener('webkitpointerlockchange', onLockChange);

    syncLock();
    opts.onLog?.(
        lookMode
            ? 'mouse look — click en el juego; si el navegador lo permite, pointer lock'
            : 'mouse — mové sobre el video',
    );
    opts.onMouse?.({ x: 0, y: 0, buttons: 0, locked: false });

    return {
        /** Delta desde el último takeDelta (no acumulado entre ticks). */
        takeDelta: (): MouseSample => {
            const isLocked = lockOnSurface();
            let x: number;
            let y: number;
            let abs: boolean;
            if (lookMode || isLocked) {
                abs = false;
                x = clampAxis(pendingX);
                y = clampAxis(pendingY);
                pendingX -= x;
                pendingY -= y;
            } else {
                abs = true;
                x = absX;
                y = absY;
            }
            const w = Math.max(-8, Math.min(8, wheel | 0));
            wheel -= w;
            if (!loggedWire && (x || y)) {
                loggedWire = true;
                opts.onLog?.(`mouse wire abs=${abs} x=${x} y=${y}`);
            }
            lastWireDx = x;
            lastWireDy = y;
            lastWireWheel = w;
            return {
                abs,
                x,
                y,
                buttons: heldMask,
                wheel: w,
                locked: isLocked,
            };
        },
        restoreLastDelta: () => {
            if (lookMode || lockOnSurface()) {
                pendingX += lastWireDx;
                pendingY += lastWireDy;
            }
            wheel += lastWireWheel;
            lastWireDx = 0;
            lastWireDy = 0;
            lastWireWheel = 0;
            opts.onChange?.();
        },
        /** @deprecated use takeDelta */
        peek: (): MouseSample => {
            const isLocked = lockOnSurface();
            if (lookMode || isLocked) {
                return {
                    abs: false,
                    x: pendingX,
                    y: pendingY,
                    buttons: heldMask,
                    wheel,
                    locked: isLocked,
                };
            }
            return {
                abs: true,
                x: absX,
                y: absY,
                buttons: heldMask,
                wheel,
                locked: false,
            };
        },
        consumeMotion: () => {
            /* no-op: takeDelta ya drena pending */
        },
        requestLock,
        exitLock: () => {
            if (lockOnSurface()) document.exitPointerLock();
        },
        stop: () => {
            document.removeEventListener('pointermove', onDocumentMove, { capture: true });
            surface.removeEventListener('pointerdown', onPointerDown);
            window.removeEventListener('pointerup', onPointerUp);
            surface.removeEventListener('wheel', onWheel);
            surface.removeEventListener('contextmenu', onContextMenu);
            document.removeEventListener('pointerlockchange', onLockChange);
            document.removeEventListener('mozpointerlockchange', onLockChange);
            document.removeEventListener('webkitpointerlockchange', onLockChange);
            if (lockOnSurface()) document.exitPointerLock();
            engaged = false;
            heldMask = 0;
            pendingX = 0;
            pendingY = 0;
            wheel = 0;
            tracking = false;
            opts.onLock?.(false);
            opts.onMouse?.(null);
        },
    };
}

/** @deprecated use lookMode */
export type AttachPointerOpts = Parameters<typeof attachPointer>[0];
