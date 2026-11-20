/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_STATION_URL?: string;
  readonly VITE_STATIONS_URL?: string;
  /** Game Dispatcher (p. ej. `/dispatcher`): asigna una estación por partida; la base de la estación viene en la sesión. */
  readonly VITE_DISPATCHER_URL?: string;
  readonly VITE_ICE_SERVERS?: string;
  readonly VITE_GAME_ID?: string;
  /** game-crud de solo lectura (default `/catalog`, proxy de nginx/Vite). */
  readonly VITE_CATALOG_URL?: string;
  /** `true` muestra los juegos en estado TEST; sin definir, solo en `npm run dev`. */
  readonly VITE_SHOW_TEST_GAMES?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
