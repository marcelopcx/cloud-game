import { useId, type ReactNode } from "react";
import { useModalDialog } from "@hooks";
import { Button } from "../button/Button.tsx";
import { AlertIcon } from "../icons/Icons.tsx";
import "./confirmDialog.css";

export interface ConfirmDialogProps {
    open: boolean;
    title: string;
    children: ReactNode;
    confirmLabel: string;
    busy?: boolean;
    error?: string | null;
    onConfirm: () => void;
    onCancel: () => void;
}

/** Confirmación de una acción destructiva (reemplaza a window.confirm). */
export const ConfirmDialog = ({ open, title, children, confirmLabel, busy, error, onConfirm, onCancel }: ConfirmDialogProps) => {
    const ref = useModalDialog(open);
    const titleId = useId();

    return (
        <dialog
            ref={ref}
            className="modal confirm-dialog"
            aria-labelledby={titleId}
            onClose={onCancel}
            onCancel={(e) => busy && e.preventDefault()}
        >
            <div className="confirm-dialog__icon"><AlertIcon size={22} /></div>
            <h2 id={titleId}>{title}</h2>
            <div className="confirm-dialog__body">{children}</div>
            {error && <p className="confirm-dialog__error" role="alert">{error}</p>}
            <footer>
                <Button variant="ghost" onClick={onCancel} disabled={busy}>Cancelar</Button>
                <Button variant="danger" onClick={onConfirm} disabled={busy} autoFocus>
                    {busy ? "Eliminando…" : confirmLabel}
                </Button>
            </footer>
        </dialog>
    );
};
