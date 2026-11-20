import { useEffect, useState, type ReactNode } from 'react';
import { GameView } from '../gameView/GameView.tsx';
import { BackButton } from '../backButton/BackButton.tsx';
import { GameIcon } from '../gameIcon/GameIcon.tsx';
import { DisconnectModal } from '../disconnectModal/DisconnectModal.tsx';
import { StationOverlay } from '../stationOverlay/StationOverlay.tsx';
import type { GameInputs, useStation } from '@hooks';
import "./gamePlayer.css";

type Station = ReturnType<typeof useStation>;

/**
 * Vista de juego a pantalla completa: el stream WebRTC ocupa todo el viewport y
 * la barra (volver + ícono + título) flota encima. El video se monta desde el principio porque
 * {@code launch} necesita los elementos {@code <video>}/{@code <audio>}.
 */
export const GamePlayer = ({
    title,
    icon,
    station,
    controls,
    onBack,
    onRetry,
    onSettled,
    actions,
}: {
    title: string;
    icon?: string;
    station: Station;
    /** Controles del juego para el modal de configuración. */
    controls: GameInputs;
    onBack: () => void;
    onRetry?: () => void;
    /** Avisa si ya hay video (o error) para que la pantalla de carga pueda retirarse. */
    onSettled?: (settled: boolean) => void;
    actions?: ReactNode;
}) => {
    const [pointerActive, setPointerActive] = useState(true);
    const settled = station.hasTrack || Boolean(station.error);
    useEffect(() => onSettled?.(settled), [settled, onSettled]);
    const barVisible = !station.mouseOn || pointerActive;

    return (
        <div className="game-player">
            <header className={`game-player__bar${barVisible ? ' is-visible' : ''}`}>
                <BackButton label="Salir del juego" variant="ghost" onClick={onBack} data-keep-windowed="" />
                <GameIcon src={icon} name={title} aria-hidden="true" />
                <h1 className="game-player__title">{title}</h1>
            </header>

            <section className="game-player__stage station-stage" aria-label="Video del juego">
                <GameView
                    videoRef={station.videoRef}
                    audioRef={station.audioRef}
                    hasTrack={station.hasTrack}
                    needs={station.needs}
                    padIds={station.padIds}
                    controls={controls}
                    pointerLocked={station.pointerLocked}
                    stats={station.rtcStats}
                    mouseOn={station.mouseOn}
                    onMouseToggle={station.setMouseOn}
                    onControlsChange={setPointerActive}
                    actions={actions}
                />
                {station.error && <StationOverlay error={station.error} onRetry={onRetry} />}
            </section>

            {/* La sesión terminó: el único camino de salida es Aceptar, que es el mismo `onBack` (stop + home). */}
            <DisconnectModal open={station.disconnected} onAccept={onBack} />
        </div>
    );
};
