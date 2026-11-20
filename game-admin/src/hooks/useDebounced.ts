import { useEffect, useState } from "react";

/** El valor, pero solo después de `delay` ms sin cambios (para no pedir una búsqueda por tecla). */
export function useDebounced<T>(value: T, delay = 250): T {
    const [debounced, setDebounced] = useState(value);
    useEffect(() => {
        const id = setTimeout(() => setDebounced(value), delay);
        return () => clearTimeout(id);
    }, [value, delay]);
    return debounced;
}
