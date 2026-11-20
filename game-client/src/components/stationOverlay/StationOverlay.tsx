import "./stationOverlay.css";

/** Error de la partida con opción de reintentar (la carga la cubre LoadingAnimation). */
export const StationOverlay = ({ error, onRetry }: { error: string; onRetry?: () => void }) => (
    <div className="station-overlay is-error" role="alert">
        <p className="station-overlay__title">No se pudo iniciar la partida</p>
        <p className="station-overlay__text">{error}</p>
        {onRetry && (
            <button type="button" className="station-overlay__retry" onClick={onRetry}>
                Reintentar
            </button>
        )}
    </div>
);
