/** Tipos de imagen: columnas de IMAGE y segmento de PUT/DELETE /games/{id}/images/{tipo}. */
export type ImageKind = 'poster' | 'cover' | 'banner1' | 'banner2' | 'icon' | 'background';

/**
 * Rutas de la tabla IMAGE (/media/games/<id>/<tipo>-<hash>.<ext>, archivos en game-crud).
 * null = sin esa imagen. El póster es obligatorio en el formulario.
 */
export type GameImages = Record<ImageKind, string | null>;

/** Imágenes pendientes del formulario: archivo nuevo, o null para quitar la guardada. */
export type ImageChanges = Partial<Record<ImageKind, File | null>>;

/**
 * GAME.status. TEST: solo game-client en desarrollo; ACTIVE: jugable; COMING_SOON: visible como
 * «Próximamente», sin jugar; DISABLED: oculto para los jugadores.
 */
export type GameStatus = 'TEST' | 'ACTIVE' | 'COMING_SOON' | 'DISABLED';

/** Cuerpo de POST /games: una fila de GAME y sus relaciones (las imágenes se suben aparte). */
export interface CreateGameRequest {
    name: string;            // name (máx. 100)
    description?: string;    // descr (máx. 500)
    rating?: number;         // rating (0 a 5)
    path: string;            // path (máx. 255)
    minContainers: number;   // min_cont (por defecto 1)
    maxContainers?: number;  // max_cont
    status: GameStatus;      // status (por defecto ACTIVE)
    /** Nombres de CATEGORY; el backend busca o crea cada una. */
    categories: string[];
    /** Nombres de GAMEPAD; el backend busca o crea cada uno. */
    gamepads: string[];
}

/** GameResponse de game-crud: los opcionales vienen como null, e images es null si el juego no tiene imagen. */
export interface Game {
    id: number;
    name: string;
    description: string | null;
    rating: number | null;
    path: string;
    minContainers: number;
    maxContainers: number | null;
    status: GameStatus;
    images: GameImages | null;
    categories: string[];
    gamepads: string[];
    createdAt: string;
}

/**
 * Cuerpo de PATCH /games/{id}: solo los campos que cambian.
 * description "" la borra; rating y maxContainers null los borran.
 * categories y gamepads se reemplazan enteros.
 */
export type UpdateGameRequest = Partial<Omit<CreateGameRequest, 'rating' | 'maxContainers'>> & {
    rating?: number | null;
    maxContainers?: number | null;
};
