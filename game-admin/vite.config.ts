import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

const dir = (p: string) => fileURLToPath(new URL(p, import.meta.url));

export default defineConfig(({ mode }) => {
  const env = { ...loadEnv(mode, process.cwd(), ''), ...process.env };
  // game-crud detrás de /api (mismo origen: sin CORS).
  const crudProxy = {
    '/api': {
      target: env.CRUD_PROXY_TARGET || 'http://127.0.0.1:8096',
      changeOrigin: true,
      rewrite: (p: string) => p.replace(/^\/api/, ''),
    },
    // Imágenes del catálogo (las rutas de IMAGE son /media/...).
    '/media': {
      target: env.CRUD_PROXY_TARGET || 'http://127.0.0.1:8096',
      changeOrigin: true,
    },
  };

  return {
    plugins: [react()],
    resolve: {
      alias: {
        '@': dir('./src'),
        '@components': dir('./src/components'),
        '@hooks': dir('./src/hooks'),
        '@services': dir('./src/services'),
        '@screens': dir('./src/screens'),
      },
    },
    server: {
      host: true,
      port: 5174, // 5173 lo usa game-client
      strictPort: true,
      proxy: crudProxy,
    },
    preview: {
      host: true,
      port: 5174,
      strictPort: true,
      proxy: crudProxy,
    },
  };
});
