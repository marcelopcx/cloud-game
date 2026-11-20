import { useEffect, useState } from "react";
import { listCategoryGames, searchGames, SEARCH_MAX_LIMIT } from "@services";
import type { Game } from "@/types";

export interface GameFilter {
    query: string;
    categoryId: number | null;
}

export interface FilteredGames {
    /** null mientras carga (o si no hay filtro: se usa la lista completa). */
    games: Game[] | null;
    error: unknown;
    /** La búsqueda devolvió el máximo: puede haber más coincidencias. */
    truncated: boolean;
}

/**
 * Juegos que cumplen el filtro, pedidos a game-crud: GET /games/search para el texto
 * y GET /categories/{id}/games para la categoría. Con los dos, la intersección.
 * Sin filtro no pide nada (active = false).
 */
export function useFilteredGames({ query, categoryId }: GameFilter, refreshKey: unknown) {
    const q = query.trim();
    const active = q !== "" || categoryId !== null;
    const key = `${q}|${categoryId}|${String(refreshKey)}`;
    const [result, setResult] = useState<FilteredGames & { key: string }>({
        key: "", games: null, error: null, truncated: false,
    });

    useEffect(() => {
        if (!active) return;
        const controller = new AbortController();
        const { signal } = controller;
        Promise.all([
            q ? searchGames(q, SEARCH_MAX_LIMIT, signal) : null,
            categoryId !== null ? listCategoryGames(categoryId, signal) : null,
        ])
            .then(([found, inCategory]) => {
                let games = found ?? inCategory ?? [];
                if (found && inCategory) {
                    const ids = new Set(inCategory.map((g) => g.id));
                    games = found.filter((g) => ids.has(g.id));
                }
                const truncated = found !== null && found.length >= SEARCH_MAX_LIMIT;
                setResult({ key, games, error: null, truncated });
            })
            .catch((error: Error) => {
                if (error.name !== "AbortError") setResult({ key, games: null, error, truncated: false });
            });
        return () => controller.abort();
    }, [active, q, categoryId, key]);

    // Mientras llega la respuesta del filtro nuevo, no se muestran resultados viejos.
    const current = result.key === key ? result : { games: null, error: null, truncated: false };
    return { active, ...current };
}
