import { useCallback, useEffect, useRef, useState } from 'react';
import {
    connectControl,
    getHealth,
    launch as launchStation,
    newSessionId,
    prepare as prepareStation,
    stationErrorMessage,
    stop as stopStation,
    waitUntilReady,
    GAME_ID,
} from '@services';
import { attachWebrtc } from '@/webrtc/session.ts';
import { StatusGameStation } from '@/types';
import type { RtcStatsSnapshot } from '@/webrtc/stats.ts';
import { normalizeNeeds, type InputNeeds } from '@/webrtc/input.ts';
import type { GameSize } from '@/webrtc/pointer.ts';

const MAX_LOGS = 120;

/**
 * Muestras de estadísticas consecutivas sin señal de video antes de dar la sesión por caída.
 * Las stats llegan una vez por segundo, así que son ~8 s de tolerancia antes del aviso.
 */
const SILENT_TICKS = 8;

function gameSizeOf(body: { gameWidth?: number; gameHeight?: number }): GameSize | null {
    const { gameWidth: width, gameHeight: height } = body;
    if (typeof width !== 'number' || typeof height !== 'number' || width <= 0 || height <= 0) return null;
    return { width, height };
}

/**
 * @param initialSessionId sesión con la que el Game Dispatcher ya dejó la
 *        estación en READY; el primer play la lanza sin volver a preparar.
 * @param saveGrant permiso de save de la sesión del dispatcher (jugador con
 *        sesión): la estación restaura el progreso al lanzar y lo guarda al parar.
 */
export const useStation = (stationGameId = GAME_ID, initialSessionId?: string, saveGrant?: string) => {
    const [status, setStatus] = useState<StatusGameStation>(StatusGameStation.IDLE);
    const [progress, setProgress] = useState(0);
    const [connected, setConnected] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [logs, setLogs] = useState<string[]>([]);
    const [hasTrack, setHasTrack] = useState(false);
    const [needs, setNeeds] = useState<InputNeeds>(() => normalizeNeeds(null));
    const [padIds, setPadIds] = useState<string[]>([]);
    const [pointerLocked, setPointerLocked] = useState(false);
    // Menú de opciones de video: mouse on/off (en launch se apaga si el juego usa gamepad).
    const [mouseOn, setMouseOn] = useState(true);
    const [rtcStats, setRtcStats] = useState<RtcStatsSnapshot | null>(null);
    const [sessionId, setSessionId] = useState(() => initialSessionId ?? newSessionId());
    /** La sesión terminó (inactividad o caída del stream): se muestra el aviso para volver al menú. */
    const [disconnected, setDisconnected] = useState(false);

    const videoRef = useRef<HTMLVideoElement | null>(null);
    const audioRef = useRef<HTMLAudioElement | null>(null);
    const peerRef = useRef<ReturnType<typeof attachWebrtc> | null>(null);
    const playAbortRef = useRef<AbortController | null>(null);
    /** Ref y no estado: el pointer lo lee en cada evento, sin re-render. */
    const gameSizeRef = useRef<GameSize | null>(null);
    /** Ref síncrono del toggle: el pump de input lo lee en cada tick (250 Hz). */
    const mouseEnabledRef = useRef(true);
    /** Muestras de stats consecutivas con el video mudo. */
    const silentTicksRef = useRef(0);

    const appendLog = useCallback((line: string) => {
        const stamp = new Date().toLocaleTimeString();
        console.info('[cloudgame]', line);
        setLogs((prev) => [...prev.slice(-(MAX_LOGS - 1)), `${stamp}  ${line}`]);
    }, []);

    const closePeer = useCallback(() => {
        peerRef.current?.close();
        peerRef.current = null;
        const video = videoRef.current;
        if (video) video.srcObject = null;
        const audio = audioRef.current;
        if (audio) {
            audio.pause();
            audio.srcObject = null;
        }
        setHasTrack(false);
        setPadIds([]);
        setPointerLocked(false);
        setMouseOn(true);
        mouseEnabledRef.current = true;
        setRtcStats(null);
        silentTicksRef.current = 0;
        setDisconnected(false);
    }, []);

    /** Fallo terminal del stream (socket caído, ICE fallida, video mudo). */
    const markDisconnected = useCallback(
        (reason: string) => {
            appendLog(`sesión caída: ${reason}`);
            // El watchdog lo detecta sin que el peer se entere: el input se suelta acá
            // para que el mando vuelva a la UI y pueda cerrar el aviso.
            peerRef.current?.releaseInput();
            setDisconnected(true);
        },
        [appendLog],
    );

    useEffect(() => {
        void getHealth(stationGameId)
            .then((data) => {
                if (data.state) setStatus(data.state);
                gameSizeRef.current = gameSizeOf(data) ?? gameSizeRef.current;
                if (data.needs) {
                    const n = normalizeNeeds(data.needs);
                    setNeeds(n);
                    appendLog(
                        `health ${data.state} pads=${n.gamepad} keyboard=${n.keyboard} mouse=${n.mouse}`,
                    );
                } else {
                    appendLog(`health ${data.state}`);
                }
            })
            .catch(() => {
                setStatus(StatusGameStation.FAILED);
                setError('Estación offline');
                appendLog('health failed');
            });

        const disconnect = connectControl(
            (ev) => {
                if (ev.type === 'STATE') {
                    if (ev.state) setStatus(ev.state);
                    gameSizeRef.current = gameSizeOf(ev) ?? gameSizeRef.current;
                    if (typeof ev.progress === 'number') setProgress(ev.progress);
                    setError(null);
                    appendLog(`STATE ${ev.state ?? ''} ${ev.progress ?? ''}${ev.cacheHit ? ' cacheHit' : ''}`.trim());
                    return;
                }
                if (ev.type === 'ERROR') {
                    setStatus(StatusGameStation.FAILED);
                    setError(ev.message || 'Error en la estación');
                    appendLog(`ERROR ${ev.code ?? ev.message ?? ''}`);
                }
            },
            (open) => {
                setConnected(open);
                appendLog(open ? 'ws control open' : 'ws control closed');
            },
            stationGameId,
        );

        return () => {
            playAbortRef.current?.abort();
            closePeer();
            disconnect();
        };
    }, [appendLog, closePeer, stationGameId]);

    useEffect(() => {
        if (status !== StatusGameStation.PLAYING) return;
        const timer = window.setTimeout(() => {
            const video = videoRef.current;
            if (!hasTrack) {
                setError('sin video — ¿display en la estación? / ¿HTTPS?');
                return;
            }
            if (video && video.videoWidth === 0) {
                setError('video sin frames — click en la pantalla');
            }
        }, 4000);
        return () => window.clearTimeout(timer);
    }, [status, hasTrack]);

    /**
     * Watchdog de stream mudo: la estación que apaga la sesión por inactividad deja la
     * conexión abierta y simplemente deja de mandar frames, así que no hay evento que
     * dispare nada. `videoKbps === 0` en varias muestras seguidas es la señal.
     */
    useEffect(() => {
        if (disconnected) return;
        if (status !== StatusGameStation.PLAYING || !hasTrack) return;
        const kbps = rtcStats?.videoKbps;
        if (kbps == null || kbps > 0) {
            silentTicksRef.current = 0;
            return;
        }
        silentTicksRef.current += 1;
        if (silentTicksRef.current < SILENT_TICKS) return;
        markDisconnected('sin señal de video');
    }, [status, hasTrack, rtcStats, disconnected, markDisconnected]);

    const prepare = async (gameId = stationGameId) => {
        setLoading(true);
        setError(null);
        try {
            await prepareStation(sessionId, gameId);
            appendLog(`prepare 202 game=${gameId}`);
            return true;
        } catch (err) {
            const message = err instanceof Error ? err.message : 'Error al preparar';
            setError(message);
            appendLog(`prepare error ${message}`);
            return false;
        } finally {
            setLoading(false);
        }
    };

    const launch = async (gameId = stationGameId) => {
        const video = videoRef.current;
        const audio = audioRef.current;
        if (video) {
            video.muted = false;
            video.playsInline = true;
            void video.play().catch(() => undefined);
        }
        if (audio) {
            audio.muted = false;
            void audio.play().catch(() => undefined);
        }
        setLoading(true);
        setError(null);
        try {
            const launched = await launchStation(sessionId, gameId, saveGrant);
            const inputNeeds = launched.needs ? normalizeNeeds(launched.needs) : needs;
            if (launched.needs) setNeeds(inputNeeds);
            appendLog(
                `launch 200 game=${gameId} pads=${inputNeeds.gamepad} keyboard=${inputNeeds.keyboard} mouse=${inputNeeds.mouse} relativeMouse=${Boolean(inputNeeds.relativeMouse)}`,
            );
            if (!video) throw new Error('No hay elemento de video');
            if (!audio) throw new Error('No hay elemento de audio');
            closePeer();
            // Con gamepad el mouse arranca apagado; el usuario lo prende desde el menú si lo quiere.
            const mouseDefault = inputNeeds.gamepad === 0;
            mouseEnabledRef.current = mouseDefault;
            setMouseOn(mouseDefault);
            video.muted = false;
            video.playsInline = true;
            video.tabIndex = 0;
            (document.activeElement as HTMLElement | null)?.blur();
            video.focus();
            audio.muted = false;
            void video.play().catch(() => undefined);
            void audio.play().catch(() => undefined);
            peerRef.current = attachWebrtc({
                signalPath: launched.wsUrl || '/ws/webrtc',
                gameId,
                video,
                audio,
                needs: inputNeeds,
                gameSize: () => gameSizeRef.current,
                onTrack: (stream) => setHasTrack(Boolean(stream)),
                onLog: appendLog,
                onError: (code) => setError(stationErrorMessage(code)),
                onDisconnected: markDisconnected,
                onPads: setPadIds,
                onPointerLock: setPointerLocked,
                onStats: setRtcStats,
                mouseEnabled: () => mouseEnabledRef.current,
            });
        } catch (err) {
            const message = err instanceof Error ? err.message : 'Error al lanzar';
            setError(message);
            appendLog(`launch error ${message}`);
            closePeer();
        } finally {
            setLoading(false);
        }
    };

    /** Botón "Jugar": prepare, espera READY de esta sesión y lanza. Si ya está READY con ella, lanza directo. */
    const play = async (gameId = stationGameId) => {
        playAbortRef.current?.abort();
        const abort = new AbortController();
        playAbortRef.current = abort;

        const current = await getHealth(gameId).catch(() => null);
        if (abort.signal.aborted) return;
        if (current?.state === StatusGameStation.READY && current.sessionId === sessionId) {
            appendLog(`ready game=${gameId} (preparada por el dispatcher)`);
            await launch(gameId);
            return;
        }
        if (!(await prepare(gameId))) return;
        setLoading(true);
        try {
            await waitUntilReady(sessionId, { gameId, signal: abort.signal });
            appendLog(`ready game=${gameId}`);
        } catch (err) {
            if (abort.signal.aborted) return;
            const message = err instanceof Error ? err.message : 'Error al preparar';
            setStatus(StatusGameStation.FAILED);
            setError(message);
            appendLog(`prepare error ${message}`);
            return;
        } finally {
            setLoading(false);
        }
        if (abort.signal.aborted) return;
        await launch(gameId);
    };

    const stop = async () => {
        playAbortRef.current?.abort();
        playAbortRef.current = null;
        setLoading(true);
        setError(null);
        closePeer();
        try {
            await stopStation(sessionId, stationGameId);
            appendLog('stop 204');
            setSessionId(newSessionId());
        } catch (err) {
            const message = err instanceof Error ? err.message : 'Error al detener';
            setError(message);
            appendLog(`stop error ${message}`);
        } finally {
            setLoading(false);
        }
    };

    return {
        status,
        progress,
        connected,
        loading,
        error,
        logs,
        hasTrack,
        needs,
        padIds,
        pointerLocked,
        mouseOn,
        setMouseOn: (on: boolean) => {
            mouseEnabledRef.current = on;
            setMouseOn(on);
        },
        rtcStats,
        disconnected,
        videoRef,
        audioRef,
        prepare,
        launch,
        play,
        stop,
    };
};
