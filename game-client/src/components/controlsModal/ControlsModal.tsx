import type { CSSProperties } from "react";
import type { GameInputs } from "@hooks";
import type { GameInputResponse } from "@/types";
import { Modal } from "../modal/Modal.tsx";
import { PagerArrows } from "../controlsModal/PagerArrows.tsx";
import controllerImg from "../../../assets/icons/controller.svg";
import "./controlsModal.css";

export interface ControlsModalProps {
    open: boolean;
    /** Controles ya pedidos por PlayerScreen (useGameInputs) mientras arrancaba el contenedor. */
    controls: GameInputs;
    onClose: () => void;
    /** Juegos con mando y teclado: alterna a la otra guía (flechas a los costados). */
    onSwitch?: () => void;
}

/**
 * Punta de cada línea de controller.svg (coordenadas del viewBox 811×741) y los botones de
 * game-crud (INPUT_CONTROLLER) que señala. `side` = hacia dónde sale el texto desde el punto.
 */
const SLOTS: { buttons: string[]; x: number; y: number; side: "left" | "right" | "bottom" }[] = [
    { buttons: ["LB"], x: 31, y: 107, side: "left" },
    { buttons: ["LT"], x: 56, y: 283, side: "left" },
    { buttons: ["Stick izquierdo", "L3"], x: 5, y: 644, side: "left" },
    { buttons: ["RB"], x: 805, y: 107, side: "right" },
    { buttons: ["RT"], x: 784, y: 283, side: "right" },
    { buttons: ["Y"], x: 794, y: 381, side: "right" },
    { buttons: ["B"], x: 792, y: 426, side: "right" },
    { buttons: ["A"], x: 794, y: 591, side: "right" },
    { buttons: ["X"], x: 792, y: 623, side: "right" },
    { buttons: ["Stick derecho", "R3"], x: 794, y: 735, side: "right" },
    { buttons: ["Start", "Select"], x: 425, y: 659, side: "bottom" },
];
const PLACED = new Set(SLOTS.flatMap((slot) => slot.buttons));

/**
 * Guía de controles en partida: el mando con la acción del juego en la punta de cada línea.
 * Lo que no tiene línea en el dibujo (D-Pad) se lista debajo.
 */
export const ControlsModal = ({ open, controls, onClose, onSwitch }: ControlsModalProps) => {
    const { inputs, status, retry } = controls;
    const withButton = inputs.filter((input): input is GameInputResponse & { controller: string } => !!input.controller);
    const unplaced = withButton.filter((input) => !PLACED.has(input.controller));

    return (
        <Modal open={open} onClose={onClose} ariaLabel="Guía de controles" className="controls-modal" aside={onSwitch && <PagerArrows page={0} width={1100} onSwitch={onSwitch} />}>
            <header className="controls-modal-header">
                <h2 className="controls-modal-title">Guía de Controles</h2>
                <p className="controls-modal-subtitle">Acciones del juego</p>
            </header>

            <div className="controls-modal-pad">
                <img src={controllerImg} alt="" draggable={false} />
                {SLOTS.map((slot) => {
                    const actions = withButton.filter((input) => slot.buttons.includes(input.controller));
                    if (actions.length === 0) return null;
                    const style = { "--x": `${(slot.x / 811) * 100}%`, "--y": `${(slot.y / 741) * 100}%` } as CSSProperties;
                    return (
                        <ul key={slot.buttons[0]} className={`controls-modal-label is-${slot.side}`} style={style}>
                            {actions.map((input) => (
                                <li key={input.action}>
                                    {/* En los puntos que agrupan varios botones se aclara cuál. */}
                                    {slot.buttons.length > 1 && <span className="controls-modal-button">{input.controller}</span>}
                                    {input.action}
                                </li>
                            ))}
                        </ul>
                    );
                })}
            </div>

            {status === "loading" && <p className="controls-modal-status">Cargando controles…</p>}
            {status === "error" && (
                <p className="controls-modal-status">
                    No se pudieron cargar los controles.{" "}
                    <button type="button" className="controls-modal-retry" onClick={retry}>Reintentar</button>
                </p>
            )}
            {status === "ready" && withButton.length === 0 && (
                <p className="controls-modal-status">Este juego todavía no tiene controles de mando cargados.</p>
            )}
            {unplaced.length > 0 && (
                <ul className="controls-modal-extra">
                    {unplaced.map((input) => (
                        <li key={input.action}>
                            <span className="controls-modal-button">{input.controller}</span>
                            {input.action}
                        </li>
                    ))}
                </ul>
            )}
        </Modal>
    );
};
