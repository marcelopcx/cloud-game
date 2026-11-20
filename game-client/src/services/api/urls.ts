const BASE = import.meta.env.VITE_STATION_URL || 'http://localhost:8090';
// Una estación por juego (compose multi): nginx enruta `${STATIONS}/<gameId>/…` a su contenedor.
const STATIONS = import.meta.env.VITE_STATIONS_URL;

// Estación creada por el Game Loader para una partida: pisa a las dos anteriores.
const dynamicBases = new Map<string, string>();

export function setStationBase(gameId: string, base: string | null) {
    if (base) dynamicBases.set(gameId, base);
    else dynamicBases.delete(gameId);
}

function stationBase(gameId?: string) {
    const dynamic = gameId ? dynamicBases.get(gameId) : undefined;
    if (dynamic) return dynamic;
    return STATIONS && gameId ? `${STATIONS}/${gameId}` : BASE;
}

export function restUrl(path: string, gameId?: string) {
    return `${stationBase(gameId)}${path}`;
}

export function wsUrl(path: string, gameId?: string) {
    const base = stationBase(gameId);
    if (base.startsWith('http')) {
        return base.replace(/^http/, 'ws') + path;
    }
    const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${proto}//${location.host}${base}${path}`;
}
