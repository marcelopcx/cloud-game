import { StationRequestError } from './station.ts';
import { playerId } from '../player.ts';
import { currentSession, logout } from '../session.ts';
import type { PlaySession, StationErrorBody } from '@/types';

const DISPATCHER = import.meta.env.VITE_DISPATCHER_URL;

/** Con `VITE_DISPATCHER_URL` cada partida pide una estación al Game Dispatcher; sin él, estación fija. */
export const dispatcherEnabled = Boolean(DISPATCHER);

async function readError(res: Response): Promise<string> {
    try {
        const body = (await res.json()) as StationErrorBody;
        return body.error?.code ?? `HTTP ${res.status}`;
    } catch {
        return `HTTP ${res.status}`;
    }
}

/** Con sesión, el dispatcher identifica al usuario por el token y la sesión trae `saveGrant`. */
function headers(json = false): HeadersInit {
    const h: Record<string, string> = { 'X-Player-Id': playerId() };
    const token = currentSession()?.token;
    if (token) h.Authorization = `Bearer ${token}`;
    if (json) h['Content-Type'] = 'application/json';
    return h;
}

/** Token vencido o inválido: se cierra la sesión y el próximo intento va como anónimo. */
async function failure(res: Response): Promise<StationRequestError> {
    if (res.status === 401 && currentSession()) {
        logout();
        return new StationRequestError('SESSION_EXPIRED');
    }
    return new StationRequestError(await readError(res));
}

/** 202: la sesión puede venir ya ASSIGNED o quedar QUEUED / PROVISIONING. */
export async function requestSession(gameId: string): Promise<PlaySession> {
    const res = await fetch(`${DISPATCHER}/sessions`, {
        method: 'POST',
        headers: headers(true),
        body: JSON.stringify({ gameId }),
    });
    if (res.status !== 202) throw await failure(res);
    return res.json() as Promise<PlaySession>;
}

export async function getSession(id: string, signal?: AbortSignal): Promise<PlaySession> {
    const res = await fetch(`${DISPATCHER}/sessions/${id}`, { headers: headers(), signal });
    if (!res.ok) throw await failure(res);
    return res.json() as Promise<PlaySession>;
}

/**
 * Idempotente. {@code keepalive} deja que el DELETE salga aunque la pestaña
 * se esté cerrando. El dispatcher recicla la estación; no la destruye.
 */
export async function releaseSession(id: string, { keepalive = false } = {}) {
    const res = await fetch(`${DISPATCHER}/sessions/${id}`, { method: 'DELETE', headers: headers(), keepalive });
    if (!res.ok && res.status !== 204) throw new StationRequestError(await readError(res));
}

function sleep(ms: number, signal?: AbortSignal) {
    return new Promise<void>((resolve, reject) => {
        const timer = window.setTimeout(resolve, ms);
        signal?.addEventListener('abort', () => {
            window.clearTimeout(timer);
            reject(signal.reason);
        }, { once: true });
    });
}

/**
 * Consulta la sesión hasta que tenga estación. Sin timeout propio: en cola
 * puede esperar lo que haga falta; el dispatcher la termina si algo falla.
 */
export async function waitUntilAssigned(
    session: PlaySession,
    { signal, intervalMs = 1000, onUpdate }: {
        signal?: AbortSignal;
        intervalMs?: number;
        onUpdate?: (session: PlaySession) => void;
    } = {},
): Promise<PlaySession> {
    let current = session;
    for (;;) {
        signal?.throwIfAborted();
        onUpdate?.(current);
        if (current.station && (current.status === 'ASSIGNED' || current.status === 'PLAYING')) return current;
        if (current.status === 'ENDED' || current.status === 'FAILED') {
            throw new StationRequestError(current.endReason ?? 'PROVISION_FAILED');
        }
        await sleep(intervalMs, signal);
        current = await getSession(session.sessionId, signal);
    }
}

export const dispatcherApi = {
    requestSession,
    getSession,
    releaseSession,
    waitUntilAssigned,
};
