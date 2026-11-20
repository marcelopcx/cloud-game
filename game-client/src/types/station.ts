import type { StatusGameStation } from './statusGameStation.ts';
import type { InputNeeds } from '@/webrtc/input.ts';

export type StationNeeds = Partial<InputNeeds> & {
    display?: boolean;
    audio?: boolean;
    gamepad?: InputNeeds['gamepad'] | boolean;
};

export type Health = {
    status: string;
    stationId: string;
    state: StatusGameStation;
    gameId: string | null;
    sessionId: string | null;
    encoder?: string | null;
    cache: unknown[];
    needs?: StationNeeds;
    /** Resolución del juego (STATION_SIZE); el video puede venir más chico. */
    gameWidth?: number;
    gameHeight?: number;
};

export type ControlEvent = {
    type: 'STATE' | 'ERROR';
    state?: StatusGameStation;
    progress?: number;
    message?: string;
    cacheHit?: boolean;
    code?: string;
    gameWidth?: number;
    gameHeight?: number;
};

export type StationErrorBody = { error: { code: string; message: string } };

export type LaunchResponse = {
    state: string;
    wsUrl: string;
    needs?: StationNeeds;
};

export type PrepareResponse = { sessionId: string; state: string };
