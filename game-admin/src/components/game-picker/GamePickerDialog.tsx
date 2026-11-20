import { useId, useState, type MouseEvent } from "react";
import { useModalDialog } from "@hooks";
import type { Game } from "@/types";
import { Button } from "../button/Button.tsx";
import { CheckIcon, CloseIcon, ImageIcon, SearchIcon } from "../icons/Icons.tsx";
import "./gamePickerDialog.css";

export interface GamePickerDialogProps {
    open: boolean;
    title: string;
    games: Game[] | null;
    /** Ids ya elegidos (se marcan). */
    selected: number[];
    /** single: elegir uno y cerrar (banners). multiple: agregar/quitar sin cerrar (carrusel, grid). */
    mode: "single" | "multiple";
    onToggle: (game: Game) => void;
    onClose: () => void;
}

const normalize = (text: string) => text.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase().trim();

export const GamePickerDialog = ({ open, title, games, selected, mode, onToggle, onClose }: GamePickerDialogProps) => {
    const ref = useModalDialog(open);
    const titleId = useId();
    const [query, setQuery] = useState("");
    const q = normalize(query);
    const visible = (games ?? []).filter((g) => normalize(g.name).includes(q) || g.categories.some((c) => normalize(c).includes(q)));

    const close = () => {
        setQuery("");
        onClose();
    };

    const handleBackdropClick = (event: MouseEvent<HTMLDialogElement>) => {
        if (event.target === event.currentTarget) close();
    };

    return (
        <dialog ref={ref} className="modal game-picker" aria-labelledby={titleId} onClose={close} onClick={handleBackdropClick}>
            <header className="game-picker__header">
                <div>
                    <h2 id={titleId}>{title}</h2>
                    <p>{mode === "single" ? "Elegí un juego." : `${selected.length} elegidos. Clic para agregar o quitar; el orden se ajusta en la sección.`}</p>
                </div>
                <Button variant="ghost" iconOnly icon={<CloseIcon />} onClick={close}>Cerrar</Button>
            </header>
            <div className="game-picker__search">
                <SearchIcon />
                <input
                    type="search"
                    placeholder="Buscar por nombre o categoría…"
                    aria-label="Buscar juego"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    autoFocus
                />
            </div>
            <ul className="game-picker__list">
                {games === null && <li className="game-picker__message">Cargando juegos…</li>}
                {games !== null && visible.length === 0 && <li className="game-picker__message">Ningún juego coincide.</li>}
                {visible.map((game) => {
                    const isSelected = selected.includes(game.id);
                    return (
                        <li key={game.id}>
                            <button
                                type="button"
                                className="game-picker__item"
                                aria-pressed={isSelected}
                                onClick={() => {
                                    onToggle(game);
                                    if (mode === "single") close();
                                }}
                            >
                                <span className="game-picker__thumb">
                                    {game.images?.poster ? <img src={game.images.cover || game.images.poster} alt="" loading="lazy" /> : <ImageIcon />}
                                    {isSelected && <span className="game-picker__check"><CheckIcon size={14} /></span>}
                                </span>
                                <span className="game-picker__name">{game.name}</span>
                                <span className="game-picker__meta">{game.categories.join(" · ") || "Sin categorías"}</span>
                            </button>
                        </li>
                    );
                })}
            </ul>
            {mode === "multiple" && (
                <footer className="game-picker__footer">
                    <Button variant="primary" onClick={close}>Listo</Button>
                </footer>
            )}
        </dialog>
    );
};
