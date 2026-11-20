import { useRef, useState, type DragEvent } from "react";

/**
 * Reordenar una lista arrastrando (HTML5 drag and drop). Devuelve las props de cada
 * ítem; al soltar llama a onMove(desde, hasta). Cada lista usa su propio hook, así que
 * una lista dentro de otra no se mezclan: el dragstart no sube al ítem padre.
 */
export function useDragSort(onMove: (from: number, to: number) => void) {
    const dragging = useRef<number | null>(null);
    const [over, setOver] = useState<number | null>(null);

    const reset = () => {
        dragging.current = null;
        setOver(null);
    };

    return (index: number) => ({
        onDragStart: (e: DragEvent) => {
            e.stopPropagation();
            dragging.current = index;
            e.dataTransfer.effectAllowed = "move";
            e.dataTransfer.setData("text/plain", String(index));
        },
        onDragOver: (e: DragEvent) => {
            if (dragging.current === null) return;
            e.preventDefault();
            e.stopPropagation();
            if (over !== index) setOver(index);
        },
        onDrop: (e: DragEvent) => {
            if (dragging.current === null) return;
            e.preventDefault();
            e.stopPropagation();
            if (dragging.current !== index) onMove(dragging.current, index);
            reset();
        },
        onDragEnd: reset,
        "data-drag-over": over === index && dragging.current !== index ? true : undefined,
    });
}

/** Copia de la lista con el ítem `from` movido a `to`. */
export function move<T>(list: T[], from: number, to: number): T[] {
    const next = [...list];
    const [item] = next.splice(from, 1);
    next.splice(to, 0, item);
    return next;
}
