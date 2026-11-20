import { useEffect } from "react";
import type { Toast } from "@hooks";
import { AlertIcon, CheckIcon, CloseIcon } from "../icons/Icons.tsx";
import "./toasts.css";

/** Los de éxito se van solos; los de error se quedan más tiempo. */
const DURATION_MS = { success: 4000, error: 9000 };

const ToastItem = ({ toast, onDismiss }: { toast: Toast; onDismiss: (id: number) => void }) => {
    useEffect(() => {
        const id = setTimeout(() => onDismiss(toast.id), DURATION_MS[toast.kind]);
        return () => clearTimeout(id);
    }, [toast, onDismiss]);

    return (
        <li className={`toast toast--${toast.kind}`} role={toast.kind === "error" ? "alert" : "status"}>
            <span className="toast__icon">{toast.kind === "error" ? <AlertIcon /> : <CheckIcon />}</span>
            <p>{toast.text}</p>
            <button type="button" aria-label="Cerrar aviso" onClick={() => onDismiss(toast.id)}>
                <CloseIcon size={14} />
            </button>
        </li>
    );
};

export const Toasts = ({ toasts, onDismiss }: { toasts: Toast[]; onDismiss: (id: number) => void }) => (
    <ol className="toasts" aria-live="polite">
        {toasts.map((toast) => (
            <ToastItem key={toast.id} toast={toast} onDismiss={onDismiss} />
        ))}
    </ol>
);
