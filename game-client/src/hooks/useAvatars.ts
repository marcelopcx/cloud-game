import { useEffect, useState } from 'react';
import { listAvatars } from '@services';
import type { Avatar } from '@/types';

/** Avatares que puede elegir el jugador; `enabled` en false no los pide (p. ej. selector cerrado). */
export const useAvatars = (enabled = true) => {
    const [avatars, setAvatars] = useState<Avatar[] | null>(null);
    const [error, setError] = useState(false);

    useEffect(() => {
        if (!enabled) return;
        const controller = new AbortController();
        listAvatars(controller.signal)
            .then((list) => {
                setAvatars(list);
                setError(false);
            })
            .catch((err: Error) => {
                if (err.name !== 'AbortError') setError(true);
            });
        return () => controller.abort();
    }, [enabled]);

    return { avatars, error };
};
