import { useEffect, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import "./modal.css";

const FOCUSABLE_SELECTOR = [
    "a[href]",
    "button:not([disabled])",
    "input:not([disabled])",
    "select:not([disabled])",
    "textarea:not([disabled])",
    "[tabindex]:not([tabindex='-1'])",
].join(",");

const getFocusableElements = (container: HTMLElement) =>
    Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)).filter(
        (element) => element.getClientRects().length > 0,
    );

export interface ModalProps {
    open: boolean;
    onClose: () => void;
    ariaLabel?: string;
    closeLabel?: string;
    className?: string;
    /** Contenido fuera de la tarjeta (p. ej. flechas laterales); los clics no cierran el modal. */
    aside?: ReactNode;
    children: ReactNode;
}

export const Modal = ({
    open,
    onClose,
    ariaLabel,
    closeLabel = "Cerrar",
    className = "",
    aside,
    children,
}: ModalProps) => {
    // Se mantiene montado mientras corre la animación de salida.
    const [isMounted, setIsMounted] = useState(open);
    if (open && !isMounted) setIsMounted(true);
    const isClosing = !open && isMounted;

    const overlayRef = useRef<HTMLDivElement>(null);
    const dialogRef = useRef<HTMLDivElement>(null);
    const closeButtonRef = useRef<HTMLButtonElement>(null);
    const onCloseRef = useRef(onClose);
    useEffect(() => {
        onCloseRef.current = onClose;
    }, [onClose]);

    // Bloquea el scroll y la interacción del fondo, atrapa el foco, cierra con Escape y devuelve el foco al salir.
    useEffect(() => {
        if (!isMounted) return;

        const { body } = document;
        const previousOverflow = body.style.overflow;
        const previousPaddingRight = body.style.paddingRight;
        const scrollbarWidth = window.innerWidth - document.documentElement.clientWidth;
        // Con scrollbar-gutter: stable (index.css) el hueco de la barra ya queda reservado al ocultarla;
        // sumarle padding lo compensaría dos veces y el fondo se correría hacia la izquierda.
        const gutterIsStable = getComputedStyle(document.documentElement).scrollbarGutter.includes("stable");
        const previouslyFocused = document.activeElement as HTMLElement | null;

        body.style.overflow = "hidden";
        if (scrollbarWidth > 0 && !gutterIsStable) body.style.paddingRight = `${scrollbarWidth}px`;

        // El resto de la página queda inerte: ni el TAB ni los lectores de pantalla salen del modal.
        const inertSiblings = Array.from(body.children).filter(
            (element): element is HTMLElement =>
                element instanceof HTMLElement && element !== overlayRef.current && !element.inert,
        );
        inertSiblings.forEach((element) => {
            element.inert = true;
        });

        // El contenido puede marcar su acción principal con data-autofocus; si no, se enfoca cerrar.
        const autofocusTarget = dialogRef.current?.querySelector<HTMLElement>("[data-autofocus]");
        (autofocusTarget ?? closeButtonRef.current)?.focus({ preventScroll: true });

        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === "Escape") {
                onCloseRef.current();
                return;
            }

            // TAB recorre en ciclo los elementos del modal (Shift+TAB en sentido inverso).
            const dialog = dialogRef.current;
            if (event.key !== "Tab" || !dialog) return;

            const focusable = getFocusableElements(dialog);
            if (focusable.length === 0) {
                event.preventDefault();
                return;
            }

            const first = focusable[0];
            const last = focusable[focusable.length - 1];
            const active = document.activeElement;
            const focusIsOutside = !dialog.contains(active);

            if (event.shiftKey && (active === first || focusIsOutside)) {
                event.preventDefault();
                last.focus();
            } else if (!event.shiftKey && (active === last || focusIsOutside)) {
                event.preventDefault();
                first.focus();
            }
        };
        window.addEventListener("keydown", handleKeyDown);

        return () => {
            body.style.overflow = previousOverflow;
            body.style.paddingRight = previousPaddingRight;
            window.removeEventListener("keydown", handleKeyDown);
            inertSiblings.forEach((element) => {
                element.inert = false;
            });
            previouslyFocused?.focus({ preventScroll: true });
        };
    }, [isMounted]);

    if (!isMounted) return null;

    const stateClass = isClosing ? "is-closing" : "is-open";

    return createPortal(
        <div ref={overlayRef} className={`modal-overlay ${stateClass}`} onClick={onClose}>
            <div
                className={["modal", stateClass, className].filter(Boolean).join(" ")}
                ref={dialogRef}
                role="dialog"
                aria-modal="true"
                aria-label={ariaLabel}
                onClick={(event) => event.stopPropagation()}
                onAnimationEnd={(event) => {
                    if (isClosing && event.target === event.currentTarget) setIsMounted(false);
                }}
            >
                {children}

                <button
                    ref={closeButtonRef}
                    type="button"
                    className="modal-close"
                    aria-label={closeLabel}
                    title={closeLabel}
                    onClick={onClose}
                >
                    <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
                        <path
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="2.25"
                            strokeLinecap="round"
                            d="M5 5l14 14M19 5L5 19"
                        />
                    </svg>
                </button>
            </div>
            {aside && <div onClick={(event) => event.stopPropagation()}>{aside}</div>}
        </div>,
        document.body,
    );
};
