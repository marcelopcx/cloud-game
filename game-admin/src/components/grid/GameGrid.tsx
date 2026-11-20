import type { Game } from "@/types";
import { Button } from "../button/Button.tsx";
import { GameStatusBadge } from "../status/GameStatusBadge.tsx";
import { EditIcon, GamepadIcon, ImageIcon, StarIcon, TrashIcon } from "../icons/Icons.tsx";
import "./gameGrid.css";

export interface GameGridProps {
    /** null = cargando (muestra esqueletos). */
    games: Game[] | null;
    onOpen: (game: Game) => void;
    onEdit: (game: Game) => void;
    onDelete: (game: Game) => void;
    onEditControls: (game: Game) => void;
}

const SKELETONS = 8;

/** Tarjetas del catálogo. El póster es el grid horizontal de SteamGridDB (920×430). */
export const GameGrid = ({ games, onOpen, onEdit, onDelete, onEditControls }: GameGridProps) => {
    if (!games) {
        return (
            <ul className="game-grid" aria-busy="true" aria-label="Cargando juegos">
                {Array.from({ length: SKELETONS }, (_, i) => (
                    <li key={i} className="game-card game-card--skeleton">
                        <div className="game-card__poster" />
                        <div className="game-card__body">
                            <span className="skeleton-line" />
                            <span className="skeleton-line skeleton-line--short" />
                        </div>
                    </li>
                ))}
            </ul>
        );
    }

    return (
        <ul className="game-grid">
            {games.map((game) => (
                <li key={game.id} className={game.status === "DISABLED" ? "game-card game-card--disabled" : "game-card"}>
                    <button type="button" className="game-card__open" onClick={() => onOpen(game)}>
                        <div className="game-card__poster">
                            {game.images?.poster ? (
                                <img src={game.images.cover || game.images.poster} alt="" loading="lazy" />
                            ) : (
                                <span className="game-card__no-image"><ImageIcon size={28} /></span>
                            )}
                            <GameStatusBadge status={game.status} className="game-card__status" />
                            {game.rating != null && (
                                <span className="game-card__rating">
                                    <StarIcon size={12} /> {game.rating.toFixed(1)}
                                </span>
                            )}
                        </div>
                        <div className="game-card__body">
                            <div className="game-card__title">
                                {game.images?.icon && <img className="game-card__icon" src={game.images.icon} alt="" loading="lazy" />}
                                <h3>{game.name}</h3>
                            </div>
                            <code className="game-card__path">{game.path}</code>
                            {game.categories.length > 0 && (
                                <ul className="game-card__tags" aria-label="Categorías">
                                    {game.categories.map((c) => <li key={c}>{c}</li>)}
                                </ul>
                            )}
                            {game.gamepads.length > 0 && (
                                <p className="game-card__gamepads">
                                    <GamepadIcon size={14} /> {game.gamepads.join(" · ")}
                                </p>
                            )}
                        </div>
                    </button>
                    <div className="game-card__actions">
                        <Button size="sm" variant="secondary" iconOnly icon={<EditIcon size={15} />} onClick={() => onEdit(game)}>
                            {`Editar ${game.name}`}
                        </Button>
                        <Button size="sm" variant="secondary" iconOnly icon={<GamepadIcon size={15} />} onClick={() => onEditControls(game)}>
                            {`Controles de ${game.name}`}
                        </Button>
                        <Button size="sm" variant="secondary" iconOnly icon={<TrashIcon size={15} />} className="game-card__delete" onClick={() => onDelete(game)}>
                            {`Eliminar ${game.name}`}
                        </Button>
                    </div>
                </li>
            ))}
        </ul>
    );
};
