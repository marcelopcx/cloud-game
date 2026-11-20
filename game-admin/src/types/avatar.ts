/** Fila de AVATAR (GET /avatars?all=true): lo que eligen los jugadores en game-client. */
export interface Avatar {
    id: number;
    name: string;
    /** Ruta pública de la imagen (/media/avatars/…). */
    url: string;
    /** false = no se ofrece a los jugadores; quien ya lo tiene lo conserva. */
    active: boolean;
}

/** Cuerpo de PATCH /avatars/{id}: solo los campos enviados. */
export interface UpdateAvatarRequest {
    name?: string;
    active?: boolean;
}
