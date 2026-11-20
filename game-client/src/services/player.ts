const KEY = 'cloudgame.playerId';

let memoryId: string | null = null;

function randomId() {
    const c = globalThis.crypto;
    if (c && typeof c.randomUUID === 'function') return c.randomUUID();
    return `p-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}

/**
 * Identidad anónima del jugador para el Game Dispatcher (header `X-Player-Id`).
 * Persiste en localStorage; si el storage no está disponible (modo privado,
 * bloqueado) vive en memoria mientras dure la pestaña.
 */
export function playerId(): string {
    if (memoryId) return memoryId;
    try {
        const stored = localStorage.getItem(KEY);
        if (stored && /^[A-Za-z0-9-]{8,64}$/.test(stored)) {
            memoryId = stored;
            return stored;
        }
        const id = randomId();
        localStorage.setItem(KEY, id);
        memoryId = id;
        return id;
    } catch {
        memoryId = randomId();
        return memoryId;
    }
}
