import { Modal } from "../modal/Modal.tsx";
import disconnectImage from "../../../assets/images/disconnect-display.jpeg";
import "./disconnectModal.css";

export interface DisconnectModalProps {
    open: boolean;
    /** Aceptar, Escape o clic fuera: vuelve al menú principal. */
    onAccept: () => void;
}

/**
 * Aviso de sesión terminada (la estación la cortó por inactividad o se perdió el stream).
 * Muestra la imagen de "technical difficulties" y deja un único camino de salida: Aceptar
 * vuelve al menú principal.
 */
export const DisconnectModal = ({ open, onAccept }: DisconnectModalProps) => (
    <Modal
        open={open}
        onClose={onAccept}
        ariaLabel="La sesión de juego se cerró"
        closeLabel="Volver al menú"
        className="disconnect-modal"
    >
        <div className="disconnect-modal-body">
            <img
                className="disconnect-modal-image"
                src={disconnectImage}
                alt="Pantalla de dificultades técnicas con un perro desenchufado"
            />

            <div className="disconnect-modal-copy">
                <h2 className="disconnect-modal-title">Se perdió la conexión</h2>
                <p className="disconnect-modal-text">
                    Tu partida se cerró por inactividad. Volvé al menú principal para elegir otro juego.
                </p>
                <button type="button" className="disconnect-modal-accept" onClick={onAccept} data-autofocus>
                    Aceptar
                </button>
            </div>
        </div>
    </Modal>
);
