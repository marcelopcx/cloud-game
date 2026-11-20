import { useEffect, useState } from 'react';
import { searchCatalogGames, type CatalogGame } from '@services';

const DEBOUNCE_MS = 250;

/** Descarga el ícono; resuelve también si falla (GameIcon ya muestra la inicial en ese caso). */
const preloadImage = (src?: string) =>
    new Promise<void>((resolve) => {
        if (!src) return resolve();
        const img = new Image();
        img.onload = img.onerror = () => resolve();
        img.src = src;
    });

/**
 * Resultados del buscador: pide /games/search tras una pausa al teclear y descarta respuestas viejas.
 * `loading` sigue activo hasta que llegan los resultados del texto actual (incluye la pausa e íconos).
 */
export const useGameSearch = (query: string) => {
    // Se guarda para qué texto son los resultados: si no coincide con el actual, aún se está buscando.
    const [resolved, setResolved] = useState<{ text: string; games: CatalogGame[] }>({ text: '', games: [] });
    const text = query.trim();

    useEffect(() => {
        if (!text) return;
        const abort = new AbortController();
        const timer = setTimeout(() => {
            searchCatalogGames(text, abort.signal)
                // Se espera a los íconos para que imagen y título aparezcan a la vez.
                .then(async (games) => {
                    await Promise.all(games.map((g) => preloadImage(g.details.icon)));
                    if (!abort.signal.aborted) setResolved({ text, games });
                })
                .catch((err: unknown) => {
                    if (abort.signal.aborted) return;
                    console.error('búsqueda:', err);
                    setResolved({ text, games: [] });
                });
        }, DEBOUNCE_MS);
        return () => {
            clearTimeout(timer);
            abort.abort();
        };
    }, [text]);

    const loading = text !== '' && resolved.text !== text;
    return { results: text && !loading ? resolved.games : [], loading };
};
