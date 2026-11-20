/** Amigo en partida (`GET /friends/playing`). `game.id` es el id del catálogo (GAME.id). */
export interface FriendPlaying {
    userId: number;
    username: string;
    /** Ruta pública del avatar; null = la inicial. */
    avatar: string | null;
    game: { id: number; name: string; icon: string | null };
    /** ISO 8601: desde cuándo está en partida. */
    since: string;
}

/** Amigo de la lista completa (`GET /friends`): `game` y `since` en null si no está en partida. */
export interface FriendEntry extends Omit<FriendPlaying, 'game' | 'since'> {
    game: FriendPlaying['game'] | null;
    since: string | null;
}

/** Solicitud pendiente recibida (`GET /friends/requests`). */
export interface FriendRequest {
    id: number;
    userId: number;
    username: string;
    avatar: string | null;
}

/** NONE: se puede enviar; SENT: ya enviada; RECEIVED: él la envió (`requestId`); FRIEND: ya son amigos. */
export type FriendRelation = 'NONE' | 'SENT' | 'RECEIVED' | 'FRIEND';

/** Jugador del buscador de amigos (`GET /friends/search`). */
export interface FriendSearchResult {
    userId: number;
    username: string;
    avatar: string | null;
    relation: FriendRelation;
    requestId: number | null;
}
