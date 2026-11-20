import { useEffect, useRef } from "react";

/**
 * Abre y cierra un <dialog> nativo con showModal() según `open`: atrapa el foco,
 * cierra con Escape (evento close) y oscurece el fondo.
 */
export function useModalDialog(open: boolean) {
    const ref = useRef<HTMLDialogElement>(null);

    useEffect(() => {
        const dialog = ref.current;
        if (!dialog) return;
        if (open && !dialog.open) dialog.showModal();
        if (!open && dialog.open) dialog.close();
    }, [open]);

    return ref;
}
