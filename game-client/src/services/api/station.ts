import { restUrl } from './urls.ts';
import { StatusGameStation } from '@/types';
import type { Health, LaunchResponse, PrepareResponse, StationErrorBody } from '@/types';

export const GAME_ID = import.meta.env.VITE_GAME_ID || 'test-pattern';

const ERROR_UI: Record<string, string> = {
    STATION_BUSY: 'El Spark está en partida. Probá en un minuto.',
    GAME_NOT_READY: 'Hay que esperar a que termine de preparar.',
    PREPARE_IN_PROGRESS: 'La estación ya está ocupada.',
    PEER_BUSY: 'Ya hay un peer en esta estación.',
    NOT_PLAYING: 'La estación no está en partida.',
    CHECKSUM_MISMATCH: 'El juego en cache no coincide. Volvé a preparar.',
    SOURCE_NOT_FOUND: 'No está el artefacto en la library de la estación.',
    UNSUPPORTED_SOURCE: 'Ese tipo de source no está en este corte.',
    UNKNOWN_GAME: 'Esta estación no tiene ese juego.',
    PREPARE_FAILED: 'La estación no pudo preparar el juego.',
    PREPARE_TIMEOUT: 'La estación tardó demasiado en preparar el juego.',
    CAPACITY_EXCEEDED: 'No hay estaciones libres en este momento. Probá en un minuto.',
    IMAGE_NOT_FOUND: 'Este juego no está instalado en el servidor.',
    DOCKER_UNAVAILABLE: 'El servidor de juegos no responde.',
    DOCKER_ERROR: 'El servidor de juegos no pudo crear la estación.',
    CONTAINER_NOT_FOUND: 'La estación de esta partida ya no existe.',
    CONTAINER_FAILED: 'La estación se cayó al arrancar.',
    CONTAINER_TIMEOUT: 'La estación tardó demasiado en arrancar.',
    UNAUTHORIZED: 'El servidor de juegos rechazó la solicitud.',
    GAME_NOT_FOUND: 'Este juego no está instalado en el servidor.',
    GAME_UNAVAILABLE: 'Este juego todavía no está disponible.',
    SESSION_NOT_FOUND: 'La partida ya no existe.',
    CATALOG_UNAVAILABLE: 'El servidor de juegos está arrancando. Probá en unos segundos.',
    PROVISION_FAILED: 'No se pudo preparar una estación para este juego.',
    STATION_LOST: 'La estación de esta partida dejó de responder.',
    INACTIVITY: 'La partida terminó por inactividad.',
    DISCONNECTED: 'La partida terminó porque se perdió la conexión.',
    CLAIM_TIMEOUT: 'La estación se liberó porque la partida no arrancó a tiempo.',
    REPLACED: 'Abriste otra partida; esta se cerró.',
    PLAYING_ELSEWHERE: 'Ya estás jugando este juego en otro dispositivo.',
};

export function stationErrorMessage(code: string) {
    return ERROR_UI[code] ?? code;
}

export class StationRequestError extends Error {
    readonly code: string;

    constructor(code: string) {
        super(stationErrorMessage(code));
        this.name = 'StationRequestError';
        this.code = code;
    }
}

async function readError(res: Response): Promise<string> {
    try {
        const body = (await res.json()) as StationErrorBody;
        return body.error?.code ?? `HTTP ${res.status}`;
    } catch {
        return `HTTP ${res.status}`;
    }
}

export function newSessionId() {
    const c = globalThis.crypto;
    if (c && typeof c.randomUUID === 'function') {
        return c.randomUUID();
    }
    const bytes = new Uint8Array(16);
    if (c && typeof c.getRandomValues === 'function') {
        c.getRandomValues(bytes);
    } else {
        for (let i = 0; i < 16; i++) bytes[i] = Math.floor(Math.random() * 256);
    }
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

export async function getHealth(gameId?: string): Promise<Health> {
    const res = await fetch(restUrl('/health', gameId));
    if (!res.ok) throw new StationRequestError(await readError(res));
    return res.json() as Promise<Health>;
}

export async function prepare(sessionId: string, gameId = GAME_ID): Promise<PrepareResponse> {
    const res = await fetch(restUrl('/prepare', gameId), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            sessionId,
            gameId,
            version: '1.0.0',
            source: {
                type: 'local',
                path: `/library/${gameId}/1.0.0`,
                checksum: 'sha256:00',
            },
        }),
    });
    if (res.status !== 202) throw new StationRequestError(await readError(res));
    return res.json() as Promise<PrepareResponse>;
}

/** `saveGrant`: permiso de save del dispatcher (jugador con sesión); la estación restaura y guarda su progreso. */
export async function launch(sessionId: string, gameId = GAME_ID, saveGrant?: string): Promise<LaunchResponse> {
    const res = await fetch(restUrl('/launch', gameId), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sessionId, gameId, saveGrant: saveGrant ?? null }),
    });
    if (!res.ok) throw new StationRequestError(await readError(res));
    return res.json() as Promise<LaunchResponse>;
}

export async function stop(sessionId: string, gameId?: string) {
    const res = await fetch(restUrl('/stop', gameId), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sessionId }),
    });
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
 * Espera a que la estación deje {@code sessionId} en READY tras el 202 de prepare.
 * Consulta /health en vez de fiarse del socket para no depender del orden
 * entre la respuesta HTTP y el evento STATE.
 */
export async function waitUntilReady(
    sessionId: string,
    { gameId, signal, timeoutMs = 120_000, intervalMs = 500 }: {
        gameId?: string;
        signal?: AbortSignal;
        timeoutMs?: number;
        intervalMs?: number;
    } = {},
): Promise<Health> {
    const deadline = Date.now() + timeoutMs;
    for (;;) {
        signal?.throwIfAborted();
        const health = await getHealth(gameId);
        const ours = health.sessionId === sessionId;
        if (ours && health.state === StatusGameStation.READY) return health;
        if (health.state === StatusGameStation.FAILED) throw new StationRequestError('PREPARE_FAILED');
        // Tras el 202 la estación queda en PREPARING con nuestra sesión; si no, se cayó o la tomó otro.
        if (!ours) {
            throw new StationRequestError(
                health.state === StatusGameStation.IDLE ? 'PREPARE_FAILED' : 'STATION_BUSY',
            );
        }
        if (Date.now() > deadline) throw new StationRequestError('PREPARE_TIMEOUT');
        await sleep(intervalMs, signal);
    }
}

export const stationApi = {
    getHealth,
    prepare,
    waitUntilReady,
    launch,
    stop,
    newSessionId,
};
