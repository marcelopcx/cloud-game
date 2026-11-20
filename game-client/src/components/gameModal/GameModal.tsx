import type { CSSProperties } from "react";
import { Button } from "../button/Button.tsx";
import { PLATFORM_ICONS, type GamePlatform } from "../cardGame/platformIcons.ts";
import { CategoryFilter } from "../categoryFilter/CategoryFilter.tsx";
import { Text } from "../text/Text.tsx";
import { Modal } from "../modal/Modal.tsx";
import playIcon from "../../../assets/icons/play.svg";
import "./gameModal.css";

// Forma esperada de la respuesta del endpoint de detalle de juego.
export interface GameDetails {
    gameId: string;
    title: string;
    backgroundImage: string;
    icon?: string;
    genres: string[];
    platforms: GamePlatform[];
    description?: string;
    /** Se muestra pero todavía no se puede jugar: el botón queda deshabilitado. */
    comingSoon?: boolean;
}

export interface GameModalProps {
    open: boolean;
    game: GameDetails | null;
    onClose: () => void;
    onPlay: (game: GameDetails) => void;
}

// A partir de este largo el título baja de tamaño para no ocupar tres líneas.
const LONG_TITLE_LENGTH = 16;

// Orden de entrada escalonada de cada bloque del contenido.
const reveal = (index: number) => ({ "--reveal-index": index }) as CSSProperties;

export const GameModal = ({ open, game, onClose, onPlay }: GameModalProps) => {
    if (!game) return null;

    return (
        <Modal open={open} onClose={onClose} ariaLabel={game.title} className="game-modal">
            <div className="game-modal-stage">
                <div className="game-modal-art" aria-hidden="true">
                    <div
                        className="game-modal-art-image"
                        style={{ backgroundImage: `url(${game.backgroundImage})` }}
                    />
                </div>

                <div className="game-modal-content">
                    <Text
                        as="h2"
                        className={["game-modal-title", game.title.length > LONG_TITLE_LENGTH && "game-modal-title--long"]
                            .filter(Boolean)
                            .join(" ")}
                    >
                        <span className="game-modal-title-inner">{game.title}</span>
                    </Text>

                    <div className="game-modal-footer">
                        <div className="game-modal-main">
                            {game.description && (
                                <Text as="p" className="game-modal-description game-modal-reveal" style={reveal(1)}>
                                    {game.description}
                                </Text>
                            )}

                            <div className="game-modal-actions game-modal-reveal" style={reveal(2)}>
                                {game.comingSoon ? (
                                    <Button
                                        label="Próximamente"
                                        variant="transparent"
                                        className="game-modal-play button--coming-soon"
                                        disabled
                                    />
                                ) : (
                                    <Button
                                        label="Jugar ahora"
                                        variant="primary"
                                        icon={playIcon}
                                        className="game-modal-play"
                                        data-autofocus
                                        onClick={() => onPlay(game)}
                                    />
                                )}
                            </div>
                        </div>

                        <dl className="game-modal-specs game-modal-reveal" style={reveal(3)}>
                            {game.genres.length > 0 && (
                                <div className="game-modal-spec">
                                    <dt>Géneros</dt>
                                    <dd>
                                        <CategoryFilter variant="tag" categories={game.genres} aria-label="Géneros" />
                                    </dd>
                                </div>
                            )}

                            {game.platforms.length > 0 && (
                                <div className="game-modal-spec game-modal-spec--controls">
                                    <dt>Controles</dt>
                                    <dd>
                                        <ul className="game-modal-platforms">
                                            {game.platforms.map((platform) => (
                                                <li key={platform} className="game-modal-platform" title={PLATFORM_ICONS[platform].label}>
                                                    <img src={PLATFORM_ICONS[platform].icon} alt="" aria-hidden="true" />
                                                    {PLATFORM_ICONS[platform].shortLabel}
                                                </li>
                                            ))}
                                        </ul>
                                    </dd>
                                </div>
                            )}
                        </dl>
                    </div>
                </div>
            </div>
        </Modal>
    );
};
