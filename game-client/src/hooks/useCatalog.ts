import { useCallback, useEffect, useState } from 'react';
import { listCatalogGames, type CatalogGame } from '@services';

export type CatalogStatus = 'loading' | 'ready' | 'error';

/** Catálogo de juegos de game-crud, pedido una vez al montar. */
export const useCatalog = () => {
    const [games, setGames] = useState<CatalogGame[]>([]);
    const [status, setStatus] = useState<CatalogStatus>('loading');
    const [attempt, setAttempt] = useState(0);

    useEffect(() => {
        const abort = new AbortController();
        listCatalogGames(abort.signal)
            .then((list) => {
                setGames(list);
                setStatus('ready');
            })
            .catch((err: unknown) => {
                if (abort.signal.aborted) return;
                console.error('catálogo:', err);
                setStatus('error');
            });
        return () => abort.abort();
    }, [attempt]);

    const retry = useCallback(() => {
        setStatus('loading');
        setAttempt((n) => n + 1);
    }, []);

    return { games, status, retry };
};
