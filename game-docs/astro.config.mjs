// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import remarkMermaid from './src/plugins/remark-mermaid.mjs';

// Se publica en https://<host>/docs/ detrás del nginx de game-client (basic auth del admin).
export default defineConfig({
  base: '/docs',
  trailingSlash: 'always',
  markdown: { remarkPlugins: [remarkMermaid] },
  integrations: [
    starlight({
      title: 'Cloud Game',
      description: 'Documentación técnica de Cloud Game: estación, loader, cliente, catálogo y juegos.',
      defaultLocale: 'root',
      locales: { root: { label: 'Español', lang: 'es' } },
      logo: {
        dark: './src/assets/logo-dark.svg',
        light: './src/assets/logo-light.svg',
        replacesTitle: true,
      },
      favicon: '/favicon.svg',
      customCss: ['@fontsource-variable/inter', './src/styles/theme.css'],
      components: {
        Head: './src/components/Head.astro',
        ThemeProvider: './src/components/ThemeProvider.astro',
      },
      social: [
        { icon: 'github', label: 'GitHub', href: 'https://github.com/marcelopcx/cloud-game' },
      ],
      lastUpdated: false,
      pagination: true,
      tableOfContents: { minHeadingLevel: 2, maxHeadingLevel: 3 },
      expressiveCode: {
        themes: ['github-dark-dimmed', 'github-light'],
        styleOverrides: { borderRadius: '10px', codeFontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace' },
      },
      sidebar: [
        {
          label: 'Empezar',
          items: [
            { label: 'Introducción', slug: 'introduccion' },
            { label: 'Arquitectura', slug: 'arquitectura' },
            { label: 'Despliegue', slug: 'despliegue' },
            { label: 'nginx', slug: 'nginx' },
          ],
        },
        {
          label: 'Game Station',
          items: [
            { label: 'Visión general', slug: 'game-station' },
            { label: 'API REST y WebSocket', slug: 'game-station/api' },
            { label: 'Video, audio y red', slug: 'game-station/media' },
            { label: 'Input remoto', slug: 'game-station/input' },
          ],
        },
        { label: 'Game Dispatcher', items: [{ label: 'Guía del dispatcher', slug: 'game-dispatcher' }] },
        { label: 'Game Loader', items: [{ label: 'Guía del loader', slug: 'game-loader' }] },
        { label: 'Game Client', items: [{ label: 'Visión general', slug: 'game-client' }] },
        {
          label: 'Catálogo',
          items: [
            { label: 'Game Crud (API)', slug: 'game-crud' },
            { label: 'Game Admin (panel)', slug: 'game-admin' },
          ],
        },
        {
          label: 'Juegos',
          items: [
            { label: 'Catálogo de juegos', slug: 'juegos' },
            { label: 'Manifiesto y nuevos títulos', slug: 'juegos/manifiesto' },
            { label: 'Requisitos de un juego', slug: 'juegos/requisitos' },
          ],
        },
        {
          label: 'Herramientas',
          items: [
            { label: 'Documentación de APIs', slug: 'game-api' },
            {
              label: 'Referencia interactiva',
              link: '/api-docs/',
              attrs: { target: '_blank', rel: 'noopener' },
              badge: { text: 'Scalar', variant: 'note' },
            },
            { label: 'Este sitio (game-docs)', slug: 'game-docs' },
          ],
        },
      ],
    }),
  ],
});
