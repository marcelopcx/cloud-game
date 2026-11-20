import type { Category } from "@/types";
import { CloseIcon, SearchIcon } from "../icons/Icons.tsx";
import "./toolbar.css";

export interface ToolbarProps {
    query: string;
    onQueryChange: (query: string) => void;
    categories: Category[] | null;
    /** Juegos por nombre de categoría (del listado completo), para el contador de cada chip. */
    counts: Map<string, number>;
    categoryId: number | null;
    onCategoryChange: (id: number | null) => void;
}

/** Buscador (GET /games/search) y filtro por categoría (GET /categories/{id}/games). */
export const Toolbar = ({ query, onQueryChange, categories, counts, categoryId, onCategoryChange }: ToolbarProps) => (
    <div className="toolbar">
        <div className="toolbar__search">
            <SearchIcon />
            <input
                type="search"
                placeholder="Buscar por nombre…"
                aria-label="Buscar juegos por nombre"
                value={query}
                onChange={(e) => onQueryChange(e.target.value)}
            />
            {query && (
                <button type="button" aria-label="Limpiar búsqueda" onClick={() => onQueryChange("")}>
                    <CloseIcon size={14} />
                </button>
            )}
        </div>

        <div className="toolbar__categories" role="group" aria-label="Filtrar por categoría">
            <button
                type="button"
                className="toolbar__chip"
                aria-pressed={categoryId === null}
                onClick={() => onCategoryChange(null)}
            >
                Todas
            </button>
            {categories?.map((category) => (
                <button
                    key={category.id}
                    type="button"
                    className="toolbar__chip"
                    aria-pressed={categoryId === category.id}
                    onClick={() => onCategoryChange(categoryId === category.id ? null : category.id)}
                >
                    {category.name}
                    <span className="toolbar__count">{counts.get(category.name) ?? 0}</span>
                </button>
            ))}
        </div>
    </div>
);
