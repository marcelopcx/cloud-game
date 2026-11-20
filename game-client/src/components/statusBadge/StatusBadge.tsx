import type { StatusGameStation } from '@/types';
import { StatusGameStation as Station } from '@/types';
import './statusBadge.css';

const LABELS: Record<StatusGameStation, string> = {
    [Station.IDLE]: 'Libre',
    [Station.PREPARING]: 'Preparando',
    [Station.READY]: 'Listo',
    [Station.PLAYING]: 'En partida',
    [Station.FAILED]: 'Error',
};

/** Modificador de color de cada estado (ver statusBadge.css). */
const THEMES: Record<StatusGameStation, string> = {
    [Station.IDLE]: 'idle',
    [Station.PREPARING]: 'preparing',
    [Station.READY]: 'ready',
    [Station.PLAYING]: 'playing',
    [Station.FAILED]: 'failed',
};

export const StatusBadge = ({
    status,
    connected,
    progress,
}: {
    status: StatusGameStation;
    connected: boolean;
    progress?: number;
}) => {
    const theme = connected ? THEMES[status] ?? 'offline' : 'offline';
    const label = !connected
        ? 'Offline'
        : status === Station.PREPARING && typeof progress === 'number'
            ? `Preparando ${progress}%`
            : LABELS[status];

    return (
        <span className={`status-badge status-badge--${theme}`}>
            <span className="status-badge__dot" />
            {label}
        </span>
    );
};
