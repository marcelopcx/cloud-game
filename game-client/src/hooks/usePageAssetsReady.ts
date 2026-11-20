import { useEffect, useState, type RefObject } from 'react';

/** Tope de espera: una imagen colgada no deja el splash puesto para siempre. */
const MAX_WAIT_MS = 30_000;
const CSS_URL = /url\(["']?([^"')]+)["']?\)/g;

/** Una promesa por URL: si la misma imagen aparece en varios lugares, se espera una vez. */
const loads = new Map<string, Promise<void>>();

/** Descarga y decodifica la imagen (comparte la petición y la caché con el <img> de la página). Un error cuenta como listo. */
const loadImage = (src: string): Promise<void> => {
    let pending = loads.get(src);
    if (!pending) {
        const img = new Image();
        img.src = src;
        pending = img.decode().catch(() => undefined);
        loads.set(src, pending);
    }
    return pending;
};

/** URLs de imagen de la página: <img> (las lazy pasan a eager para que bajen ya) y fondos CSS. */
const pageImageUrls = (root: HTMLElement): string[] => {
    const urls = new Set<string>();
    root.querySelectorAll('img').forEach((img) => {
        if (img.loading === 'lazy') img.loading = 'eager';
        const src = img.currentSrc || img.src;
        if (src) urls.add(src);
    });
    root.querySelectorAll<HTMLElement>('*').forEach((el) => {
        const background = getComputedStyle(el).backgroundImage;
        if (background && background !== 'none') {
            for (const match of background.matchAll(CSS_URL)) urls.add(new URL(match[1], document.baseURI).href);
        }
    });
    return [...urls];
};

const nextFrame = () => new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));

/**
 * true cuando la página dentro de `rootRef` terminó de cargar: después de que `dataReady` avise que
 * llegaron los datos (y React los pintó), espera las fuentes y todas las imágenes, y vuelve a revisar
 * por si al cargar aparecieron más. Lo usa el splash para no dejar entrar a un home a medio cargar.
 */
export const usePageAssetsReady = (rootRef: RefObject<HTMLElement | null>, dataReady: boolean) => {
    const [ready, setReady] = useState(false);

    useEffect(() => {
        if (!dataReady || ready) return;
        let cancelled = false;
        const timeout = window.setTimeout(() => {
            console.warn(`splash: la página no terminó de cargar en ${MAX_WAIT_MS / 1000} s; se deja pasar igual`);
            if (!cancelled) setReady(true);
        }, MAX_WAIT_MS);

        void (async () => {
            await document.fonts?.ready;
            const seen = new Set<string>();
            for (;;) {
                await nextFrame();
                const root = rootRef.current;
                if (!root || cancelled) break;
                const fresh = pageImageUrls(root).filter((url) => !seen.has(url));
                if (fresh.length === 0) break;
                fresh.forEach((url) => seen.add(url));
                await Promise.all(fresh.map(loadImage));
            }
            if (!cancelled) {
                window.clearTimeout(timeout);
                setReady(true);
            }
        })();

        return () => {
            cancelled = true;
            window.clearTimeout(timeout);
        };
    }, [dataReady, ready, rootRef]);

    return ready;
};
