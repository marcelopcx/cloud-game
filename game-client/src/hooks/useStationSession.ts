import { useCallback, useEffect, useState } from 'react';
import {
    dispatcherEnabled,
    getSession,
    releaseSession,
    requestSession,
    setStationBase,
    stationErrorMessage,
    waitUntilAssigned,
} from '@services';
import type { PlaySession } from '@/types';

export type SessionPhase = 'queued' | 'provisioning' | 'ready' | 'error';

/** Cada cuánto se revisa la sesión en partida (para enterarse de un fin por inactividad). */
const WATCH_MS = 5000;

/**
 * Con Game Dispatcher: al montarse pide una partida de {@code gameId}, espera
 * a que le asignen estación (en cola o mientras se crea una reservada para
 * este jugador) y apunta las URLs del juego a ella. Al desmontarse (salir) o
 * cerrar la pestaña libera la sesión; la estación vuelve al pool. Sin
 * dispatcher queda {@code ready} de entrada y se usa la estación fija.
 */
export const useStationSession = (gameId: string) => {
    const [phase, setPhase] = useState<SessionPhase>(dispatcherEnabled ? 'provisioning' : 'ready');
    const [error, setError] = useState<string | null>(null);
    const [session, setSession] = useState<PlaySession | null>(null);
    const [attempt, setAttempt] = useState(0);

    useEffect(() => {
        if (!dispatcherEnabled) return;
        const abort = new AbortController();
        let id: string | null = null;

        const release = (keepalive: boolean) => {
            if (!id) return;
            const current = id;
            id = null;
            setStationBase(gameId, null);
            void releaseSession(current, { keepalive }).catch(() => undefined);
        };
        const onPageHide = () => release(true);
        window.addEventListener('pagehide', onPageHide);

        const fail = (err: unknown) => {
            if (abort.signal.aborted) return;
            release(false);
            setError(err instanceof Error ? err.message : 'No se pudo iniciar la partida');
            setPhase('error');
        };

        // Diferido: si el efecto se desmonta enseguida (StrictMode) no se pide nada.
        const timer = window.setTimeout(async () => {
            setPhase('provisioning');
            setError(null);
            setSession(null);
            try {
                // Sin signal: si el POST se cortara a mitad, la sesión quedaría sin id para liberarla.
                const created = await requestSession(gameId);
                id = created.sessionId;
                if (abort.signal.aborted) {
                    release(false);
                    return;
                }
                const assigned = await waitUntilAssigned(created, {
                    signal: abort.signal,
                    onUpdate: (s) => {
                        setSession(s);
                        setPhase(s.status === 'QUEUED' ? 'queued' : 'provisioning');
                    },
                });
                setStationBase(gameId, assigned.station!.stationBase);
                setSession(assigned);
                setPhase('ready');
            } catch (err) {
                fail(err);
                return;
            }
            // En partida: si el dispatcher la termina (inactividad, estación caída) se muestra el motivo.
            while (!abort.signal.aborted && id) {
                await new Promise((r) => window.setTimeout(r, WATCH_MS));
                if (abort.signal.aborted || !id) return;
                try {
                    const current = await getSession(id, abort.signal);
                    if (current.status === 'ENDED' || current.status === 'FAILED') {
                        id = null;
                        setStationBase(gameId, null);
                        setError(stationErrorMessage(current.endReason ?? 'SESSION_NOT_FOUND'));
                        setPhase('error');
                        return;
                    }
                } catch {
                    // Un fallo de red puntual no corta la partida.
                }
            }
        }, 0);

        return () => {
            window.clearTimeout(timer);
            abort.abort();
            window.removeEventListener('pagehide', onPageHide);
            release(false);
        };
    }, [gameId, attempt]);

    const retry = useCallback(() => setAttempt((n) => n + 1), []);

    return { phase, error, session, retry };
};
