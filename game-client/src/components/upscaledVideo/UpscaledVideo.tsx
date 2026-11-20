import { useEffect, useRef, type MouseEventHandler, type RefObject } from 'react';
import { createUpscaler, type UpscaleMode, type UpscaleStatus } from '@/webrtc/upscale.ts';
import './upscaledVideo.css';

type Upscaler = ReturnType<typeof createUpscaler>;

/**
 * El <video> del stream más un <canvas> con FSR encima (ver upscale.ts).
 * El video queda montado y visible debajo: lo necesitan srcObject, el
 * autoplay y el mapeo del mouse. El canvas no recibe eventos.
 */
export const UpscaledVideo = ({
    videoRef,
    mode,
    muted,
    onClick,
    onDoubleClick,
    onStatus,
}: {
    videoRef: RefObject<HTMLVideoElement | null>;
    mode: UpscaleMode;
    muted: boolean;
    onClick?: MouseEventHandler<HTMLVideoElement>;
    onDoubleClick?: MouseEventHandler<HTMLVideoElement>;
    onStatus?: (status: UpscaleStatus) => void;
}) => {
    const canvasRef = useRef<HTMLCanvasElement>(null);
    const upscalerRef = useRef<Upscaler | null>(null);
    const modeRef = useRef(mode);
    const statusRef = useRef(onStatus);

    useEffect(() => {
        statusRef.current = onStatus;
    }, [onStatus]);

    useEffect(() => {
        const video = videoRef.current;
        const canvas = canvasRef.current;
        if (!video || !canvas) return;
        const upscaler = createUpscaler({
            video,
            canvas,
            mode: modeRef.current,
            onStatus: (status) => statusRef.current?.(status),
            onLog: (line) => console.info('[cloudgame]', line),
        });
        upscalerRef.current = upscaler;
        return () => {
            upscaler.stop();
            upscalerRef.current = null;
        };
    }, [videoRef]);

    useEffect(() => {
        modeRef.current = mode;
        upscalerRef.current?.setMode(mode);
    }, [mode]);

    return (
        <>
            <video
                ref={videoRef}
                autoPlay
                playsInline
                muted={muted}
                controls={false}
                width={1280}
                height={720}
                onClick={onClick}
                onDoubleClick={onDoubleClick}
            />
            <canvas ref={canvasRef} className="game-view__upscale" hidden aria-hidden="true" />
        </>
    );
};
