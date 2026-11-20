import { useCallback, useEffect, useState } from 'react';
import {
    CatalogRequestError,
    getFriendRequests,
    getFriends,
    logout,
    respondFriendRequest,
    searchPlayers,
    sendFriendRequest,
    subscribeFriendEvents,
} from '@services';
import type { FriendEntry, FriendRequest, FriendSearchResult } from '@/types';

const SEARCH_DEBOUNCE_MS = 250;

/** Token vencido: se cierra la sesión (el header vuelve a "Iniciar sesión"). */
const handleError = (what: string, err: unknown) => {
    if (err instanceof CatalogRequestError && err.message === 'UNAUTHORIZED') logout();
    else console.error(`${what}:`, err);
};

/** Mismo orden que `GET /friends`: primero los que juegan (el más reciente arriba), después por username. */
const sortFriends = (list: FriendEntry[]) => [...list].sort((a, b) => {
    if (a.since && b.since) return b.since.localeCompare(a.since);
    if (a.since) return -1;
    if (b.since) return 1;
    return a.username.localeCompare(b.username, undefined, { sensitivity: 'base' });
});

/**
 * Centro de amigos del jugador con sesión: la bandeja de solicitudes (para el contador del botón)
 * y la lista de amigos mientras el panel la muestra (`watchFriends`). No se consulta cada tanto:
 * se recarga cuando `/friends/events` avisa que algo cambió.
 */
export const useFriends = (token: string | null, watchFriends: boolean) => {
    const [requests, setRequests] = useState<FriendRequest[]>([]);
    const [friends, setFriends] = useState<FriendEntry[] | null>(null);
    const [friendsError, setFriendsError] = useState(false);
    // Se incrementa para volver a pedir la lista (p. ej. al aceptar una solicitud).
    const [friendsVersion, setFriendsVersion] = useState(0);
    const reloadFriends = useCallback(() => setFriendsVersion((v) => v + 1), []);

    const loadRequests = useCallback((signal?: AbortSignal) => {
        if (!token) return;
        getFriendRequests(token, signal)
            .then(setRequests)
            .catch((err: unknown) => {
                if (!signal?.aborted) handleError('solicitudes de amistad', err);
            });
    }, [token]);

    useEffect(() => {
        if (!token) return;
        const abort = new AbortController();
        loadRequests(abort.signal);
        const unsubscribe = subscribeFriendEvents(token, (event) => {
            if (event.type === 'requests' || event.type === 'resync') loadRequests(abort.signal);
            if (event.type === 'friends' || event.type === 'resync') reloadFriends();
        });
        return () => {
            unsubscribe();
            abort.abort();
        };
    }, [token, loadRequests, reloadFriends]);

    useEffect(() => {
        if (!token || !watchFriends) return;
        const abort = new AbortController();
        getFriends(token, abort.signal)
            .then((list) => {
                setFriends(list);
                setFriendsError(false);
            })
            .catch((err: unknown) => {
                if (abort.signal.aborted) return;
                handleError('lista de amigos', err);
                setFriendsError(true);
            });
        return () => abort.abort();
    }, [token, watchFriends, friendsVersion]);

    /** Acepta o rechaza; la solicitud sale de la bandeja al instante y vuelve si falla. */
    const respond = useCallback(async (request: FriendRequest, accept: boolean) => {
        if (!token) return;
        setRequests((list) => list.filter((r) => r.id !== request.id));
        try {
            await respondFriendRequest(token, request.id, accept);
            if (accept) reloadFriends();
        } catch (err) {
            handleError('responder solicitud', err);
            loadRequests();
        }
    }, [token, loadRequests, reloadFriends]);

    return {
        requests: token ? requests : [],
        friends: token ? friends : null,
        friendsError,
        respond,
        reloadRequests: loadRequests,
        reloadFriends,
    };
};

/**
 * Todos los amigos del jugador con sesión. Quién juega se actualiza en vivo con los eventos
 * `friend` de `/friends/events`; la lista entera solo se vuelve a pedir si cambia o hubo reconexión.
 */
export const useFriendList = (token: string | null) => {
    const [list, setList] = useState<FriendEntry[]>([]);

    useEffect(() => {
        if (!token) return;
        const abort = new AbortController();
        const load = () => getFriends(token, abort.signal)
            .then(setList)
            .catch((err: unknown) => {
                if (!abort.signal.aborted) handleError('lista de amigos', err);
            });
        void load();
        const unsubscribe = subscribeFriendEvents(token, (event) => {
            if (event.type === 'friend') {
                const { friend } = event;
                setList((current) => {
                    // Un amigo que no está en la lista (recién agregado): se recarga entera.
                    if (!current.some((f) => f.userId === friend.userId)) {
                        void load();
                        return current;
                    }
                    return sortFriends(current.map((f) => (f.userId === friend.userId ? friend : f)));
                });
            } else if (event.type === 'friends' || event.type === 'resync') {
                void load();
            }
        });
        return () => {
            unsubscribe();
            abort.abort();
        };
    }, [token]);

    return token ? list : [];
};

/**
 * Buscador de jugadores para enviar solicitudes, con pausa al teclear como `useGameSearch`.
 * `send` actualiza la relación del resultado con lo que responde game-crud.
 */
export const useFriendSearch = (token: string | null, query: string) => {
    const [resolved, setResolved] = useState<{ text: string; results: FriendSearchResult[] }>({ text: '', results: [] });
    const [pending, setPending] = useState<Set<number>>(new Set());
    const text = query.trim();

    useEffect(() => {
        if (!token || !text) return;
        const abort = new AbortController();
        const timer = setTimeout(() => {
            searchPlayers(token, text, abort.signal)
                .then((results) => {
                    if (!abort.signal.aborted) setResolved({ text, results });
                })
                .catch((err: unknown) => {
                    if (abort.signal.aborted) return;
                    handleError('buscar jugadores', err);
                    setResolved({ text, results: [] });
                });
        }, SEARCH_DEBOUNCE_MS);
        return () => {
            clearTimeout(timer);
            abort.abort();
        };
    }, [token, text]);

    const send = useCallback(async (userId: number) => {
        if (!token) return;
        setPending((s) => new Set(s).add(userId));
        try {
            const updated = await sendFriendRequest(token, userId);
            setResolved((r) => ({ ...r, results: r.results.map((u) => (u.userId === userId ? updated : u)) }));
        } catch (err) {
            // 409: ya enviada o ya son amigos; se refleja como enviada para no ofrecer el botón otra vez.
            if (err instanceof CatalogRequestError && err.message === 'CONFLICT') {
                setResolved((r) => ({
                    ...r,
                    results: r.results.map((u) => (u.userId === userId && u.relation === 'NONE' ? { ...u, relation: 'SENT' } : u)),
                }));
            } else {
                handleError('enviar solicitud', err);
            }
        } finally {
            setPending((s) => {
                const next = new Set(s);
                next.delete(userId);
                return next;
            });
        }
    }, [token]);

    const loading = text !== '' && resolved.text !== text;
    return { results: text && !loading ? resolved.results : [], loading, pending, send };
};
