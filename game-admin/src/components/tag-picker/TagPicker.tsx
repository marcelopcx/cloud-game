import { useId, useState, type KeyboardEvent } from "react";
import { CloseIcon, PlusIcon } from "../icons/Icons.tsx";
import "./tagPicker.css";

export interface TagPickerProps {
    /** Nombre de los inputs ocultos, uno por elegido (se leen con FormData.getAll). */
    name: string;
    label: string;
    /** Sugerencias; null mientras cargan. */
    options: string[] | null;
    optionsError?: string | null;
    defaultValue?: string[];
    onChange?: (selected: string[]) => void;
    placeholder?: string;
    hint?: string;
}

/** "Acción" y "accion" coinciden al buscar. */
const normalize = (text: string) => text.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase().trim();

const MAX_SUGGESTIONS = 16;

/**
 * Chips con buscador. Sugiere las opciones existentes y permite agregar una nueva
 * (game-crud crea por nombre las categorías y gamepads que no existan).
 */
export const TagPicker = ({
    name,
    label,
    options,
    optionsError,
    defaultValue = [],
    onChange,
    placeholder = "Buscar o agregar…",
    hint,
}: TagPickerProps) => {
    const inputId = useId();
    const [selected, setSelected] = useState<string[]>(defaultValue);
    const [query, setQuery] = useState("");

    const update = (next: string[]) => {
        setSelected(next);
        onChange?.(next);
    };

    const q = normalize(query);
    const taken = new Set(selected.map(normalize));
    const matches = (options ?? []).filter((o) => !taken.has(normalize(o)) && normalize(o).includes(q));
    // Se ofrece crear si el texto no coincide exacto con una opción ni con algo ya elegido.
    const canCreate = q !== "" && !taken.has(q) && !(options ?? []).some((o) => normalize(o) === q);

    const add = (value: string) => {
        const clean = value.trim().replace(/\s+/g, " ");
        if (!clean || taken.has(normalize(clean))) return;
        update([...selected, clean]);
        setQuery("");
    };

    const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
        // Enter (o coma) agrega en vez de enviar el formulario: primero una sugerencia que
        // empiece por el texto, si no el texto como nuevo, y si no la primera sugerencia.
        if (event.key === "Enter" || event.key === ",") {
            event.preventDefault();
            if (q === "") return;
            const prefix = matches.find((m) => normalize(m).startsWith(q));
            if (prefix) add(prefix);
            else if (canCreate) add(query);
            else if (matches.length > 0) add(matches[0]);
        } else if (event.key === "Backspace" && query === "" && selected.length > 0) {
            update(selected.slice(0, -1));
        }
    };

    return (
        <div className="tag-picker">
            <label className="tag-picker__label" htmlFor={inputId}>{label}</label>
            <div className="tag-picker__box">
                {selected.map((tag) => (
                    <span key={tag} className="tag-picker__chip">
                        {tag}
                        <button type="button" aria-label={`Quitar ${tag}`} onClick={() => update(selected.filter((t) => t !== tag))}>
                            <CloseIcon size={12} />
                        </button>
                    </span>
                ))}
                <input
                    id={inputId}
                    type="text"
                    autoComplete="off"
                    placeholder={selected.length === 0 ? placeholder : ""}
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    onKeyDown={handleKeyDown}
                />
            </div>

            <div className="tag-picker__suggestions" aria-label={`Sugerencias de ${label.toLowerCase()}`}>
                {optionsError && <span className="tag-picker__message">No se pudieron cargar: {optionsError}</span>}
                {!optionsError && options === null && <span className="tag-picker__message">Cargando…</span>}
                {matches.slice(0, MAX_SUGGESTIONS).map((option) => (
                    <button key={option} type="button" className="tag-picker__suggestion" onClick={() => add(option)}>
                        {option}
                    </button>
                ))}
                {canCreate && (
                    <button type="button" className="tag-picker__suggestion tag-picker__suggestion--new" onClick={() => add(query)}>
                        <PlusIcon size={12} /> Crear «{query.trim()}»
                    </button>
                )}
                {options !== null && matches.length === 0 && !canCreate && q !== "" && (
                    <span className="tag-picker__message">Ya está elegida.</span>
                )}
            </div>
            {hint && <small className="tag-picker__hint">{hint}</small>}

            {selected.map((tag) => (
                <input key={tag} type="hidden" name={name} value={tag} />
            ))}
        </div>
    );
};
