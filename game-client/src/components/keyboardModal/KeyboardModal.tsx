import type { GameInputs } from "@hooks";
import { Modal } from "../modal/Modal.tsx";
import { PagerArrows } from "../controlsModal/PagerArrows.tsx";
import keyboardImg from "../../../assets/icons/mouse-keyboard.svg";
import { KEYBOARD_IMAGE, KEY_BOXES } from "./keyboardLayout.ts";
import "../controlsModal/controlsModal.css";
import "./keyboardModal.css";

export interface KeyboardModalProps {
    open: boolean;
    /** Controles ya pedidos por PlayerScreen (useGameInputs) mientras arrancaba el contenedor. */
    controls: GameInputs;
    onClose: () => void;
    /** Juegos con mando y teclado: alterna a la otra guía (flechas a los costados). */
    onSwitch?: () => void;
}

// El teclado ocupa 1041 de los 1252 px de ancho del SVG (el resto es el ratón) y todo su alto.
const KEYBOARD_WIDTH_SHARE = 1041 / 1252;
// El relleno cubre también el contorno del dibujo, no solo el interior de la tecla.
const PAD = 8;

const boxStyle = ([x, y, w, h]: readonly [number, number, number, number]) => ({
    left: `${((x - PAD) / KEYBOARD_IMAGE.width) * KEYBOARD_WIDTH_SHARE * 100}%`,
    width: `${((w + 2 * PAD) / KEYBOARD_IMAGE.width) * KEYBOARD_WIDTH_SHARE * 100}%`,
    top: `${((y - PAD) / KEYBOARD_IMAGE.height) * 100}%`,
    height: `${((h + 2 * PAD) / KEYBOARD_IMAGE.height) * 100}%`,
});

/** Guía de teclado y ratón en partida: el dibujo y debajo la lista tecla → acción del juego. */
export const KeyboardModal = ({ open, controls, onClose, onSwitch }: KeyboardModalProps) => {
    const { inputs, status, retry } = controls;
    const withKey = inputs.filter((input): input is typeof input & { keyboard: string } => !!input.keyboard);
    // Teclas que usa el juego, sin repetir; las que el dibujo no tiene (ratón) quedan fuera.
    const usedKeys = [...new Set(withKey.map((input) => input.keyboard))].filter((name) => name in KEY_BOXES);

    return (
        <Modal open={open} onClose={onClose} ariaLabel="Guía de teclado" className="controls-modal keyboard-modal" aside={onSwitch && <PagerArrows page={1} width={1252} onSwitch={onSwitch} />}>
            <header className="controls-modal-header">
                <h2 className="controls-modal-title">Guía de Teclado</h2>
                <p className="controls-modal-subtitle">Acciones del juego</p>
            </header>

            <div className="keyboard-modal-board">
                <img className="keyboard-modal-img" src={keyboardImg} alt="" draggable={false} />
                {usedKeys.map((name) => (
                    <span key={name} aria-hidden="true" className="keyboard-modal-key" style={boxStyle(KEY_BOXES[name])} />
                ))}
            </div>

            {status === "loading" && <p className="controls-modal-status">Cargando controles…</p>}
            {status === "error" && (
                <p className="controls-modal-status">
                    No se pudieron cargar los controles.{" "}
                    <button type="button" className="controls-modal-retry" onClick={retry}>Reintentar</button>
                </p>
            )}
            {status === "ready" && withKey.length === 0 && (
                <p className="controls-modal-status">Este juego todavía no tiene controles de teclado cargados.</p>
            )}
            {withKey.length > 0 && (
                <ul className="keyboard-modal-binds">
                    {withKey.map((input) => (
                        <li key={input.action}>
                            <kbd>{input.keyboard}</kbd>
                            {input.action}
                        </li>
                    ))}
                </ul>
            )}
        </Modal>
    );
};
