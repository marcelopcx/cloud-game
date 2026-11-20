# API de la Game Station

Contrato HTTP y WebSocket de [`game-station`](../../game-station). Todos los cuerpos JSON usan
**camelCase**. En producción el cliente accede a través de nginx con el prefijo `/station`
(por ejemplo `https://<host>/station/health`). Las rutas de este documento son las de la
estación, sin ese prefijo. La versión OpenAPI está en
[`game-api/openapi/game-station.yaml`](../../game-api/openapi/game-station.yaml) (Scalar en `/api-docs/`).

| Método | Ruta | Respuesta | Descripción |
| --- | --- | --- | --- |
| `GET` | `/health` | `200` | Snapshot del estado de la estación. |
| `POST` | `/prepare` | `202` | Inicia la copia de assets al cache. |
| `POST` | `/launch` | `200` | Arranca el juego y habilita el peer WebRTC. |
| `POST` | `/stop` | `204` | Termina la partida y vuelve a `IDLE`. Idempotente. |
| `WS` | `/ws/control` | — | Eventos de estado, del servidor al cliente. |
| `WS` | `/ws/webrtc` | — | Signaling SDP/ICE de un único peer. |

## REST

### `GET /health`

```json
{
  "status": "UP",
  "stationId": "station-1",
  "state": "IDLE",
  "gameId": null,
  "sessionId": null,
  "encoder": null,
  "cache": [{ "gameId": "supertuxkart", "version": "1.0.0" }],
  "supportedGameId": "supertuxkart",
  "needs": {
    "display": true,
    "audio": true,
    "gamepad": 2,
    "keyboard": true,
    "mouse": true,
    "relativeMouse": false
  },
  "httpPort": 8090,
  "display": ":99",
  "gameWidth": 1280,
  "gameHeight": 720,
  "playingSince": null,
  "lastInputAt": null,
  "peerConnected": false
}
```

| Campo | Descripción |
| --- | --- |
| `state` | `IDLE` · `PREPARING` · `READY` · `PLAYING` · `FAILED` |
| `gameId`, `sessionId` | Partida activa, o `null`. |
| `encoder` | `openh264` o `smpte` en `PLAYING`; `null` en cualquier otro estado. |
| `cache` | Entradas `CACHE_ROOT/<gameId>/<version>/` que ya tienen `payload.bin`. |
| `supportedGameId` | `id` del manifiesto de esta imagen. Es el único `gameId` aceptado. |
| `needs` | Periféricos que requiere el juego (del manifiesto). El cliente habilita pad, teclado y mouse según estos flags. |
| `gameWidth`, `gameHeight` | Resolución del juego (`STATION_SIZE`). El video puede venir más chico (`STATION_STREAM_SIZE`); el mouse absoluto se envía en estos píxeles. |
| `playingSince` | Epoch ms del `launch`; `null` fuera de `PLAYING`. |
| `lastInputAt` | Epoch ms del último input real: tecla, botón o stick presionado, o movimiento del mouse. Los datagramas en reposo que el cliente manda a 250 Hz no cuentan. `null` sin input todavía o fuera de `PLAYING`. |
| `peerConnected` | `true` si hay un socket `/ws/webrtc` abierto (solo en `PLAYING`). |

`playingSince`, `lastInputAt` y `peerConnected` los usa el Game Dispatcher para decidir inactividad y desconexión (con `IDLE_TIMEOUT_S=0` en la estación).

`/health` no incluye `progress`: el progreso solo se publica por `/ws/control`.

### `POST /prepare`

```json
{
  "sessionId": "0b8f…",
  "gameId": "supertuxkart",
  "version": "1.0.0",
  "source": {
    "type": "local",
    "path": "/library/supertuxkart/1.0.0",
    "checksum": "sha256:00"
  }
}
```

Respuesta `202`:

```json
{ "sessionId": "0b8f…", "state": "PREPARING" }
```

El `202` solo confirma que la copia empezó. El resultado llega de forma asíncrona como
`STATE READY` o `STATE FAILED` por `/ws/control`, y también se refleja en `/health`.

| Campo | Regla |
| --- | --- |
| `sessionId`, `gameId`, `version` | Obligatorios, no vacíos. El `sessionId` lo genera el cliente. |
| `source.type` | `local` (default si viene vacío). `http` y `azure-sas` fallan con `UNSUPPORTED_SOURCE`. |
| `source.path` | Archivo, o directorio que contiene `payload.bin`. Un path `/library/<x>` se resuelve bajo `LIBRARY_ROOT/<x>`. |
| `source.checksum` | Obligatorio. `sha256:<hex>` o `<hex>`. Un digest de solo ceros (por ejemplo `sha256:00`) indica que se use el sidecar `checksum` (o `payload.bin.sha256`) de la library. |
| `source.url` | Se acepta y se ignora. |

La copia se escribe en `CACHE_ROOT/<gameId>/<version>/payload.bin` (a través de un `.part`) y se
verifica con SHA-256. Si el destino ya existe con el digest esperado, es un **cache hit**: no
se copia y el evento `READY` lleva `cacheHit: true`. Con `kind: test-pattern` no se lee disco.

### `POST /launch`

```json
{ "sessionId": "0b8f…", "gameId": "supertuxkart", "saveGrant": "eyJ…" }
```

`saveGrant` es opcional: lo trae la sesión del dispatcher cuando el jugador inició sesión. Con él, la
estación baja el progreso (`GET {SAVES_URL}/saves/{gameId}` de game-crud) y lo extrae antes de arrancar
el juego (un `404` es una partida nueva). Si no lo puede bajar, responde `500 LAUNCH_FAILED` y no
arranca, para no pisar el progreso guardado en el `stop`.

Respuesta `200`:

```json
{
  "state": "PLAYING",
  "wsUrl": "/ws/webrtc",
  "needs": { "display": true, "audio": true, "gamepad": 2, "keyboard": true, "mouse": true, "relativeMouse": false }
}
```

Requiere estado `READY`. Pasa a `PLAYING` y arranca, en este orden: input, Xvfb, juego, audio y
captura de video. Si el arranque falla, la estación ejecuta `stop` y responde `500 LAUNCH_FAILED`.

### `POST /stop`

```json
{ "sessionId": "0b8f…" }
```

Respuesta `204` sin cuerpo. `sessionId` debe venir, pero no se compara con la sesión activa.
Cierra el peer, la captura, los pads y los procesos del juego, y deja la estación en `IDLE`.
Si la partida se lanzó con `saveGrant`, primero empaqueta el progreso y lo sube
(`PUT {SAVES_URL}/saves/{gameId}`, con 3 intentos; si falla, queda en `/var/tmp/cloudgame-unsent/`).
Si venía de `PLAYING` y `STATION_RESET_SAVES=true` (default en la imagen), antes de pasar a `IDLE`
borra el progreso del jugador: vacía el cache (el próximo `prepare` vuelve a copiar, `cacheHit: false`),
restaura `HOME` y borra los `saves` del manifiesto. Así el contenedor queda listo para otro jugador.
Un `stop` que llega mientras otro está en curso (p. ej. el del browser y el del dispatcher a la vez)
espera a que termine y responde `204` sin hacer nada más: el progreso se sube una sola vez, antes del reset.

## Errores HTTP

Todos los errores de dominio usan el mismo cuerpo:

```json
{ "error": { "code": "GAME_NOT_READY", "message": "Hay que preparar el juego antes de lanzar" } }
```

| Código | HTTP | Cuándo |
| --- | --- | --- |
| `BAD_REQUEST` | 400 | Falla la validación del body (campo vacío o faltante). |
| `UNKNOWN_GAME` | 400 | `gameId` distinto del `id` del manifiesto de la imagen. |
| `STATION_BUSY` | 409 | `launch` con la estación ya en `PLAYING`. |
| `PREPARE_IN_PROGRESS` | 409 | `prepare` durante `PREPARING` o `PLAYING`. |
| `GAME_NOT_READY` | 424 | `launch` sin un `prepare` exitoso (estado distinto de `READY`). |
| `LAUNCH_FAILED` | 500 | Fallo al arrancar display, juego o nativo WebRTC. |

Los errores de preparación no salen por HTTP (la respuesta ya fue `202`). Se emiten por
`/ws/control`:

| Código | Causa |
| --- | --- |
| `SOURCE_NOT_FOUND` | `source.path` vacío, inexistente o sin `payload.bin`. |
| `CHECKSUM_MISMATCH` | El SHA-256 no coincide, o el checksum es un placeholder y no hay sidecar. |
| `UNSUPPORTED_SOURCE` | `source.type` es `http` o `azure-sas`. |
| `PREPARE_FAILED` | Error de E/S u otra excepción durante la copia. |

## WebSocket `/ws/control`

Canal de solo lectura para el cliente: la estación ignora lo que el cliente envíe. Admite
varias conexiones simultáneas y todas reciben los mismos eventos.

**`STATE`**, en cada transición y durante el progreso de `prepare`:

```json
{ "type": "STATE", "state": "PREPARING", "progress": 42, "message": "Copiando assets", "cacheHit": false, "gameWidth": 1280, "gameHeight": 720 }
```

`gameWidth`/`gameHeight` (en todos los `STATE`) son la resolución del juego, no la del stream.
El cliente convierte el mouse absoluto a esos píxeles: `x = xVideo / videoWidth * gameWidth`.

| `state` | `message` que envía la estación |
| --- | --- |
| `PREPARING` | `Copiando assets` · `Cache hit` · `Verificado` |
| `READY` | `Listo` |
| `PLAYING` | `En partida` |
| `IDLE` | `Libre` |
| `FAILED` | Mensaje del error |

**`ERROR`**, que precede a `STATE FAILED`:

```json
{ "type": "ERROR", "code": "CHECKSUM_MISMATCH", "message": "checksum sha256:… != sha256:…" }
```

## WebSocket `/ws/webrtc`

Signaling de un único peer. El navegador es el **offerer**.

Los mensajes de texto admiten hasta **256 KB**. El OFFER de Chrome/Opera pasa de los 8 KB que Tomcat acepta por defecto, y con ese límite el socket se cerraba con `1009` justo después del OFFER.

| Dirección | Mensaje |
| --- | --- |
| cliente → estación | `{ "type": "OFFER", "sdp": "…" }` |
| estación → cliente | `{ "type": "ANSWER", "sdp": "…" }` |
| ambos | `{ "type": "ICE", "candidate": "candidate:…", "sdpMid": "0", "sdpMLineIndex": 0 }` |
| estación → cliente | `{ "type": "ERROR", "code": "…" }` |

| Código de error | Cuándo |
| --- | --- |
| `NOT_PLAYING` | Se abrió el socket con la estación fuera de `PLAYING`. La estación cierra el socket. |
| `PEER_BUSY` | Ya hay otro socket de signaling abierto. La estación cierra el nuevo. |
| `SIGNALING` | Error procesando un `OFFER` o `ICE` (incluye `message`). |

- El OFFER del navegador trae dos transceivers `recvonly` (video y audio) y el DataChannel `input`.
- Los candidatos ICE que llegan antes del SDP remoto se encolan. Los candidatos locales se envían después del `ANSWER`.
- Al cerrar el socket se libera el peer, pero **el juego sigue corriendo**: otro navegador puede negociar sin relanzar la partida.

Los detalles de media y del filtrado ICE están en [media.md](media.md). El formato del
DataChannel está en [input.md](input.md).

## CORS

`CORS_ORIGINS` (lista separada por comas, admite patrones como `https://*.example.com`)
aplica a REST y a los dos WebSocket. Métodos permitidos: `GET`, `POST`, `OPTIONS`.
