# Documentación de las APIs (Scalar)

**Cloud Game API**: un solo documento [Scalar](https://scalar.com/) con **todos los
endpoints** de los módulos, agrupados en la barra lateral, con el logo y los colores de game-client
y el botón **Probar**.

| Grupo | Endpoints | Spec | Prefijo en la página |
| --- | --- | --- | --- |
| Game Dispatcher | `/health`, `/sessions` (pedir, obtener, liberar), `/stations`, `/pool` | [`openapi/game-dispatcher.yaml`](openapi/game-dispatcher.yaml) (a mano) | `/dispatcher` |
| Game Loader | `/health`, `/images`, `/containers` (crear, listar, obtener, eliminar, logs, logs SSE) | [`openapi/game-loader.yaml`](openapi/game-loader.yaml) (a mano) | `/loader` |
| Game Station | `/health`, `/prepare`, `/launch`, `/stop` + WebSockets `/ws/control` y `/ws/webrtc` (mensajes en *Models*) | [`openapi/game-station.yaml`](openapi/game-station.yaml) (a mano) | `/station/{httpPort}` |
| Catálogo (game-crud) | `/games`, `/games/{id}`, `/games/search`, `/categories`, `/categories/{id}/games`, `/health` | `/v3/api-docs`, generado por springdoc de los controladores | `/crud` |

`game-admin` y `game-client` no tienen API propia: sus rutas de nginx (`/dispatcher/sessions`,
`/ports/…`, `/admin/api/…`) están en la introducción de la página.

## Acceso

`https://<PUBLIC_HOST>/api-docs/`, con el **mismo login que el panel admin**: el nginx de
`game-client` valida el basic auth contra los administradores de game-crud (tabla `ADMIN`), igual
que en `/admin/`.

Cada ruta del documento es una URL real del proxy de este contenedor, así que **Probar** funciona
tal cual. El token del loader no llega al browser: nginx lo agrega en `/loader/`, así que en Scalar
no se carga ningún Bearer. Para la estación, poner en `httpPort` el que devolvió *Crear contenedor*
(`8200+`) o `8090` para la estación fija.

## Cómo está armado

```
game-api/
├── Dockerfile      nginx + bundles de @scalar/api-reference y js-yaml (versiones fijas, sin CDN)
├── nginx.conf      estáticos y proxies /loader/ (con Bearer), /station/<puerto>/ y /crud/
├── index.html      encabezado con el logo
├── app.js          une los tres specs en uno (prefijos, tags por módulo, x-tagGroups)
├── theme.css       colores de game-client aplicados a las variables de Scalar
├── logo.svg        copia de game-client/assets/logo_header.svg
├── favicon.svg     el mando del logo
└── openapi/        specs escritos a mano (loader y estación)
```

`app.js` renombra los modelos por módulo (`Loader.Health`, `Station.Health`, `Crud.GameResponse`…)
para que no choquen, y quita la `security` de las operaciones (el basic auth ocupa el header
`Authorization`). Si un spec no carga (p. ej. game-crud caído), su grupo aparece como *No disponible*
y el resto se muestra igual.

En `compose.loader.yaml` corre como `game-api` con host network en `127.0.0.1:8098`.

| Variable | Default | Uso |
| --- | --- | --- |
| `DOCS_LISTEN` | `127.0.0.1:8098` | Dónde escucha el nginx. |
| `LOADER_UPSTREAM` | `http://127.0.0.1:8095` | Loader. |
| `DISPATCHER_UPSTREAM` | `http://127.0.0.1:8094` | Dispatcher. |
| `DISPATCHER_API_TOKEN` | vacío | Bearer que nginx agrega a `/dispatcher/` (API admin `/stations`, `/pool`). |
| `LOADER_API_TOKEN` | — | Bearer que se agrega en `/loader/`. |
| `CRUD_UPSTREAM` | `http://127.0.0.1:8096` | game-crud (con `API_DOCS_ENABLED=true`). |
| `STATION_HOST` | `127.0.0.1` | Host de las estaciones (`8090`, `8200`–`8299`). |

Actualizar Scalar: cambiar `SCALAR_VERSION` en el `Dockerfile`.

## Local

```bash
docker build -t cloudgame/game-api game-api
docker run --rm -p 8098:8098 -e DOCS_LISTEN=0.0.0.0:8098 \
  -e LOADER_UPSTREAM=http://host.docker.internal:8095 -e LOADER_API_TOKEN=<token> \
  -e CRUD_UPSTREAM=http://host.docker.internal:8096 -e STATION_HOST=host.docker.internal \
  cloudgame/game-api
```

Abrir `http://127.0.0.1:8098` (sin login: el basic auth lo pone el nginx de game-client).

## Mantener los specs

Si cambia una ruta, un campo o un código de error del loader o la estación, actualizar el YAML
en el mismo cambio (junto con `docs/game-station/api.md` o `docs/game-loader/README.md`).
game-crud se actualiza solo desde sus anotaciones `@Operation` / `@Schema`. Validar con:

```bash
npx @redocly/cli lint game-api/openapi/*.yaml
```

El único error esperado es `no-empty-servers`: el servidor lo define `app.js`.
