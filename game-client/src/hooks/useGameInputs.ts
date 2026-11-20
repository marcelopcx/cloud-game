import { useCallback, useEffect, useState } from 'react';
import { getGameInputs } from '@services';
import type { GameInputResponse } from '@/types';

export type GameInputsStatus = 'loading' | 'ready' | 'error';

/**
 * Controles del juego (acción → tecla/botón) de game-crud. PlayerScreen lo monta mientras se
 * crea el contenedor, así el modal de controles ya los tiene al abrirse.
 */
export const useGameInputs = (gameId: string) => {
    const [inputs, setInputs] = useState<GameInputResponse[]>([]);
    const [status, setStatus] = useState<GameInputsStatus>('loading');
    const [attempt, setAttempt] = useState(0);

    useEffect(() => {
        const abort = new AbortController();
        getGameInputs(gameId, abort.signal)
            .then((list) => {
                setInputs(list);
                setStatus('ready');
            })
            .catch((err: unknown) => {
                if (abort.signal.aborted) return;
                console.error('controles:', err);
                setStatus('error');
            });
        return () => abort.abort();
    }, [gameId, attempt]);

    const retry = useCallback(() => {
        setStatus('loading');
        setAttempt((n) => n + 1);
    }, []);

    return { inputs, status, retry };
};

export type GameInputs = ReturnType<typeof useGameInputs>;
