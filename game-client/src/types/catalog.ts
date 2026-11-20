/**
 * Estado de publicación (GAME.status): TEST solo se ve en desarrollo, COMING_SOON se muestra como
 * «Próximamente» sin poder jugarlo y DISABLED no llega al cliente.
 */
export type CatalogGameStatus = 'TEST' | 'ACTIVE' | 'COMING_SOON' | 'DISABLED';

/** Juego tal como lo devuelve game-crud (`GET /games`). */
export type CatalogGameResponse = {
    id: number;
    name: string;
    description: string | null;
    rating: number | null;
    /** `/games/<gameId>`: el último segmento es el id del manifest.yaml (el que va en /prepare). */
    path: string;
    minContainers: number;
    maxContainers: number | null;
    status: CatalogGameStatus;
    images: {
        poster: string;
        cover: string | null;
        banner1: string | null;
        banner2: string | null;
        icon: string | null;
        /** Portada: fondo de la ficha del juego. */
        background: string | null;
    } | null;
    categories: string[];
    /** Nombres de GAMEPAD: "Mando", "Teclado y ratón", "Táctil". */
    gamepads: string[];
    createdAt: string;
};

/** Control de un juego (`GET /games/{id}/inputs`): null = el juego no tiene ese control para la acción. */
export type GameInputResponse = {
    action: string;
    keyboard: string | null;
    /** Botón del mando, nomenclatura Xbox. */
    controller: string | null;
};

/** Tipo de sección del home: banner principal, sub banner, carrusel, grid o carrusel de próximos lanzamientos. */
export type HomeSectionType = 'HERO' | 'BANNER' | 'CAROUSEL' | 'GRID' | 'UPCOMING';

/** Distribución del home (`GET /home` de game-crud), en el orden en que se muestra. */
export type HomeResponse = {
    sections: {
        id: number;
        type: HomeSectionType;
        /** Título del carrusel o del grid; null en los banners. */
        title: string | null;
        /** En orden. Vacía en un GRID = todo el catálogo. En UPCOMING, los juegos COMING_SOON (los pone game-crud). */
        games: CatalogGameResponse[];
    }[];
};
