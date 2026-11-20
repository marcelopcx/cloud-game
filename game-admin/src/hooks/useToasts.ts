import { useCallback, useState } from "react";

export interface Toast {
    id: number;
    kind: "success" | "error";
    text: string;
}

let nextId = 1;

/** Avisos (éxito o error) que muestra <Toasts>; se guardan los últimos 4. */
export function useToasts() {
    const [toasts, setToasts] = useState<Toast[]>([]);
    const dismiss = useCallback((id: number) => setToasts((all) => all.filter((t) => t.id !== id)), []);
    const push = useCallback((kind: Toast["kind"], text: string) => {
        setToasts((all) => [...all.slice(-3), { id: nextId++, kind, text }]);
    }, []);
    return { toasts, push, dismiss };
}
