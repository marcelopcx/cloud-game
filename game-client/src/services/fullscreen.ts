type FsDoc = Document & {
    webkitFullscreenElement?: Element | null;
    webkitExitFullscreen?: () => Promise<void> | void;
};

type FsEl = HTMLElement & {
    webkitRequestFullscreen?: () => Promise<void> | void;
};

type FsVideo = HTMLVideoElement & {
    webkitEnterFullscreen?: () => void;
    webkitExitFullscreen?: () => void;
    webkitDisplayingFullscreen?: boolean;
};

export function activeFullscreenEl(): Element | null {
    const doc = document as FsDoc;
    return document.fullscreenElement ?? doc.webkitFullscreenElement ?? null;
}

export async function enterFullscreen(container: HTMLElement, video: HTMLVideoElement | null) {
    const node = container as FsEl;
    try {
        if (node.requestFullscreen) {
            await node.requestFullscreen();
            return;
        }
        if (node.webkitRequestFullscreen) {
            await node.webkitRequestFullscreen();
            return;
        }
    } catch {
        /* el embed puede bloquear Fullscreen API */
    }
    try {
        if (video?.requestFullscreen) {
            await video.requestFullscreen();
            return;
        }
    } catch {
        /* fallback iOS / webkit */
    }
    (video as FsVideo | null)?.webkitEnterFullscreen?.();
}

export async function exitFullscreen(video: HTMLVideoElement | null = null) {
    const doc = document as FsDoc;
    try {
        if (document.fullscreenElement && document.exitFullscreen) {
            await document.exitFullscreen();
            return;
        }
        if (doc.webkitFullscreenElement && doc.webkitExitFullscreen) {
            await doc.webkitExitFullscreen();
            return;
        }
    } catch {
        /* ignore */
    }
    const v = video as FsVideo | null;
    if (v?.webkitDisplayingFullscreen) v.webkitExitFullscreen?.();
}

export function enterDocumentFullscreen(): Promise<void> {
    return enterFullscreen(document.documentElement, null);
}

type KeyboardLockNav = Navigator & {
    keyboard?: { lock?: (codes?: string[]) => Promise<void>; unlock?: () => void };
};

// Keyboard Lock API (Chromium, contexto seguro): en pantalla completa, Escape llega a la página
// en vez de salir. Mantenerlo apretado ~2 s sigue saliendo; eso lo impone el navegador.
export function lockEscapeKey() {
    void (navigator as KeyboardLockNav).keyboard?.lock?.(['Escape']).catch(() => undefined);
}

export function unlockEscapeKey() {
    (navigator as KeyboardLockNav).keyboard?.unlock?.();
}
