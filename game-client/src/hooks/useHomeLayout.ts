import { useEffect, useState } from 'react';
import { getHomeLayout, type HomeSection } from '@services';

/**
 * Distribución del home de game-crud. null mientras carga; [] si no está configurada
 * o no se pudo pedir (el home usa entonces su distribución por defecto).
 */
export const useHomeLayout = () => {
    const [sections, setSections] = useState<HomeSection[] | null>(null);

    useEffect(() => {
        const abort = new AbortController();
        getHomeLayout(abort.signal)
            .then(setSections)
            .catch((err: unknown) => {
                if (abort.signal.aborted) return;
                console.error('home:', err);
                setSections([]);
            });
        return () => abort.abort();
    }, []);

    return sections;
};
