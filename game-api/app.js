// Une los OpenAPI de los módulos en un solo documento para Scalar. Cada módulo se
// monta bajo el prefijo con el que lo reenvía el nginx de game-api (nginx.conf):
//   /dispatcher/…          → game-dispatcher (el Bearer admin lo agrega nginx)
//   /loader/…              → game-loader (el Bearer lo agrega nginx)
//   /station/{httpPort}/…  → game-station (8090 fija, 8200+ del loader)
//   /crud/…                → game-crud (spec generado por springdoc)
// Así cada ruta del documento es una URL real y "Probar" funciona con el login del admin.

// Página en /api-docs/ (detrás de game-client) o en / (contenedor directo).
const BASE = new URL('.', location.href).href.replace(/\/$/, '');

const MODULES = [
  {
    key: 'Dispatcher',
    group: 'Game Dispatcher',
    prefix: '/dispatcher',
    load: () => loadYaml('openapi/game-dispatcher.yaml'),
    note:
      'Servicio `game-dispatcher` (:8094). Ruta nativa = quitar `/dispatcher`. El Bearer ' +
      '`DISPATCHER_API_TOKEN` lo agrega el proxy; `/sessions` pide el header `X-Player-Id`.',
  },
  {
    key: 'Loader',
    group: 'Game Loader',
    prefix: '/loader',
    load: () => loadYaml('openapi/game-loader.yaml'),
    note:
      'Servicio `game-loader` (:8095). Ruta nativa = quitar `/loader`. El Bearer ' +
      '`LOADER_API_TOKEN` lo agrega el proxy: no hace falta cargarlo.',
  },
  {
    key: 'Station',
    group: 'Game Station',
    prefix: '/station/{httpPort}',
    load: () => loadYaml('openapi/game-station.yaml'),
    note:
      'Servicio `game-station`: `8090` (estación fija) o `8200+` (el `httpPort` que devuelve ' +
      '*Crear contenedor*). Ruta nativa = quitar `/station/{httpPort}`.',
    pathParams: [
      {
        name: 'httpPort',
        in: 'path',
        required: true,
        description: '`8090` (estación fija) o `8200`–`8299` (estaciones del loader).',
        schema: { type: 'string', default: '8200', pattern: '^(8090|82[0-9]{2})$' },
      },
    ],
  },
  {
    key: 'Crud',
    group: 'Catálogo (game-crud)',
    prefix: '/crud',
    load: () => loadJson('crud/v3/api-docs'),
    note:
      'Servicio `game-crud` (:8096), documentación generada de los controladores. ' +
      'Ruta nativa = quitar `/crud`. `game-admin` lo usa en `/admin/api`.',
  },
];

const METHODS = ['get', 'put', 'post', 'delete', 'options', 'head', 'patch', 'trace'];

async function loadYaml(url) {
  const res = await fetch(url, { cache: 'no-cache' });
  if (!res.ok) throw new Error(`${url}: HTTP ${res.status}`);
  return jsyaml.load(await res.text());
}

async function loadJson(url) {
  const res = await fetch(url, { cache: 'no-cache' });
  if (!res.ok) throw new Error(`${url}: HTTP ${res.status}`);
  return res.json();
}

// Renombra "#/components/<tipo>/<Nombre>" → "#/components/<tipo>/<Módulo>.<Nombre>" para que
// Health, Error, etc. de cada módulo no choquen.
function prefixRefs(node, key) {
  if (Array.isArray(node)) return node.map((n) => prefixRefs(n, key));
  if (!node || typeof node !== 'object') return node;
  const out = {};
  for (const [k, v] of Object.entries(node)) {
    out[k] =
      k === '$ref' && typeof v === 'string' && v.startsWith('#/components/')
        ? v.replace(/^(#\/components\/[^/]+\/)/, `$1${key}.`)
        : prefixRefs(v, key);
  }
  return out;
}

function mount(doc, mod, merged) {
  const spec = prefixRefs(doc, mod.key);
  const tagName = (t) => `${mod.group} · ${t}`;
  const tags = new Map((spec.tags || []).map((t) => [t.name, t]));

  for (const [path, item] of Object.entries(spec.paths || {})) {
    const target = { ...item };
    if (mod.pathParams) target.parameters = [...mod.pathParams, ...(item.parameters || [])];
    for (const m of METHODS) {
      const op = item[m];
      if (!op) continue;
      const opTags = op.tags && op.tags.length ? op.tags : ['General'];
      opTags.forEach((t) => tags.has(t) || tags.set(t, { name: t }));
      // Por el proxy no se manda Authorization: el basic auth del admin lo ocupa y el
      // Bearer del loader lo pone nginx.
      const { security, ...rest } = op;
      target[m] = {
        ...rest,
        tags: opTags.map(tagName),
        operationId: `${mod.key.toLowerCase()}_${op.operationId || m + path.replace(/\W+/g, '_')}`,
      };
    }
    merged.paths[mod.prefix + (path === '/' ? '' : path)] = target;
  }

  const groupTags = [];
  for (const t of tags.values()) {
    const name = tagName(t.name);
    const first = groupTags.length === 0;
    merged.tags.push({
      ...t,
      name,
      'x-displayName': t.name,
      description: [first ? `> ${mod.note}` : '', first ? spec.info?.description || '' : '', t.description || '']
        .filter(Boolean)
        .join('\n\n'),
    });
    groupTags.push(name);
  }
  merged['x-tagGroups'].push({ name: mod.group, tags: groupTags });

  for (const [type, entries] of Object.entries(spec.components || {})) {
    if (type === 'securitySchemes') continue;
    merged.components[type] ??= {};
    for (const [name, value] of Object.entries(entries)) {
      merged.components[type][`${mod.key}.${name}`] = value;
    }
  }
}

function unavailable(mod, err, merged) {
  const name = `${mod.group} · No disponible`;
  merged.tags.push({
    name,
    'x-displayName': 'No disponible',
    description: `> ${mod.note}\n\nNo se pudo cargar el spec (${err.message}).`,
  });
  merged['x-tagGroups'].push({ name: mod.group, tags: [name] });
}

const INTRO = `Referencia de **todos los endpoints** de Cloud Game, agrupados por módulo.
El botón **Probar** envía la petición por el proxy de esta página, con la misma sesión que el
panel admin.

| Módulo | Servicio | Prefijo en esta página |
| --- | --- | --- |
| Game Dispatcher | \`game-dispatcher\` :8094 | \`/dispatcher\` |
| Game Loader | \`game-loader\` :8095 | \`/loader\` |
| Game Station | \`game-station\` :8090 / :82xx | \`/station/{httpPort}\` |
| Catálogo | \`game-crud\` :8096 | \`/crud\` |

**Flujo de una partida:** *Pedir partida* (dispatcher) → *Obtener sesión* hasta \`ASSIGNED\` →
estación con ese \`httpPort\` (ya en \`READY\`): *Launch* con \`stationSessionId\` → WebRTC →
*Stop* → *Liberar sesión* (la estación vuelve al pool).

### Rutas públicas (nginx de game-client)

| Ruta | Destino |
| --- | --- |
| \`POST /dispatcher/sessions\`, \`GET·DELETE /dispatcher/sessions/{id}\` | Dispatcher (solo esas tres) |
| \`/ports/{httpPort}/…\` (REST y WS) | Estación asignada por el dispatcher |
| \`/station/…\`, \`/stations/{gameId}/…\` | Estación fija / una por juego (otros modos de despliegue) |
| \`/admin/\`, \`/admin/api/…\` | Panel game-admin y game-crud (basic auth) |
| \`/api-docs/\` | Esta documentación (basic auth) |

Errores en todos los módulos: \`{ "error": { "code", "message" } }\`, JSON en camelCase.`;

async function build() {
  const merged = {
    openapi: '3.1.0',
    info: { title: 'Cloud Game API', version: '1.0.0', description: INTRO },
    servers: [{ url: BASE, description: 'Proxy de la documentación (login del admin)' }],
    tags: [],
    'x-tagGroups': [],
    paths: {},
    components: {},
  };
  const docs = await Promise.allSettled(MODULES.map((m) => m.load()));
  docs.forEach((r, i) =>
    r.status === 'fulfilled' ? mount(r.value, MODULES[i], merged) : unavailable(MODULES[i], r.reason, merged),
  );
  return merged;
}

build().then((content) => {
  document.getElementById('cloudgame-loading')?.remove();
  Scalar.createApiReference('#app', {
    content,
    title: 'Cloud Game API',
    slug: 'cloudgame',
    theme: 'none',
    forceDarkModeState: 'dark',
    hideDarkModeToggle: true,
    withDefaultFonts: false, // sin fuentes de un CDN
    telemetry: false,
    agent: { disabled: true },
    mcp: { disabled: true },
    showDeveloperTools: 'never',
    documentDownloadType: 'both',
    defaultHttpClient: { targetKey: 'shell', clientKey: 'curl' },
  });
});
