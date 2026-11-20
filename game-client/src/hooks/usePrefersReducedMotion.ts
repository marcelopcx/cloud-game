import { useSyncExternalStore } from 'react';

const QUERY = '(prefers-reduced-motion: reduce)';

/** Una sola MediaQueryList para toda la app: crear una por render es tirar. */
let cached: MediaQueryList | null = null;
const media = () => (cached ??= window.matchMedia(QUERY));

/** Si el sistema pide menos movimiento. Se queda escuchando porque el ajuste se puede cambiar con la partida en marcha. */
const subscribe = (onChange: () => void) => {
    const list = media();
    list.addEventListener('change', onChange);
    return () => list.removeEventListener('change', onChange);
};

const getSnapshot = () => media().matches;

export const usePrefersReducedMotion = () => useSyncExternalStore(subscribe, getSnapshot, () => false);