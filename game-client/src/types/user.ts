/** Usuario de game-crud (`POST /auth/login`, `GET /auth/me`). */
export interface UserResponse {
    id: number;
    email: string;
    username: string;
    /** null si no tiene avatar (se muestra la inicial). */
    avatarId: number | null;
    /** Ruta pública de la imagen (`/media/avatars/…`). */
    avatar: string | null;
}

/** Avatar que el jugador puede elegir (`GET /catalog/avatars`): no se suben imágenes propias. */
export interface Avatar {
    id: number;
    name: string;
    url: string;
    active: boolean;
}
