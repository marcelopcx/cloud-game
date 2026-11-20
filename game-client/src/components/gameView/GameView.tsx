import {
    useCallback,
    useEffect,
    useRef,
    useState,
    type MouseEvent,
    type ReactNode,
    type RefObject,
} from 'react';
import { MenuOptionGame } from '../menuOptionGame/MenuOptionGame.tsx';
import type { MenuOptionGameActionDetail, MenuOptionGameValues } from '../menuOptionGame/MenuOptionGame.tsx';
import { ControlsModal } from '../controlsModal/ControlsModal.tsx';
import { KeyboardModal } from '../keyboardModal/KeyboardModal.tsx';
import type { GameInputs } from '@hooks';
import controllerImg from '../../../assets/icons/controller.svg';
import keyboardImg from '../../../assets/icons/mouse-keyboard.svg';
import { RtcStatsOverlay } from '../rtcStats/RtcStats.tsx';
import { UpscaledVideo } from '../upscaledVideo/UpscaledVideo.tsx';
import { activeFullscreenEl, enterFullscreen, exitFullscreen, lockEscapeKey, unlockEscapeKey } from '@services';
import type { RtcStatsSnapshot } from '@/webrtc/stats.ts';
import type { InputNeeds } from '@/webrtc/input.ts';
import { type UpscaleMode, type UpscaleStatus } from '@/webrtc/upscale.ts';
import './gameView.css';

const HIDE_DELAY_MS = 2400;

/** Descarga y decodifica el dibujo antes de mostrarlo, para que la guía aparezca completa. */
const decodeImage = (src: string) => {
    const img = new Image();
    img.src = src;
    return img.decode().catch(() => undefined);
};

/** Escape no habilita requestFullscreen y el botón de volver (data-keep-windowed) sale sin volver a pantalla completa. */
function wantsFullscreen(event: Event): boolean {
    if (event instanceof KeyboardEvent && event.key === 'Escape') return false;
    return !(event.target instanceof Element && event.target.closest('[data-keep-windowed]'));
}

type FsVideo = HTMLVideoElement & {
    webkitDisplayingFullscreen?: boolean;
};

function inputHint(needs: InputNeeds, padIds: string[], pointerLocked: boolean, muted: boolean): string | null {
    const parts: string[] = [];
    if (muted) parts.push('click en el video para activar el sonido');
    if (needs.gamepad > 0 && padIds.length === 0) {
        parts.push(
            needs.gamepad === 1
                ? 'conectá un gamepad y apretá un botón'
                : `conectá hasta ${needs.gamepad} gamepads`,
        );
    }
    if (needs.keyboard) parts.push('teclado activo');
    if (needs.mouse && !pointerLocked) parts.push('click en el video para la cámara');
    return parts.length ? parts.join(' · ') : null;
}

export const GameView = ({
    videoRef,
    audioRef,
    hasTrack,
    needs,
    padIds,
    controls,
    pointerLocked,
    mouseOn,
    banner,
    actions,
    stats,
    onMouseToggle,
    onControlsChange,
}: {
    videoRef: RefObject<HTMLVideoElement | null>;
    audioRef: RefObject<HTMLAudioElement | null>;
    hasTrack: boolean;
    needs: InputNeeds;
    padIds: string[];
    /** Controles del juego para el modal (useGameInputs, cargados junto con el contenedor). */
    controls: GameInputs;
    pointerLocked: boolean;
    /** Estado del mouse en useStation; el toggle del menú lo refleja. */
    mouseOn: boolean;
    banner?: ReactNode;
    actions?: ReactNode;
    stats?: RtcStatsSnapshot | null;
    /** Toggle del menú: enciende/apaga el mouse que viaja al juego (useStation). */
    onMouseToggle?: (on: boolean) => void;
    onControlsChange?: (visible: boolean) => void;
}) => {
    const containerRef = useRef<HTMLDivElement>(null);
    const hideTimer = useRef<number>(0);
    /** Cursor sobre la barra de acciones o el menú: no se ocultan mientras tanto. */
    const hovering = useRef(false);
    const intentionalExit = useRef(false);
    const [controlsOn, setControlsOn] = useState(true);
    const [isFullscreen, setIsFullscreen] = useState(false);
    const [muted, setMuted] = useState(false);
    const [upscale, setUpscale] = useState<UpscaleStatus | null>(null);
    // Estado del menú de opciones de video; "calidad" mapea al upscale FSR, "mouse" vive en useStation
    // y "controles" abre el modal con los controles del juego.
    const [menuState, setMenuOptions] = useState<Omit<MenuOptionGameValues, 'mouse' | 'controls'>>({
        quality: false,
        metrics: false,
    });
    const [controlsModalOpen, setControlsModalOpen] = useState(false);
    const [keyboardPage, setKeyboardPage] = useState(false);
    const hasBothGuides = needs.gamepad > 0 && needs.keyboard;
    useEffect(() => {
        if (hasBothGuides) void decodeImage(keyboardImg);
    }, [hasBothGuides]);
    const [menuOpen, setMenuOpen] = useState(false);
    // Sin video, con el menú abierto (también con el Home del mando) o el modal de controles: no se oculta nada.
    const pinned = !hasTrack || menuOpen || controlsModalOpen;
    const controlsVisible = controlsOn || pinned;
    const menuOptions: MenuOptionGameValues = { ...menuState, mouse: mouseOn, controls: controlsModalOpen };
    const upscaleMode: UpscaleMode = menuOptions.quality ? 'auto' : 'off';

    const handleMenuAction = (detail: MenuOptionGameActionDetail) => {
        if (detail.action === 'mouse') {
            onMouseToggle?.(detail.enabled);
            return;
        }
        if (detail.action === 'controls') {
            setControlsModalOpen(detail.enabled);
            return;
        }
        setMenuOptions((prev) => ({ ...prev, [detail.action]: detail.enabled }));
    };

    const showControls = useCallback(() => {
        setControlsOn(true);
        window.clearTimeout(hideTimer.current);
        if (pinned || hovering.current) return;
        hideTimer.current = window.setTimeout(() => setControlsOn(false), HIDE_DELAY_MS);
    }, [pinned]);

    const setHovering = useCallback((hover: boolean) => {
        hovering.current = hover;
        showControls();
    }, [showControls]);

    // Al dejar de estar fijado (llega el video, se cierra el menú) arranca la cuenta para ocultar.
    useEffect(() => {
        if (pinned || hovering.current) return;
        hideTimer.current = window.setTimeout(() => setControlsOn(false), HIDE_DELAY_MS);
        return () => window.clearTimeout(hideTimer.current);
    }, [pinned]);

    useEffect(() => {
        onControlsChange?.(controlsVisible);
    }, [controlsVisible, onControlsChange]);

    useEffect(() => {
        const sync = () => {
            const video = videoRef.current as FsVideo | null;
            setIsFullscreen(
                activeFullscreenEl() !== null
                || Boolean(video?.webkitDisplayingFullscreen),
            );
        };
        document.addEventListener('fullscreenchange', sync);
        document.addEventListener('webkitfullscreenchange', sync);
        const video = videoRef.current;
        video?.addEventListener('webkitbeginfullscreen', sync);
        video?.addEventListener('webkitendfullscreen', sync);
        return () => {
            document.removeEventListener('fullscreenchange', sync);
            document.removeEventListener('webkitfullscreenchange', sync);
            video?.removeEventListener('webkitbeginfullscreen', sync);
            video?.removeEventListener('webkitendfullscreen', sync);
        };
    }, [videoRef]);

    // Escape no saca de pantalla completa mientras el player está montado; lo recibe el juego.
    useEffect(() => {
        lockEscapeKey();
        return () => unlockEscapeKey();
    }, []);

    // Firefox y Safari no tienen Keyboard Lock: Escape sale igual y no cuenta como gesto para volver.
    // Si se sale sin usar el toggle, la próxima interacción (click o tecla) vuelve a pantalla completa,
    // salvo sobre un elemento con data-keep-windowed (el botón de volver del player).
    useEffect(() => {
        if (!hasTrack) return;
        const events = ['pointerdown', 'keydown'] as const;
        const disarm = () => events.forEach((e) => window.removeEventListener(e, reenter, true));
        const reenter = (event: Event) => {
            if (!wantsFullscreen(event)) return;
            disarm();
            if (!activeFullscreenEl()) void enterFullscreen(document.documentElement, videoRef.current);
        };
        const onChange = () => {
            const intentional = intentionalExit.current;
            intentionalExit.current = false;
            if (activeFullscreenEl()) {
                disarm();
                return;
            }
            if (!intentional) events.forEach((e) => window.addEventListener(e, reenter, true));
        };
        document.addEventListener('fullscreenchange', onChange);
        document.addEventListener('webkitfullscreenchange', onChange);
        return () => {
            disarm();
            document.removeEventListener('fullscreenchange', onChange);
            document.removeEventListener('webkitfullscreenchange', onChange);
        };
    }, [hasTrack, videoRef]);

    useEffect(() => {
        const video = videoRef.current;
        const audio = audioRef.current;
        if (!hasTrack || !video) return;
        video.muted = false;
        if (audio) audio.muted = false;
        setMuted(false);
        // El sonido necesita un gesto previo del usuario (click o tecla); un botón del
        // mando no cuenta. Si el navegador lo bloquea, el video arranca mudo en vez de
        // quedar en negro, y el click en el video (unmute) activa el sonido.
        const fallBackToMuted = () => {
            video.muted = true;
            if (audio) audio.muted = true;
            setMuted(true);
        };
        void video.play().catch(() => {
            fallBackToMuted();
            void video.play().catch(() => undefined);
        });
        if (audio) void audio.play().catch(fallBackToMuted);
    }, [hasTrack, videoRef, audioRef]);

    // Pantalla completa sobre el documento (no el contenedor) para que la barra del player siga visible.
    useEffect(() => {
        if (!hasTrack || activeFullscreenEl()) return;
        let cancelled = false;
        const retryEvents = ['pointerdown', 'keydown'] as const;
        const cleanup = () => retryEvents.forEach((e) => window.removeEventListener(e, retry, true));
        const retry = (event: Event) => {
            if (!wantsFullscreen(event)) return;
            cleanup();
            if (!cancelled && !activeFullscreenEl()) void enterFullscreen(document.documentElement, videoRef.current);
        };
        const attempt = async () => {
            try {
                await document.documentElement.requestFullscreen();
            } catch {
                if (!cancelled) retryEvents.forEach((e) => window.addEventListener(e, retry, true));
            }
        };
        void attempt();
        return () => {
            cancelled = true;
            cleanup();
        };
    }, [hasTrack, videoRef]);

    const unmute = () => {
        const video = videoRef.current;
        const audio = audioRef.current;
        if (!video) return;
        video.muted = false;
        if (audio) audio.muted = false;
        setMuted(false);
        void video.play().catch(() => {
            video.muted = true;
            if (audio) audio.muted = true;
            setMuted(true);
        });
        if (audio) {
            void audio.play().catch(() => {
                audio.muted = true;
                setMuted(true);
            });
        }
    };

    const toggleFullscreen = (event: MouseEvent) => {
        event.stopPropagation();
        if (isFullscreen) {
            intentionalExit.current = true;
            void exitFullscreen(videoRef.current);
            return;
        }
        void enterFullscreen(document.documentElement, videoRef.current);
    };

    const hint = hasTrack ? inputHint(needs, padIds, pointerLocked, muted) : null;
    const classes = [
        'game-view',
        controlsVisible ? 'is-controls' : 'is-idle',
        hasTrack ? 'has-track' : '',
        needs.mouse ? 'has-mouse' : '',
        pointerLocked ? 'is-locked' : '',
    ].filter(Boolean).join(' ');

    return (
        <div
            ref={containerRef}
            className={classes}
            tabIndex={0}
            onMouseMove={() => showControls()}
            onPointerDown={() => showControls()}
            onMouseLeave={() => {
                if (!pinned) setControlsOn(false);
            }}
            onDoubleClick={(event) => {
                if (!needs.mouse) return;
                event.preventDefault();
                event.stopPropagation();
            }}
        >
            <UpscaledVideo
                videoRef={videoRef}
                mode={upscaleMode}
                muted={muted}
                onClick={unmute}
                onDoubleClick={needs.mouse ? undefined : toggleFullscreen}
                onStatus={setUpscale}
            />
            <audio ref={audioRef} autoPlay />
            {!hasTrack && (
                <div className="game-view__placeholder">pantalla de juego</div>
            )}
            {hasTrack && menuOptions.metrics && (
                <RtcStatsOverlay stats={stats ?? null} upscale={upscale?.label ?? null} />
            )}
            {hint && <p className="game-view__hint">{hint}</p>}

            {banner && <div className="game-view__banner">{banner}</div>}

            <div
                className="game-view__bar"
                onClick={(e) => e.stopPropagation()}
                onMouseEnter={() => setHovering(true)}
                onMouseLeave={() => setHovering(false)}
            >
                <div className="game-view__actions">{actions}</div>
            </div>

            <MenuOptionGame
                values={menuOptions}
                onAction={handleMenuAction}
                hidden={!controlsVisible}
                onOpenChange={setMenuOpen}
                onHoverChange={setHovering}
            />
            {/* Con mando y teclado a la vez, las flechas del modal alternan entre ambas guías. */}
            {(() => {
                const both = needs.gamepad > 0 && needs.keyboard;
                const onKeyboard = needs.gamepad === 0 || (both && keyboardPage);
                const close = () => setControlsModalOpen(false);
                const onSwitch = both
                    ? () => void decodeImage(keyboardPage ? controllerImg : keyboardImg).then(() => setKeyboardPage((p) => !p))
                    : undefined;
                return onKeyboard
                    ? <KeyboardModal open={controlsModalOpen} controls={controls} onClose={close} onSwitch={onSwitch} />
                    : <ControlsModal open={controlsModalOpen} controls={controls} onClose={close} onSwitch={onSwitch} />;
            })()}
        </div>
    );
};
