import type { FriendEntry } from '@/types';
import { CATALOG } from './catalog.ts';

/**
 * Cambios en vivo de `/catalog/friends/events` (SSE):
 * - `friend`: un amigo empezó o dejó de jugar (`game` y `since` en null si dejó).
 * - `friends`: cambió la lista de amigos → volver a pedirla.
 * - `requests`: cambió la bandeja de solicitudes → volver a pedirla.
 * - `resync`: se pudieron perder cambios (reconexión) → volver a pedir todo.
 */
export type FriendEvent =
    | { type: 'friend'; friend: FriendEntry }
    | { type: 'friends' }
    | { type: 'requests' }
    | { type: 'resync' };

type Listener = (event: FriendEvent) => void;

const MAX_BACKOFF_MS = 30_000;

/** Una sola conexión por token, compartida por todos los que escuchan (carrusel, panel…). */
let current: { token: string; listeners: Set<Listener>; abort: AbortController } | null = null;

/** Escucha los eventos de amigos; devuelve la función para dejar de escuchar. */
export function subscribeFriendEvents(token: string, listener: Listener): () => void {
    if (current?.token !== token) {
        current?.abort.abort();
        current = { token, listeners: new Set(), abort: new AbortController() };
        void run(current.token, current.listeners, current.abort.signal);
    }
    const conn = current;
    conn.listeners.add(listener);
    return () => {
        conn.listeners.delete(listener);
        if (conn.listeners.size === 0) {
            conn.abort.abort();
            if (current === conn) current = null;
        }
    };
}

async function run(token: string, listeners: Set<Listener>, signal: AbortSignal) {
    const emit = (event: FriendEvent) => listeners.forEach((l) => l(event));
    let backoff = 1000;
    let first = true;
    while (!signal.aborted) {
        try {
            const res = await fetch(`${CATALOG}/friends/events`, {
                headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
                signal,
            });
            // Token vencido: no tiene sentido reintentar (el resto de la app cierra la sesión).
            if (res.status === 401) return;
            if (!res.ok || !res.body) throw new Error(`friends/events ${res.status}`);
            // Lo que pasó mientras no había conexión se recupera recargando.
            if (!first) emit({ type: 'resync' });
            first = false;
            backoff = 1000;
            await read(res.body, emit);
        } catch {
            if (signal.aborted) return;
        }
        await new Promise((resolve) => setTimeout(resolve, backoff));
        backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
    }
}

/** Parser SSE mínimo: `event:` + `data:` hasta la línea vacía; los comentarios (`:ping`) se ignoran. */
async function read(body: ReadableStream<Uint8Array>, emit: (event: FriendEvent) => void) {
    const reader = body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let name = '';
    let data = '';
    for (;;) {
        const { value, done } = await reader.read();
        if (done) return;
        buffer += decoder.decode(value, { stream: true });
        let newline: number;
        while ((newline = buffer.indexOf('\n')) >= 0) {
            const line = buffer.slice(0, newline).replace(/\r$/, '');
            buffer = buffer.slice(newline + 1);
            if (line === '') {
                dispatch(name, data, emit);
                name = '';
                data = '';
            } else if (line.startsWith('event:')) {
                name = line.slice(6).trim();
            } else if (line.startsWith('data:')) {
                data += line.slice(5).trim();
            }
        }
    }
}

function dispatch(name: string, data: string, emit: (event: FriendEvent) => void) {
    switch (name) {
        case 'friend':
            try {
                emit({ type: 'friend', friend: JSON.parse(data) as FriendEntry });
            } catch {
                emit({ type: 'friends' });
            }
            return;
        case 'friends':
        case 'requests':
        case 'resync':
            emit({ type: name });
    }
}
