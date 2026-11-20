import type { FriendEntry, FriendPlaying, FriendRequest, FriendSearchResult } from '@/types';
import { getJson } from './catalog.ts';

// Todo /catalog/friends va con el token del jugador. Los errores llegan como CatalogRequestError
// con el código de game-crud (`UNAUTHORIZED`, `CONFLICT`, `DISPATCHER_UNAVAILABLE`, …).
const auth = (token: string, init?: RequestInit): RequestInit => ({
    ...init,
    headers: { ...init?.headers, Authorization: `Bearer ${token}` },
});

/** Todos los amigos: primero los que juegan, después el resto por username. */
export function getFriends(token: string, signal?: AbortSignal): Promise<FriendEntry[]> {
    return getJson<FriendEntry[]>('/friends', signal, auth(token));
}

/** Amigos en partida, el que empezó más recién primero. */
export function getFriendsPlaying(token: string, signal?: AbortSignal): Promise<FriendPlaying[]> {
    return getJson<FriendPlaying[]>('/friends/playing', signal, auth(token));
}

/** Bandeja de entrada: solicitudes pendientes recibidas. */
export function getFriendRequests(token: string, signal?: AbortSignal): Promise<FriendRequest[]> {
    return getJson<FriendRequest[]>('/friends/requests', signal, auth(token));
}

/** Jugadores cuyo username contiene `query`, con su relación con el jugador. */
export function searchPlayers(token: string, query: string, signal?: AbortSignal, limit = 10): Promise<FriendSearchResult[]> {
    const params = new URLSearchParams({ q: query, limit: String(limit) });
    return getJson<FriendSearchResult[]>(`/friends/search?${params}`, signal, auth(token));
}

/** Envía la solicitud; si el otro ya había enviado una, queda aceptada (`relation: 'FRIEND'`). */
export function sendFriendRequest(token: string, userId: number): Promise<FriendSearchResult> {
    return getJson<FriendSearchResult>('/friends/requests', undefined, auth(token, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId }),
    }));
}

export async function respondFriendRequest(token: string, requestId: number, accept: boolean): Promise<void> {
    await getJson<void>(`/friends/requests/${requestId}/${accept ? 'accept' : 'reject'}`, undefined,
        auth(token, { method: 'POST' }));
}
