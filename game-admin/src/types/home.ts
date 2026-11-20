import type { Game } from './game.ts';

/**
 * HERO = banner principal, BANNER = sub banner, CAROUSEL = carrusel, GRID = grid con filtro por género,
 * UPCOMING = carrusel de próximos lanzamientos (se llena solo con los juegos COMING_SOON).
 */
export type HomeSectionType = 'HERO' | 'BANNER' | 'CAROUSEL' | 'GRID' | 'UPCOMING';

/** GET /home: secciones en orden, con los juegos completos. */
export interface HomeResponse {
    sections: {
        id: number;
        type: HomeSectionType;
        title: string | null;
        games: Game[];
    }[];
}

/** PUT /home: reemplaza toda la distribución; el orden de la lista es el del home. */
export interface UpdateHomeRequest {
    sections: {
        type: HomeSectionType;
        title?: string;
        gameIds: number[];
    }[];
}
