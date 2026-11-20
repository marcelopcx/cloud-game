// Copia la documentación del repo (docs/ y los README de cada módulo) a src/content/docs/ para
// Starlight. La fuente sigue siendo docs/: este script corre antes de `dev` y `build`, y lo
// generado no se commitea (.gitignore).
//
// Por cada archivo: el primer `# Título` pasa al frontmatter y los links relativos se reescriben:
//   - a otro documento publicado → su página del sitio (con el ancla, si tiene)
//   - a un archivo de docs/ que no es Markdown (p. ej. el .drawio) → se copia a public/files/
//   - a cualquier otra cosa del repo (código, compose, scripts) → GitHub
import { copyFileSync, existsSync, mkdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join, posix, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const SITE = resolve(HERE, '..');
const REPO = resolve(process.env.REPO_ROOT ?? join(SITE, '..'));
const BASE = '/docs';
const GITHUB = 'https://github.com/marcelopcx/cloud-game';

const OUT = join(SITE, 'src/content/docs');
const FILES = join(SITE, 'public/files');

// Archivo del repo → ruta en el sitio. El orden del menú está en astro.config.mjs.
const PAGES = {
  'README.md': { slug: 'introduccion', title: 'Introducción', from: /\n---\n/ },
  'docs/arquitectura.md': { slug: 'arquitectura' },
  'docs/despliegue.md': { slug: 'despliegue' },
  'docs/nginx.md': { slug: 'nginx' },
  'docs/game-station/README.md': { slug: 'game-station' },
  'docs/game-station/api.md': { slug: 'game-station/api' },
  'docs/game-station/media.md': { slug: 'game-station/media' },
  'docs/game-station/input.md': { slug: 'game-station/input' },
  'docs/game-loader/README.md': { slug: 'game-loader' },
  'docs/game-dispatcher/README.md': { slug: 'game-dispatcher' },
  'docs/game-client/README.md': { slug: 'game-client' },
  'docs/game-crud/README.md': { slug: 'game-crud' },
  'docs/game-admin/README.md': { slug: 'game-admin' },
  'games/README.md': { slug: 'juegos' },
  'docs/games/README.md': { slug: 'juegos/manifiesto' },
  'docs/games/requisitos.md': { slug: 'juegos/requisitos' },
  'game-api/README.md': { slug: 'game-api' },
  'docs/game-docs/README.md': { slug: 'game-docs' },
};
// El índice de docs/ lo reemplaza la portada del sitio.
const ALIASES = { 'docs/README.md': '' };

const pageUrl = (slug) => (slug ? `${BASE}/${slug}/` : `${BASE}/`);

function rewriteLink(target, srcFile) {
  if (/^([a-z]+:|#|\/)/i.test(target)) return target; // http, mailto, anclas, absolutos
  const [rawPath, anchor] = target.split('#');
  const repoPath = posix.normalize(posix.join(posix.dirname(srcFile), decodeURI(rawPath))).replace(/\/$/, '');
  const hash = anchor ? `#${anchor}` : '';

  if (PAGES[repoPath]) return pageUrl(PAGES[repoPath].slug) + hash;
  if (repoPath in ALIASES) return pageUrl(ALIASES[repoPath]) + hash;
  // Carpeta de docs con README publicado (p. ej. "docs/game-loader").
  if (PAGES[`${repoPath}/README.md`]) return pageUrl(PAGES[`${repoPath}/README.md`].slug) + hash;

  if (repoPath.startsWith('docs/') && existsSync(join(REPO, repoPath)) && statSync(join(REPO, repoPath)).isFile()) {
    const dest = join(FILES, repoPath.slice('docs/'.length));
    mkdirSync(dirname(dest), { recursive: true });
    copyFileSync(join(REPO, repoPath), dest);
    return encodeURI(`${BASE}/files/${repoPath.slice('docs/'.length)}`);
  }

  // Sin extensión → carpeta (GitHub redirige si no lo es).
  const kind = /\.[^/]+$/.test(repoPath) ? 'blob' : 'tree';
  return encodeURI(`${GITHUB}/${kind}/main/${repoPath}`) + hash;
}

// Solo fuera de bloques de código.
function rewriteLinks(markdown, srcFile) {
  return markdown
    .split(/(^```[\s\S]*?^```)/m)
    .map((chunk, i) =>
      i % 2 === 1
        ? chunk
        : chunk
            .replace(/(\]\()([^)\s]+)(\))/g, (_, open, target, close) => open + rewriteLink(target, srcFile) + close)
            // <img src="…"> (diagramas .drawio.svg de docs/diagrams)
            .replace(/(<img [^>]*src=")([^"]+)(")/g, (_, open, target, close) => open + rewriteLink(target, srcFile) + close),
    )
    .join('');
}

function convert(srcFile, { slug, title, from }) {
  let body = readFileSync(join(REPO, srcFile), 'utf8');
  if (from) body = body.slice(body.search(from) + body.match(from)[0].length);

  const h1 = body.match(/^# (.+)$/m);
  if (h1 && !title) title = h1[1].replace(/`/g, '').trim();
  if (h1) body = body.replace(h1[0], '');

  const frontmatter = [
    '---',
    `title: ${JSON.stringify(title)}`,
    `editUrl: ${JSON.stringify(`${GITHUB}/edit/main/${srcFile}`)}`,
    '---',
    '',
    `<!-- Generado por game-docs/scripts/sync-docs.mjs desde ${srcFile}. Editar el original. -->`,
    '',
    '',
  ].join('\n');

  const dest = join(OUT, `${slug}.md`);
  mkdirSync(dirname(dest), { recursive: true });
  writeFileSync(dest, frontmatter + rewriteLinks(body.trim(), srcFile) + '\n');
}

// Limpia lo generado antes (conserva index.mdx y las páginas escritas a mano).
for (const { slug } of Object.values(PAGES)) rmSync(join(OUT, `${slug}.md`), { force: true });
rmSync(FILES, { recursive: true, force: true });

const missing = [];
for (const [src, page] of Object.entries(PAGES)) {
  if (!existsSync(join(REPO, src))) {
    missing.push(src);
    continue;
  }
  convert(src, page);
}
console.log(`sync-docs: ${Object.keys(PAGES).length - missing.length} páginas desde ${REPO}`);
if (missing.length) {
  console.error(`sync-docs: faltan ${missing.join(', ')}`);
  process.exit(1);
}
