import { useState, type HTMLAttributes } from "react";
import { CardGame, type CardGameProps } from "../cardGame/CardGame.tsx";
import { CategoryFilter } from "../categoryFilter/CategoryFilter.tsx";
import "./gamesGridLayout.css";

export type CompactGameItem = Omit<CardGameProps, "onClick" | "variant">;

export interface GamesGridLayoutProps extends HTMLAttributes<HTMLElement> {
    title?: string;
    games: CompactGameItem[];
    genres?: string[];
    /** false: respeta el orden de `games` (el elegido en game-admin) en vez de ordenar por título. */
    sortByTitle?: boolean;
    onGameClick?: (id: number) => void;
    onGenreChange?: (genre: string | null) => void;
}

export const GamesGridLayout = ({
    title = "Descubre todos los juegos",
    games = [],
    genres = [],
    sortByTitle = true,
    onGameClick,
    onGenreChange,
    className = "",
    ...rest
}: GamesGridLayoutProps) => {
    const [selectedGenre, setSelectedGenre] = useState<string | null>(null);

    const gridClassName = ["games-grid", className].filter(Boolean).join(" ");

    const sortedGenres = [...genres].sort((a, b) => a.localeCompare(b, "es"));

    const sortedGames = sortByTitle ? [...games].sort((a, b) => a.title.localeCompare(b.title, "es")) : games;

    const visibleGames = selectedGenre
        ? sortedGames.filter((game) => game.gender?.includes(selectedGenre))
        : sortedGames;

    const handleGenreChange = (genre: string | null) => {
        setSelectedGenre(genre);
        onGenreChange?.(genre);
    };

    return (
        <section className={gridClassName} {...rest}>
            {title && <h2 className="games-grid-title">{title}</h2>}

            <CategoryFilter
                categories={sortedGenres}
                allLabel="Todos"
                selected={selectedGenre}
                onChange={handleGenreChange}
            />

            {visibleGames.length > 0 ? (
                <ul className="games-grid-list">
                    {visibleGames.map((game) => (
                        <li key={game.id} className="games-grid-item">
                            <CardGame {...game} variant="compact" onClick={onGameClick} />
                        </li>
                    ))}
                </ul>
            ) : (
                <p className="games-grid-empty">No hay juegos disponibles en este momento.</p>
            )}
        </section>
    );
};
