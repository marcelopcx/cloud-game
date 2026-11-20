package api.model;

/** Estado de publicación de un juego (columna GAME.status). */
public enum GameStatus {
    /** Solo lo ve game-client en desarrollo (o con VITE_SHOW_TEST_GAMES). */
    TEST,
    /** Visible y jugable para todos. */
    ACTIVE,
    /** Visible como «Próximamente», sin poder jugarlo. */
    COMING_SOON,
    /** Oculto para los jugadores (ni en el juego aleatorio); solo lo ve game-admin. */
    DISABLED
}
