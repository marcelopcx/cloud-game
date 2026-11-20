# Game Docs

Sitio de documentación del proyecto, hecho con [Astro Starlight](https://starlight.astro.build/).
Se publica en `https://<PUBLIC_HOST>/docs/` con el mismo login que el panel admin.

| | |
| --- | --- |
| Código | [`game-docs/`](../../game-docs) |
| Stack | Astro 7 + Starlight 0.42, Mermaid 11, buscador Pagefind (sin conexión) |
| Servido por | nginx 1.27 (imagen Docker), en `127.0.0.1:8099` |
| Fuente del contenido | `docs/` y los README de los módulos (no se duplican) |

## De dónde sale el contenido

La fuente sigue siendo `docs/` (y los README que se leen en GitHub). Antes de `dev` y `build`,
[`scripts/sync-docs.mjs`](../../game-docs/scripts/sync-docs.mjs) copia cada archivo a
`src/content/docs/` y:

- pasa el primer `# Título` al frontmatter;
- reescribe los links relativos: a otro documento publicado → su página del sitio (con el ancla);
  a un archivo de `docs/` que no es Markdown (p. ej. el `.drawio`) → descarga desde `/docs/files/`;
  a código, compose o scripts → GitHub;
- agrega el link *Editar página* al archivo original en GitHub.

Lo generado está en `.gitignore`. Solo `src/content/docs/index.mdx` (la portada) se escribe a mano.

| Archivo del repo | Página |
| --- | --- |
| `README.md` (desde el primer `---`) | `/docs/introduccion/` |
| `docs/arquitectura.md`, `docs/despliegue.md` | `/docs/arquitectura/`, `/docs/despliegue/` |
| `docs/game-station/*.md` | `/docs/game-station/…` |
| `docs/game-loader/`, `game-client/`, `game-crud/`, `game-admin/`, `game-docs/` | `/docs/<módulo>/` |
| `games/README.md`, `docs/games/README.md` | `/docs/juegos/`, `/docs/juegos/manifiesto/` |
| `game-api/README.md` | `/docs/game-api/` |

**Agregar un documento:** crearlo en `docs/`, sumarlo a `PAGES` en `sync-docs.mjs` y al `sidebar`
de `astro.config.mjs`.

Los diagramas están hechos en [draw.io](https://www.drawio.com/) y guardados como `.drawio.svg` en
`docs/diagrams/`: se abren y editan directamente en draw.io, GitHub los muestra como imagen y
`scripts/sync-docs.mjs` los copia al sitio. El soporte de bloques ` ```mermaid ` sigue disponible
(`src/plugins/remark-mermaid.mjs`) por si se agrega alguno.

## Personalización

| Archivo | Qué define |
| --- | --- |
| `astro.config.mjs` | Título, idioma (español), logo claro/oscuro, menú, temas de código, componentes propios. |
| `src/styles/theme.css` | Colores de game-client (negro `#111`, turquesa `#22c2fc`) en las variables de Starlight, menú, tablas, portada. |
| `src/components/ThemeProvider.astro` | La primera visita arranca en modo oscuro; después respeta el selector. |
| `src/assets/` | Logo (`logo-dark.svg`, `logo-light.svg`) y el mando de la portada. |
| `public/favicon.svg` | El mando de Cloud Game (igual que game-client, game-admin y game-api). |

## Desarrollo local

```bash
cd game-docs
npm ci
npm run dev        # http://localhost:4321/docs/ (recarga al editar src/; tras editar docs/, npm run sync)
npm run build      # dist/ estático
```

## Imagen Docker y acceso

El `Dockerfile` se construye **desde la raíz del repo** (`docker build -f game-docs/Dockerfile .`)
porque necesita `docs/` y los README; `Dockerfile.dockerignore` deja entrar solo eso. El sitio
estático se sirve con nginx en `127.0.0.1:8099` (`DOCS_SITE_LISTEN`).

En `compose.loader.yaml` corre como `game-docs`. El nginx de game-client publica `/docs/` con
**basic auth** contra los administradores de game-crud (tabla `ADMIN`), igual que `/admin/` y
`/api-docs/`.

El CI compila el sitio (`npm run build`) en cada push; si falta un archivo listado en `PAGES`, el
sync falla y el build también.
