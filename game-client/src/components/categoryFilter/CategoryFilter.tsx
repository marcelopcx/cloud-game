import { useCallback, useEffect, useRef, useState, type HTMLAttributes } from "react";
import "./categoryFilter.css";

/**
 * - `filter`: chips seleccionables en una fila con scroll horizontal y flechas.
 * - `tag`: etiquetas de solo lectura, compactas, que saltan de línea si no caben.
 */
export type CategoryFilterVariant = "filter" | "tag";

export interface CategoryFilterProps extends Omit<HTMLAttributes<HTMLUListElement>, "onChange"> {
    categories: string[];
    variant?: CategoryFilterVariant;
    /** Categoría seleccionada (modo controlado). Si se omite, el componente maneja su propio estado. */
    selected?: string | null;
    onChange?: (category: string | null) => void;
    /** Solo en `filter`: agrega al inicio un chip con este texto que quita el filtro (activo si no hay selección). */
    allLabel?: string;
}

export const CategoryFilter = ({
    categories,
    variant = "filter",
    selected,
    onChange,
    allLabel,
    className = "",
    ...rest
}: CategoryFilterProps) => {
    const [internalSelected, setInternalSelected] = useState<string | null>(null);
    const listRef = useRef<HTMLUListElement>(null);
    const [overflowStart, setOverflowStart] = useState(false);
    const [overflowEnd, setOverflowEnd] = useState(false);

    const isControlled = selected !== undefined;
    const current = isControlled ? selected : internalSelected;

    const filterClassName = [
        "category-filter",
        !overflowStart && !overflowEnd && "category-filter--centered",
        overflowStart && "category-filter--overflow-start",
        overflowEnd && "category-filter--overflow-end",
        className,
    ]
        .filter(Boolean)
        .join(" ");

    const handleClick = (category: string) => {
        // Con chip "todos", tocar la categoría activa la deja seleccionada: se quita el filtro desde ese chip.
        const next = current === category && !allLabel ? null : category;
        if (!isControlled) setInternalSelected(next);
        onChange?.(next);
    };

    const isTag = variant === "tag";

    const updateOverflowState = useCallback(() => {
        const list = listRef.current;
        if (!list) return;

        setOverflowStart(list.scrollLeft > 1);
        setOverflowEnd(list.scrollLeft + list.clientWidth < list.scrollWidth - 1);
    }, []);

    useEffect(() => {
        const list = listRef.current;
        if (!list || isTag) return;

        updateOverflowState();

        const observer = new ResizeObserver(updateOverflowState);
        observer.observe(list);
        for (const item of list.children) observer.observe(item);
        list.addEventListener("scroll", updateOverflowState, { passive: true });

        return () => {
            observer.disconnect();
            list.removeEventListener("scroll", updateOverflowState);
        };
    }, [updateOverflowState, categories, isTag]);

    const scrollCategories = (direction: 1 | -1) => {
        const list = listRef.current;
        if (!list) return;

        const items = Array.from(list.children) as HTMLElement[];
        if (items.length === 0) return;

        const viewStart = list.scrollLeft;
        const viewEnd = viewStart + list.clientWidth;

        const target =
            direction === 1
                ? items.find((item) => item.offsetLeft + item.offsetWidth > viewEnd + 1)
                : [...items].reverse().find((item) => item.offsetLeft < viewStart - 1);

        if (!target) return;

        list.scrollTo({ left: target.offsetLeft, behavior: "smooth" });
    };

    if (categories.length === 0) return null;

    if (isTag) {
        return (
            <ul className={["category-filter", "category-filter--tag", className].filter(Boolean).join(" ")} aria-label="Categorías" {...rest}>
                {categories.map((category) => (
                    <li key={category}>
                        <span className="category-filter-chip category-filter-chip--tag">{category}</span>
                    </li>
                ))}
            </ul>
        );
    }

    const showControls = overflowStart || overflowEnd;

    return (
        <div className="category-filter-container">
            {showControls && (
                <button
                    type="button"
                    className="category-filter-arrow category-filter-arrow--prev"
                    aria-label="Categorías anteriores"
                    disabled={!overflowStart}
                    onClick={() => scrollCategories(-1)}
                />
            )}

            <ul ref={listRef} className={filterClassName} aria-label="Categorías" {...rest}>
                {allLabel && (
                    <li>
                        <button
                            type="button"
                            className={["category-filter-chip", current === null && "category-filter-chip--active"]
                                .filter(Boolean)
                                .join(" ")}
                            aria-pressed={current === null}
                            onClick={() => {
                                if (!isControlled) setInternalSelected(null);
                                onChange?.(null);
                            }}
                        >
                            {allLabel}
                        </button>
                    </li>
                )}
                {categories.map((category) => {
                    const isActive = current === category;

                    return (
                        <li key={category}>
                            <button
                                type="button"
                                className={[
                                    "category-filter-chip",
                                    isActive && "category-filter-chip--active",
                                ]
                                    .filter(Boolean)
                                    .join(" ")}
                                aria-pressed={isActive}
                                onClick={() => handleClick(category)}
                            >
                                {category}
                            </button>
                        </li>
                    );
                })}
            </ul>

            {showControls && (
                <button
                    type="button"
                    className="category-filter-arrow category-filter-arrow--next"
                    aria-label="Categorías siguientes"
                    disabled={!overflowEnd}
                    onClick={() => scrollCategories(1)}
                />
            )}
        </div>
    );
};
