import type { CompactGameItem, GameDetails, GamePlatform } from '@components';
import type { CatalogGameResponse, CatalogGameStatus, GameInputResponse, HomeResponse, HomeSectionType } from '@/types';

// game-crud de solo lectura: nginx (y el proxy de Vite en desarrollo) reenvían /catalog → :8096.
export const CATALOG = import.meta.env.VITE_CATALOG_URL || '/catalog';

/** Juegos de prueba (TEST): en `npm run dev`, o en un build con VITE_SHOW_TEST_GAMES=true. */
const SHOW_TEST_GAMES = import.meta.env.VITE_SHOW_TEST_GAMES
    ? import.meta.env.VITE_SHOW_TEST_GAMES === 'true'
    : import.meta.env.DEV;

/** Lo que ve el jugador: DISABLED nunca, TEST solo en desarrollo. */
const VISIBLE_STATUSES: CatalogGameStatus[] = SHOW_TEST_GAMES ? ['ACTIVE', 'COMING_SOON', 'TEST'] : ['ACTIVE', 'COMING_SOON'];
const STATUS_PARAM = VISIBLE_STATUSES.join(',');
const isVisible = (game: CatalogGameResponse) => VISIBLE_STATUSES.includes(game.status ?? 'ACTIVE');

/**
 * Juego del catálogo: tarjeta del grid (`image`, cover 920x430), tarjeta vertical del
 * carrusel (`portrait`, poster 600x900), fondo del banner principal (`heroImage`, banner1),
 * del sub banner (`bannerImage`, banner2) y detalle del modal (portada, `background`).
 */
export type CatalogGame = CompactGameItem & {
    gameId: string;
    portrait: string;
    heroImage: string;
    bannerImage: string;
    details: GameDetails;
};

const PLATFORMS: Record<string, GamePlatform> = {
    'Mando': 'gamepad',
    'Teclado y ratón': 'mouse',
    'Táctil': 'mobile',
};

export class CatalogRequestError extends Error {
    constructor(message: string) {
        super(message);
        this.name = 'CatalogRequestError';
    }
}

function toCatalogGame(game: CatalogGameResponse): CatalogGame {
    const gameId = game.path.split('/').filter(Boolean).pop() ?? game.path;
    const images = game.images;
    const poster = images?.poster ?? '';
    const cover = images?.cover ?? poster;
    // Las imágenes anchas se reemplazan entre sí antes de caer en la cover.
    const wide = images?.background ?? images?.banner1 ?? images?.banner2 ?? cover;
    const platforms = game.gamepads.map((name) => PLATFORMS[name]).filter(Boolean);
    const comingSoon = game.status === 'COMING_SOON';
    return {
        id: game.id,
        gameId,
        title: game.name,
        image: cover,
        portrait: poster,
        heroImage: images?.banner1 ?? wide,
        bannerImage: images?.banner2 ?? images?.banner1 ?? wide,
        rating: game.rating ?? 0,
        gender: game.categories,
        platforms,
        comingSoon,
        details: {
            gameId,
            title: game.name,
            backgroundImage: wide,
            icon: images?.icon ?? undefined,
            genres: game.categories,
            platforms,
            description: game.description ?? undefined,
            comingSoon,
        },
    };
}

/** Sección del home ya convertida: los juegos listos para tarjetas, banners y modal. */
export type HomeSection = { id: number; type: HomeSectionType; title: string | null; games: CatalogGame[] };

export async function getJson<T>(path: string, signal?: AbortSignal, init?: RequestInit): Promise<T> {
    const res = await fetch(`${CATALOG}${path}`, { ...init, signal });
    if (!res.ok) {
        let code = `HTTP ${res.status}`;
        try {
            const body = (await res.json()) as { error?: { code?: string } };
            code = body.error?.code ?? code;
        } catch {
            // Sin cuerpo JSON: respondió el proxy, no game-crud.
        }
        throw new CatalogRequestError(code);
    }
    if (res.status === 204) return undefined as T;
    return res.json() as Promise<T>;
}

export async function listCatalogGames(signal?: AbortSignal): Promise<CatalogGame[]> {
    const games = await getJson<CatalogGameResponse[]>(`/games?status=${STATUS_PARAM}`, signal);
    return games.map(toCatalogGame);
}

/**
 * Controles del juego (acción → tecla/botón) para el modal del reproductor. El player solo conoce
 * el id del manifest, así que primero busca el juego del catálogo por `path`. Sin juego: lista vacía.
 */
export async function getGameInputs(gameId: string, signal?: AbortSignal): Promise<GameInputResponse[]> {
    const games = await getJson<CatalogGameResponse[]>('/games', signal);
    const game = games.find((g) => g.path.split('/').filter(Boolean).pop() === gameId);
    if (!game) return [];
    return getJson<GameInputResponse[]>(`/games/${game.id}/inputs`, signal);
}

/** Distribución del home armada en game-admin. Vacía = sin configurar. Sin los juegos que el jugador no ve. */
export async function getHomeLayout(signal?: AbortSignal): Promise<HomeSection[]> {
    const home = await getJson<HomeResponse>('/home', signal);
    return home.sections.map((section) => ({ ...section, games: section.games.filter(isVisible).map(toCatalogGame) }));
}

/** Buscador del header: juegos cuyo nombre contiene `query` (game-crud ignora mayúsculas y tildes). */
export async function searchCatalogGames(query: string, signal?: AbortSignal, limit = 8): Promise<CatalogGame[]> {
    const params = new URLSearchParams({ q: query, status: STATUS_PARAM, limit: String(limit) });
    const games = await getJson<CatalogGameResponse[]>(`/games/search?${params}`, signal);
    return games.map(toCatalogGame);
}
