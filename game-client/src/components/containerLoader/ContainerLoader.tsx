import { BackButton } from '../backButton/BackButton.tsx';
import { StationOverlay } from '../stationOverlay/StationOverlay.tsx';
import "../gamePlayer/gamePlayer.css";
import "./containerLoader.css";

/**
 * Pantalla previa al player mientras el Game Dispatcher asigna una estación:
 * en cola o preparándola (lo cubre LoadingAnimation) y el error, con reintentar.
 */
export const ContainerLoader = ({
    title,
    phase,
    error,
    onBack,
    onRetry,
    notice,
}: {
    title: string;
    phase: 'queued' | 'provisioning' | 'error';
    error: string | null;
    onBack: () => void;
    onRetry: () => void;
    /** Aviso bajo la barra mientras se espera la estación (p. ej. jugar sin sesión). */
    notice?: string;
}) => (
    <div className="game-player">
        <header className="game-player__bar">
            <BackButton label="Salir del juego" onClick={onBack} />
            <h1 className="game-player__title">{title}</h1>
        </header>
        {notice ? <p className="game-player__notice" role="note">{notice}</p> : null}

        <section className="game-player__stage game-player__stage--loading station-stage" aria-label="Estación">
            {phase === 'error' ? <StationOverlay error={error ?? ''} onRetry={onRetry} /> : null}
        </section>
    </div>
);
