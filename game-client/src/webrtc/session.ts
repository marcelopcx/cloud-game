import { wsUrl } from '@services/api/urls.ts';
import { isGamepadMenuOpen, setGamepadClaimedBySession } from '@/gamepad/sessionGate.ts';
import { type InputNeeds, normalizeNeeds } from '@/webrtc/input.ts';
import { type GameSize, type MouseHud } from '@/webrtc/pointer.ts';
import { startInput } from '@/webrtc/pump.ts';
import { createPeer, type SignalIn } from '@/webrtc/peer.ts';
import { readRtcStats, type RtcStatsSnapshot } from '@/webrtc/stats.ts';

function bindStream(
    video: HTMLVideoElement,
    audio: HTMLAudioElement,
    picture: MediaStream,
    sound: MediaStream,
    onLog: (line: string) => void,
) {
    video.playsInline = true;
    video.autoplay = true;
    video.setAttribute('playsinline', 'true');
    video.setAttribute('webkit-playsinline', 'true');
    video.tabIndex = 0;
    video.focus();
    if (video.srcObject !== picture) {
        video.srcObject = picture;
    }
    audio.autoplay = true;
    audio.setAttribute('playsinline', 'true');
    if (audio.srcObject !== sound) {
        audio.srcObject = sound;
    }
    video.muted = false;
    audio.muted = false;
    const play = () => {
        void video.play().then(
            () => onLog(`video play ${video.videoWidth}x${video.videoHeight} muted=${video.muted}`),
            (err) => onLog(`video play ${err instanceof Error ? err.message : err}`),
        );
        void audio.play().then(
            () => onLog(`audio play muted=${audio.muted}`),
            (err) => onLog(`audio play ${err instanceof Error ? err.message : err}`),
        );
    };
    video.onloadedmetadata = play;
    video.onplaying = () => {
        onLog(`video playing ${video.videoWidth}x${video.videoHeight}`);
    };
    const videoTrack = picture.getVideoTracks()[0];
    if (videoTrack) {
        videoTrack.onunmute = () => onLog('track unmute');
        videoTrack.onmute = () => onLog('track mute');
    }
    play();
}

export function attachWebrtc(opts: {
    signalPath: string;
    /** Estación del juego en modo multi; sin él, la estación única. */
    gameId?: string;
    video: HTMLVideoElement;
    audio: HTMLAudioElement;
    needs?: InputNeeds | null;
    /** Resolución del juego para el mouse absoluto; el stream puede ser más chico. */
    gameSize?: () => GameSize | null;
    onTrack: (stream: MediaStream | null) => void;
    onLog: (line: string) => void;
    onError: (code: string) => void;
    /**
     * Fallo terminal del stream (socket de señalización caído, ICE fallida): la sesión
     * ya no se puede recuperar. El stream que sigue vivo pero mudo lo detecta el watchdog
     * de useStation con las estadísticas, no acá.
     */
    onDisconnected?: (reason: string) => void;
    onPads?: (ids: string[]) => void;
    onKeys?: (held: string[]) => void;
    onMouse?: (state: MouseHud | null) => void;
    onPointerLock?: (locked: boolean) => void;
    onStats?: (stats: RtcStatsSnapshot) => void;
    /** Pregunta en cada tick si el mouse viaja por el datagrama (toggle del menú). */
    mouseEnabled?: () => boolean;
}) {
    const needs = normalizeNeeds(opts.needs);
    const ws = new WebSocket(wsUrl(opts.signalPath, opts.gameId));
    let detachInput: (() => void) | null = null;
    const picture = new MediaStream();
    const sound = new MediaStream();
    let avBound = false;
    /** Separa "se cayó la sesión" del cierre que hacemos nosotros al salir o reintentar. */
    let stopped = false;
    /** El input ya se soltó (sesión caída o cerrada): un DataChannel tardío no lo vuelve a tomar. */
    let inputReleased = false;
    /**
     * Corta el input hacia la estación y le devuelve el mando a la UI. Con la sesión
     * caída el aviso de desconexión tiene que poder cerrarse con el mando, y nada de lo
     * que se apriete tiene a dónde ir.
     */
    const releaseInput = () => {
        inputReleased = true;
        detachInput?.();
        detachInput = null;
        setGamepadClaimedBySession(false);
    };
    const signalLost = (reason: string) => {
        if (stopped) return;
        releaseInput();
        opts.onDisconnected?.(reason);
    };
    const peer = createPeer({
        send: (msg) => {
            if (ws.readyState !== WebSocket.OPEN) return;
            if (msg.type === 'OFFER') opts.onLog('→ OFFER');
            ws.send(JSON.stringify(msg));
        },
        onTrack: (stream) => {
            for (const track of stream.getVideoTracks()) {
                if (!picture.getTracks().some((t) => t.id === track.id)) {
                    picture.addTrack(track);
                }
            }
            for (const track of stream.getAudioTracks()) {
                if (!sound.getTracks().some((t) => t.id === track.id)) {
                    sound.addTrack(track);
                }
            }
            const videoTrack = picture.getVideoTracks()[0];
            const audioTrack = sound.getAudioTracks()[0];
            opts.onLog(
                `ontrack video=${videoTrack?.readyState ?? 'none'} audio=${audioTrack?.readyState ?? 'none'} av_sync=split`,
            );
            if (!avBound && (videoTrack || audioTrack)) {
                avBound = true;
                bindStream(opts.video, opts.audio, picture, sound, opts.onLog);
            } else if (audioTrack && sound.getAudioTracks().length === 1) {
                if (opts.audio.srcObject !== sound) {
                    opts.audio.srcObject = sound;
                }
                void opts.audio.play().catch(() => undefined);
            }
            opts.onTrack(new MediaStream([...picture.getTracks(), ...sound.getTracks()]));
        },
        onState: (s) => {
            opts.onLog(s);
            // `disconnected` a secas es transitorio (el ICE se puede recuperar): lo absorbe el watchdog.
            if (s === 'pc failed' || s === 'ice failed') signalLost(s);
        },
    });

    peer.input.binaryType = 'arraybuffer';
    const mirrorMute = () => {
        opts.audio.muted = opts.video.muted;
        if (!opts.video.muted) void opts.audio.play().catch(() => undefined);
    };
    opts.video.addEventListener('volumechange', mirrorMute);
    let prevStats: { at: number; video: number; audio: number } | null = null;
    const statsTimer = window.setInterval(() => {
        void readRtcStats(peer.pc, prevStats).then(({ snap, next }) => {
            prevStats = next;
            opts.onStats?.(snap);
        });
    }, 1000);

    peer.input.onopen = () => {
        if (stopped || inputReleased) return;
        opts.onLog(
            `dc input open unreliable pads=${needs.gamepad} keyboard=${needs.keyboard} mouse=${needs.mouse}`,
        );
        detachInput = startInput({
            channel: peer.input,
            video: opts.video,
            needs,
            gameSize: opts.gameSize,
            mouseEnabled: opts.mouseEnabled,
            gamepadEnabled: () => !isGamepadMenuOpen(),
            onPads: (ids) => {
                opts.onLog(
                    ids.length
                        ? `gamepad ×${ids.length} ${ids.join(' · ')}`
                        : 'gamepad desconectado',
                );
                opts.onPads?.(ids);
            },
            onKeys: opts.onKeys,
            onMouse: opts.onMouse,
            onLock: opts.onPointerLock,
            onLog: opts.onLog,
        });
        // Con la partida en curso el pad deja de navegar la UI, también si el juego no
        // lo usa: si no, el primer D-pad enfocaría "Salir del juego" y A cerraría la partida.
        setGamepadClaimedBySession(true);
    };

    let signalChain = Promise.resolve();
    ws.onmessage = (ev) => {
        const msg = JSON.parse(String(ev.data)) as SignalIn;
        opts.onLog(`← ${msg.type}${msg.type === 'ERROR' ? ` ${msg.code}` : ''}`);
        signalChain = signalChain.then(async () => {
            if (msg.type === 'ERROR') {
                opts.onError(msg.code);
                // El error ya se informa por `onError` (con su reintentar): el `ws.close()`
                // de abajo dispara `onclose` y no debe abrir además el aviso de inactividad.
                stopped = true;
                releaseInput();
                peer.close();
                ws.close();
                return;
            }
            await peer.onSignal(msg);
        });
    };

    ws.onopen = () => {
        void peer.offer();
    };

    ws.onerror = () => opts.onLog('ws webrtc error');

    // El socket de señalización sin `onclose` dejaba la caída invisible: el offer ya se
    // había enviado y no volvía a llegar nada, así que el video quedaba congelado.
    ws.onclose = (ev) => {
        opts.onLog(`ws webrtc close code=${ev.code}${ev.reason ? ` reason=${ev.reason}` : ''}`);
        signalLost('ws webrtc close');
    };

    const close = () => {
        stopped = true;
        window.clearInterval(statsTimer);
        opts.video.removeEventListener('volumechange', mirrorMute);
        releaseInput();
        opts.onPads?.([]);
        opts.onKeys?.([]);
        opts.onMouse?.(null);
        opts.onPointerLock?.(false);
        peer.close();
        ws.close();
        opts.video.srcObject = null;
        opts.audio.pause();
        opts.audio.srcObject = null;
        opts.onTrack(null);
    };

    /**
     * `close` cierra todo. `releaseInput` solo suelta el input: lo usa el watchdog de
     * useStation, que detecta la sesión muerta (stream mudo) sin que el peer se entere.
     */
    return { close, releaseInput };
}
