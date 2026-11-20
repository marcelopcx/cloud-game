import { useEffect, useState } from "react";
import { checkHealth, type CrudHealth } from "@services";

const INTERVAL_MS = 15_000;

/** Estado de game-crud (GET /health), cada 15 s. null hasta la primera respuesta. */
export function useHealth(): CrudHealth | null {
    const [health, setHealth] = useState<CrudHealth | null>(null);

    useEffect(() => {
        let controller = new AbortController();
        const poll = () => {
            controller.abort();
            controller = new AbortController();
            checkHealth(controller.signal).then(setHealth).catch(() => {});
        };
        poll();
        const id = setInterval(poll, INTERVAL_MS);
        return () => {
            clearInterval(id);
            controller.abort();
        };
    }, []);

    return health;
}
