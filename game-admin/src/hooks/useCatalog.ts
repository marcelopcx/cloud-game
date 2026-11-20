import { useEffect, useState } from "react";
import { listCategories, listGames } from "@services";
import type { Category, Game } from "@/types";

export interface Catalog {
    games: Game[] | null;
    categories: Category[] | null;
    error: unknown;
}

/** GET /games y GET /categories. Se vuelven a pedir cuando cambia refreshKey (tras crear, editar o borrar). */
export function useCatalog(refreshKey: unknown): Catalog {
    const [catalog, setCatalog] = useState<Catalog>({ games: null, categories: null, error: null });

    useEffect(() => {
        const controller = new AbortController();
        Promise.all([listGames(controller.signal), listCategories(controller.signal)])
            .then(([games, categories]) => setCatalog({ games, categories, error: null }))
            .catch((error: Error) => {
                if (error.name !== "AbortError") setCatalog((c) => ({ ...c, error }));
            });
        return () => controller.abort();
    }, [refreshKey]);

    return catalog;
}
