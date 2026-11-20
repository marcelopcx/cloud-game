import type { CSSProperties } from "react";
import "./controlsModal.css";

const Chevron = ({ flip }: { flip?: boolean }) => (
    <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true" style={flip ? { transform: "scaleX(-1)" } : undefined}>
        <path d="M9 5l7 7-7 7" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
);

/** Flecha hacia la otra guía (solo hay dos páginas) y puntos con la actual marcada; fuera de la tarjeta. */
export const PagerArrows = ({ page, width, onSwitch }: { page: 0 | 1; width: number; onSwitch: () => void }) => (
    <div style={{ "--card-w": `${width}px` } as CSSProperties}>
        {page === 0 ? (
            <button type="button" className="controls-modal-arrow is-next" aria-label="Guía siguiente" onClick={onSwitch}>
                <Chevron />
            </button>
        ) : (
            <button type="button" className="controls-modal-arrow is-prev" aria-label="Guía anterior" onClick={onSwitch}>
                <Chevron flip />
            </button>
        )}
        <div className="controls-modal-dots" role="img" aria-label={`Página ${page + 1} de 2`}>
            <span className={page === 0 ? "is-current" : ""} />
            <span className={page === 1 ? "is-current" : ""} />
        </div>
    </div>
);
