import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

const src = fileURLToPath(new URL('./src', import.meta.url));

export default defineConfig(({ mode }) => {
  const env = { ...loadEnv(mode, process.cwd(), ''), ...process.env };
  // Estación detrás del nginx remoto: https://<ip>/station (cert autofirmado → secure: false).
  const stationProxy = {
    '/station': {
      target: env.STATION_PROXY_TARGET || 'http://127.0.0.1:8090',
      changeOrigin: true,
      secure: false,
      ws: true,
      rewrite: (p: string) => p.replace(/^\/station/, ''),
    },
    // Game Dispatcher (VITE_DISPATCHER_URL=/dispatcher): API pública, sin token. En desarrollo el
    // dispatcher corre con DISPATCHER_STATIONS_URL= (vacío) y devuelve http://127.0.0.1:<puerto> directo.
    '/dispatcher': {
      target: env.DISPATCHER_PROXY_TARGET || 'http://127.0.0.1:8094',
      changeOrigin: true,
      rewrite: (p: string) => p.replace(/^\/dispatcher/, ''),
    },
    // Catálogo (game-crud): lo que ve el home.
    '/catalog': {
      target: env.CRUD_PROXY_TARGET || 'http://127.0.0.1:8096',
      changeOrigin: true,
      rewrite: (p: string) => p.replace(/^\/catalog/, ''),
    },
    // Imágenes del catálogo (las rutas de IMAGE son /media/...). Contra el nginx del servidor
    // (CRUD_PROXY_TARGET=https://<host>/catalog) van a https://<host>/media, no a /catalog/media
    // (que responde el index.html de la SPA); MEDIA_PROXY_TARGET lo fija a mano.
    '/media': {
      target: env.MEDIA_PROXY_TARGET || (env.CRUD_PROXY_TARGET || 'http://127.0.0.1:8096').replace(/\/catalog\/?$/, ''),
      changeOrigin: true,
      secure: false,
    },
    // Estaciones de un dispatcher remoto (DISPATCHER_STATIONS_URL=/ports): solo si se define el
    // destino, p. ej. PORTS_PROXY_TARGET=https://<ip-del-servidor> para desarrollar contra el servidor.
    ...(env.PORTS_PROXY_TARGET && {
      '/ports': {
        target: env.PORTS_PROXY_TARGET,
        changeOrigin: true,
        ws: true,
      },
    }),
  };

  return {
    plugins: [react()],
    resolve: {
      alias: {
        '@': src,
        '@components': fileURLToPath(new URL('./src/components', import.meta.url)),
        '@hooks': fileURLToPath(new URL('./src/hooks', import.meta.url)),
        '@services': fileURLToPath(new URL('./src/services', import.meta.url)),
        '@screens': fileURLToPath(new URL('./src/screens', import.meta.url)),
      },
    },
    server: {
      host: true,
      port: 5173,
      strictPort: true,
      proxy: stationProxy,
    },
    preview: {
      host: true,
      port: 5173,
      strictPort: true,
      proxy: stationProxy,
    },
  };
});
