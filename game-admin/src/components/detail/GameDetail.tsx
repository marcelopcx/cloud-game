import { useEffect, useId, useState, type MouseEvent } from "react";
import { useModalDialog } from "@hooks";
import { describeError, getGame, getGameInputs } from "@services";
import type { Game, GameInput } from "@/types";
import { Button } from "../button/Button.tsx";
import { CloseIcon, EditIcon, ExternalIcon, GamepadIcon, ImageIcon, StarIcon, TrashIcon } from "../icons/Icons.tsx";
import { GameStatusBadge } from "../status/GameStatusBadge.tsx";
import "./gameDetail.css";

export interface GameDetailProps {
    /** El juego del listado: se muestra enseguida y se refresca con GET /games/{id}. */
    game: Game | null;
    onClose: () => void;
    onEdit: (game: Game) => void;
    onDelete: (game: Game) => void;
    onEditControls: (game: Game) => void;
}

/** Las seis URLs de IMAGE, con el uso que les da game-client (docs/game-crud/README.md). */
const IMAGE_SLOTS = [
    { key: "banner1", label: "Banner 1", usage: "Banner principal · 1920×620", ratio: "1920 / 620" },
    { key: "banner2", label: "Banner 2", usage: "Sub banner · 1920×620", ratio: "1920 / 620" },
    { key: "poster", label: "Póster", usage: "Carrusel · 600×900", ratio: "2 / 3" },
    { key: "cover", label: "Cover", usage: "Tarjeta del grid · 920×430", ratio: "460 / 215" },
    { key: "background", label: "Portada", usage: "Fondo de la ficha · 1920×620", ratio: "1920 / 620" },
    { key: "icon", label: "Ícono", usage: "Ficha y reproductor · cuadrado", ratio: "1 / 1" },
] as const;

const dateFormat = new Intl.DateTimeFormat("es", { dateStyle: "long", timeStyle: "short" });

export const GameDetail = ({ game, onClose, onEdit, onDelete, onEditControls }: GameDetailProps) => {
    const ref = useModalDialog(game !== null);
    const titleId = useId();
    const [fresh, setFresh] = useState<{ game: Game | null; error: string | null }>({ game: null, error: null });

    useEffect(() => {
        if (!game) return;
        const controller = new AbortController();
        getGame(game.id, controller.signal)
            .then((g) => setFresh({ game: g, error: null }))
            .catch((e: Error) => {
                if (e.name !== "AbortError") setFresh({ game: null, error: describeError(e, "actualizar la ficha") });
            });
        return () => controller.abort();
    }, [game]);

    // Controles (GET /games/{id}/inputs): se piden cada vez que se abre la ficha y se olvidan al cerrarla.
    const [inputs, setInputs] = useState<{ id: number; rows: GameInput[] | null; error: string | null } | null>(null);
    useEffect(() => {
        if (!game) return;
        const controller = new AbortController();
        getGameInputs(game.id, controller.signal)
            .then((rows) => setInputs({ id: game.id, rows, error: null }))
            .catch((e: Error) => {
                if (e.name !== "AbortError") setInputs({ id: game.id, rows: null, error: describeError(e, "cargar los controles") });
            });
        return () => controller.abort();
    }, [game]);

    // Lo recién pedido, si corresponde a este juego; si no, lo del listado.
    const current = fresh.game && fresh.game.id === game?.id ? fresh.game : game;

    const handleClosed = () => {
        setInputs(null);
        onClose();
    };

    const handleBackdropClick = (event: MouseEvent<HTMLDialogElement>) => {
        if (event.target === event.currentTarget) onClose();
    };

    return (
        <dialog ref={ref} className="modal game-detail" aria-labelledby={titleId} onClose={handleClosed} onClick={handleBackdropClick}>
            {current && (
                <article>
                    <header className="game-detail__hero">
                        {(current.images?.background || current.images?.banner1 || current.images?.cover || current.images?.poster) && (
                            <img className="game-detail__cover" src={current.images.background || current.images.banner1 || current.images.cover || current.images.poster || undefined} alt="" />
                        )}
                        <div className="game-detail__hero-shade" />
                        <Button variant="ghost" iconOnly icon={<CloseIcon />} className="game-detail__close" onClick={onClose}>
                            Cerrar
                        </Button>
                        <div className="game-detail__heading">
                            {current.images?.icon ? (
                                <img className="game-detail__icon" src={current.images.icon} alt="" />
                            ) : (
                                <span className="game-detail__icon game-detail__icon--empty"><ImageIcon /></span>
                            )}
                            <div>
                                <p className="game-detail__id">ID {current.id} <GameStatusBadge status={current.status} /></p>
                                <h2 id={titleId}>{current.name}</h2>
                                {current.rating != null && (
                                    <p className="game-detail__rating">
                                        <StarIcon size={14} /> {current.rating.toFixed(1)} <span>/ 5</span>
                                    </p>
                                )}
                            </div>
                        </div>
                    </header>

                    <div className="game-detail__body">
                        {fresh.error && <p className="game-detail__error" role="alert">{fresh.error}</p>}

                        <p className={current.description ? "game-detail__description" : "game-detail__description game-detail__muted"}>
                            {current.description || "Sin descripción."}
                        </p>

                        <dl className="game-detail__facts">
                            <div>
                                <dt>Ruta</dt>
                                <dd><code>{current.path}</code></dd>
                            </div>
                            <div>
                                <dt>Contenedores</dt>
                                <dd>
                                    {current.minContainers} – {current.maxContainers ?? "sin límite"}
                                </dd>
                            </div>
                            <div>
                                <dt>Creado</dt>
                                <dd>{dateFormat.format(new Date(current.createdAt))}</dd>
                            </div>
                        </dl>

                        <section>
                            <h3>Categorías</h3>
                            <TagList items={current.categories} empty="Sin categorías." />
                        </section>
                        <section>
                            <h3>Gamepads</h3>
                            <TagList items={current.gamepads} empty="Sin gamepads." />
                        </section>

                        <section>
                            <h3>Controles</h3>
                            {inputs?.id !== current.id ? (
                                <p className="game-detail__muted">Cargando…</p>
                            ) : inputs.error ? (
                                <p className="game-detail__muted">{inputs.error}</p>
                            ) : inputs.rows!.length === 0 ? (
                                <p className="game-detail__muted">Sin controles cargados.</p>
                            ) : (
                                <div className="game-detail__controls">
                                    <table>
                                        <thead>
                                            <tr>
                                                <th scope="col">Acción</th>
                                                <th scope="col">Teclado y ratón</th>
                                                <th scope="col">Mando</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {inputs.rows!.map((row) => (
                                                <tr key={row.action}>
                                                    <td>{row.action}</td>
                                                    <td>{row.keyboard ? <kbd>{row.keyboard}</kbd> : "—"}</td>
                                                    <td>{row.controller ? <kbd>{row.controller}</kbd> : "—"}</td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </section>

                        <section>
                            <h3>Imágenes</h3>
                            <ul className="game-detail__images">
                                {IMAGE_SLOTS.map((slot) => {
                                    const url = current.images?.[slot.key];
                                    return (
                                        <li key={slot.key}>
                                            <div className="game-detail__thumb" style={{ aspectRatio: slot.ratio }}>
                                                {url ? <img src={url} alt={`${slot.label} de ${current.name}`} loading="lazy" /> : <ImageIcon />}
                                            </div>
                                            <div className="game-detail__image-meta">
                                                <strong>{slot.label}</strong>
                                                <span>{slot.usage}</span>
                                                {url ? (
                                                    <a href={url} target="_blank" rel="noreferrer">
                                                        Abrir <ExternalIcon size={12} />
                                                    </a>
                                                ) : (
                                                    <span className="game-detail__muted">Sin URL</span>
                                                )}
                                            </div>
                                        </li>
                                    );
                                })}
                            </ul>
                        </section>
                    </div>

                    <footer className="game-detail__footer">
                        <Button variant="ghost" icon={<TrashIcon size={16} />} className="game-detail__delete" onClick={() => onDelete(current)}>
                            Eliminar
                        </Button>
                        <div className="game-detail__footer-actions">
                            <Button variant="secondary" icon={<GamepadIcon size={16} />} onClick={() => onEditControls(current)}>
                                Controles
                            </Button>
                            <Button variant="primary" icon={<EditIcon size={16} />} onClick={() => onEdit(current)}>
                                Editar
                            </Button>
                        </div>
                    </footer>
                </article>
            )}
        </dialog>
    );
};

const TagList = ({ items, empty }: { items: string[]; empty: string }) =>
    items.length === 0 ? (
        <p className="game-detail__muted">{empty}</p>
    ) : (
        <ul className="game-detail__tags">
            {items.map((item) => <li key={item}>{item}</li>)}
        </ul>
    );
