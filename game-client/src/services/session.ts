import type { UserResponse } from '@/types';

const KEY = 'cloudgame.session';

export interface PlayerSession {
    /** JWT de game-crud: va como `Authorization: Bearer` al dispatcher. */
    token: string;
    user: UserResponse;
}

let current: PlayerSession | null | undefined;
const listeners = new Set<() => void>();

function read(): PlayerSession | null {
    try {
        const raw = localStorage.getItem(KEY);
        if (!raw) return null;
        const parsed = JSON.parse(raw) as PlayerSession;
        return parsed?.token && parsed.user ? parsed : null;
    } catch {
        return null;
    }
}

function emit() {
    listeners.forEach((fn) => fn());
}

/**
 * Sesión del jugador (con progreso guardado). Sin sesión se juega como anónimo
 * (`X-Player-Id`) y el progreso se borra al salir. Persiste en localStorage; si
 * el storage no está disponible vive en memoria mientras dure la pestaña.
 */
export function currentSession(): PlayerSession | null {
    if (current === undefined) current = read();
    return current;
}

export function saveSession(session: PlayerSession) {
    current = session;
    try {
        localStorage.setItem(KEY, JSON.stringify(session));
    } catch {
        // Solo en memoria.
    }
    emit();
}

export function logout() {
    current = null;
    try {
        localStorage.removeItem(KEY);
    } catch {
        // Nada que borrar.
    }
    emit();
}

/** Para `useSyncExternalStore`. */
export function subscribeSession(fn: () => void) {
    listeners.add(fn);
    return () => {
        listeners.delete(fn);
    };
}
