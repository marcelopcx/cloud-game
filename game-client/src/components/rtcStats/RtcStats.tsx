import type { RtcStatsSnapshot } from '@/webrtc/stats.ts';
import './rtcStats.css';

function fmt(value: number | null, suffix: string) {
    if (value === null || Number.isNaN(value)) return '—';
    return `${value}${suffix}`;
}

export const RtcStatsOverlay = ({
    stats,
    upscale,
}: {
    stats: RtcStatsSnapshot | null;
    /** Estado del upscaler FSR (upscale.ts). */
    upscale?: string | null;
}) => {
    if (!stats) return null;
    return (
        <div className="rtc-stats" aria-label="WebRTC stats">
            <span>{stats.videoCodec ?? '—'}</span>
            <span>V {fmt(stats.videoKbps, ' kb/s')}</span>
            <span>A {fmt(stats.audioKbps, ' kb/s')}</span>
            <span>{fmt(stats.fps, ' fps')}</span>
            <span>RTT {fmt(stats.rttMs, ' ms')}</span>
            <span>Vj {fmt(stats.videoJitterMs, ' ms')}</span>
            <span>Aj {fmt(stats.audioJitterMs, ' ms')}</span>
            <span>lost {fmt(stats.lost, '')}</span>
            {upscale && <span>{upscale}</span>}
        </div>
    );
};
