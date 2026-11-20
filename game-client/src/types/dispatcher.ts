export type SessionStatus = 'QUEUED' | 'PROVISIONING' | 'ASSIGNED' | 'PLAYING' | 'ENDED' | 'FAILED';

/** Estación asignada: base REST/WS (p. ej. `/ports/8201`) y sessionId con el que ya está en READY. */
export type StationRef = {
    stationBase: string;
    stationSessionId: string;
    httpPort: number;
};

/** Sesión de juego del Game Dispatcher (`POST/GET /sessions`). */
export type PlaySession = {
    sessionId: string;
    gameId: string;
    status: SessionStatus;
    /** 1 = siguiente; solo en QUEUED. */
    queuePosition: number | null;
    /** Solo en ASSIGNED y PLAYING. */
    station: StationRef | null;
    /** Solo en ENDED / FAILED: RELEASED, REPLACED, INACTIVITY, DISCONNECTED, CLAIM_TIMEOUT, STATION_LOST, PROVISION_FAILED. */
    endReason: string | null;
    createdAt: string;
    /** Solo con sesión de usuario y estación asignada: se manda en POST /launch. */
    saveGrant?: string | null;
};
