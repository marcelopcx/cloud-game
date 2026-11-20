# Game Client

SPA web con la que el jugador navega el catálogo y juega. Controla la estación por REST y
WebSocket, recibe el stream por WebRTC y envía el input por un DataChannel.

| | |
| --- | --- |
| Código | [`game-client/`](../../game-client) |
| Framework | React 19 + TypeScript 6 |
| Build | Vite 8 (`@vitejs/plugin-react`) |
| Lint | ESLint 10 + `typescript-eslint`, `react-hooks`, `react-refresh` |
| Servido por | nginx 1.27 (imagen Docker) |
| Dependencias de runtime | Solo `react` y `react-dom`. WebRTC, Gamepad API y WebSocket son APIs nativas del navegador. |

## Pantallas

`App.tsx` alterna entre tres vistas, sin router (antes muestra un `SplashAnimation`):

El home se monta detrás del splash, que **no se puede saltar hasta que la página cargó**: el home avisa
cuando llegaron el catálogo y la distribución (`onDataReady`) y `usePageAssetsReady` espera las fuentes,
todas las `<img>` (las `loading="lazy"` pasan a eager) y los fondos CSS, revisando de nuevo por si
aparecieron más. Si la animación termina antes, queda en su último cuadro con "Cargando…" y sale sola al
completarse la carga; con todo cargado, un clic (o A/B del mando) la cierra cuando se quiera. Tope de
espera: 30 s, para que una imagen colgada no deje el splash puesto.

| Vista | Componente | Contenido |
| --- | --- | --- |
| Home | `screens/homeScreen.tsx` | Header con búsqueda, las secciones armadas en game-admin (banner principal, sub banners, carruseles y grids, en ese orden; ver abajo) y footer. Clic en una tarjeta abre `GameModal` (ficha del juego) con **Jugar**. |
| Login | `screens/loginScreen.tsx` | Se abre con el ícono de usuario del header. Tiene dos vistas: **Iniciar sesión** (`POST /catalog/auth/login`) y **Crear cuenta** (`POST /catalog/auth/register`), que pide username, correo, contraseña y un avatar de los predeterminados (`GET /catalog/avatars`, componente `AvatarPicker`; no se suben imágenes). La sesión (`{ token, user }`) queda en `localStorage` (`services/session.ts`, hook `usePlayerSession`). Con sesión, el botón de perfil muestra el avatar (`UserAvatar`, o la inicial si no tiene) y el panel ofrece **Cambiar avatar** (`PUT /catalog/auth/me/avatar`) y **Cerrar sesión**. Los amigos también se muestran con su avatar. |
| Player | `screens/playerScreen.tsx` | Con dispatcher, primero `useStationSession` pide la estación y muestra `ContainerLoader` (`Preparando tu estación…`, `En cola · posición N` o error con **Reintentar**). Después ejecuta `play(gameId)` y muestra video, overlay de estado y HUDs. **Volver** ejecuta `stop()`, libera la sesión (la estación vuelve al pool) y regresa al home. |

> **Catálogo.** El grid, el carrusel y las fichas del modal salen de [game-crud](../game-crud/README.md)
> (`GET /catalog/games`, ver `services/api/catalog.ts` y `hooks/useCatalog.ts`). El `gameId` es el último
> segmento de `path` (`/games/<gameId>`). Imágenes: `banner1` → banner principal, `banner2` → sub banner,
> `poster` → tarjeta vertical del carrusel, `cover` → tarjeta del grid, `background` (portada) → fondo del modal, `icon` → modal y barra del reproductor. Categorías → filtro de géneros; gamepads `Mando` → `gamepad`,
> `Teclado y ratón` → `mouse`. Si game-crud no responde, el home muestra el error con **Reintentar**.
>
> **Distribución del home.** `hooks/useHomeLayout.ts` pide `GET /catalog/home` (se edita en game-admin,
> pestaña **Home**; reglas en [Game Crud → Home](../game-crud/README.md#home)). Cada sección se dibuja
> en orden: `HERO` → `Banner` *featured* con el header, `BANNER` → `Banner` *highlight*, `CAROUSEL` →
> `Carousel`, `GRID` → `GamesGridLayout` (sin juegos elegidos, todo el catálogo por título; nunca los
> «Próximamente»), `UPCOMING` → `Carousel` con los juegos `COMING_SOON` (si no hay ninguno no se muestra).
> Si no hay secciones guardadas o la petición falla, se usa la distribución fija de siempre (SuperTuxKart,
> "Jugados recientemente", Twilight Princess, el carrusel «Próximamente» y el grid).
> Los dos banners destacados (SuperTuxKart y Twilight Princess) siguen fijos, con imágenes locales. La
> búsqueda solo hace `console.log`, y aunque se llegue al input con el mando hay que escribirla con el
> teclado físico ([Navegación con mando](#navegación-con-mando)).

> **Sesión y progreso.** Sin sesión se juega igual (anónimo, `X-Player-Id`), pero la estación borra el
> progreso al salir; mientras se espera la estación se muestra *Sin sesión: tu progreso no se guarda al
> salir*. Con sesión, `services/api/dispatcher.ts` agrega `Authorization: Bearer <token>` y la sesión del
> dispatcher trae `saveGrant`, que `useStation` manda en `POST /launch`: la estación restaura el progreso
> y lo guarda al parar. Si el dispatcher responde `401` (token vencido), se cierra la sesión y el error es
> `SESSION_EXPIRED`; **Reintentar** sigue como anónimo.

## Estructura

```
src/
├── app/            App.tsx: navegación home ↔ player
├── screens/        homeScreen, playerScreen
├── components/
│   ├── button/     Button, BackButton, Controls
│   ├── card/       CardGame (tarjeta compacta con plataformas y rating)
│   └── ui/         Header, Footer, Banner, HeroContent, Carousel, GamesGridLayout,
│                   CategoryFilter, Text, GameView, StatusBadge, ProgressBar,
│                   RtcStats, modal/{Modal, GameModal, ControlsModal}, splash,
│                   player/{GamePlayer, StationOverlay, ContainerLoader}
├── hooks/          useStation (orquesta la partida), useStationSession (sesión del dispatcher),
│                   useCatalog (juegos de game-crud), useGameInputs (controles del juego)
├── gamepad/        navegación de la UI con mando: GamepadNav, geometry (puro), focusCandidates,
│                   padMachine (puro), padReader, sessionGate
├── services/
│   ├── api/        station.ts (REST), dispatcher.ts (Game Dispatcher), catalog.ts (game-crud), urls.ts (base REST/WS)
│   ├── player.ts   id anónimo del jugador (localStorage → X-Player-Id)
│   └── ws/         control.ts (/ws/control)
├── webrtc/         session, peer, pump, snapshot, gamepad, keyboard, pointer, frame, input, stats
└── types/          Health, ControlEvent, respuestas REST, StatusGameStation, CatalogGameResponse
```

Aliases de import (`vite.config.ts`): `@` → `src`, `@components`, `@hooks`, `@services`, `@screens`.

## Flujo de una partida (`useStation`)

`useStation` concentra el estado de la partida: `status`, `progress`, `error`, `logs` (últimas
120 líneas), `needs`, pads conectados, teclas presionadas, HUD del mouse, pointer lock y
estadísticas RTC.

0. Con dispatcher (`VITE_DISPATCHER_URL`), `useStationSession` hace `POST /dispatcher/sessions {gameId}` con `X-Player-Id` y consulta la sesión cada 1 s hasta `ASSIGNED` (en cola muestra la posición). Después apunta las URLs de ese `gameId` a `station.stationBase` (`/ports/<httpPort>`, con `setStationBase`) y le pasa a `useStation` el `stationSessionId`. Al desmontar, o en `pagehide`, hace `DELETE` de la sesión (con `keepalive`). En partida revisa la sesión cada 5 s: si el dispatcher la terminó (inactividad, estación caída), muestra el motivo.
1. Al montar: `GET /health` (estado inicial y `needs`) y conexión a `/ws/control` (`STATE` y `ERROR`).
2. `play(gameId)`:
   0. Si `/health` ya está `READY` con el `sessionId` actual (la estación que preparó el dispatcher), va directo a `launch`.
   1. `POST /prepare` con un `sessionId` nuevo (UUID), `version: "1.0.0"` y `source` local `/library/<gameId>/1.0.0` con `checksum: "sha256:00"` (la estación usa el sidecar de la library).
   2. `waitUntilReady`: consulta `/health` cada 500 ms, hasta 120 s, hasta ver su `sessionId` en `READY`. Falla con `PREPARE_FAILED`, `STATION_BUSY` o `PREPARE_TIMEOUT`.
   3. `POST /launch` y luego `attachWebrtc` sobre el `wsUrl` devuelto.
3. `stop()`: cierra el peer, hace `POST /stop` y genera un `sessionId` nuevo.
4. Si a los 4 s de `PLAYING` no hay track o el video no tiene frames, muestra un aviso (`sin video — ¿display en la estación? / ¿HTTPS?` o `video sin frames — click en la pantalla`).

## WebRTC

| Módulo | Función |
| --- | --- |
| `session.ts` | Abre `/ws/webrtc`, crea el peer, envía el `OFFER`, enlaza video y audio a `<video>`/`<audio>`, arranca el input al abrir el DataChannel y lee stats cada 1 s. |
| `peer.ts` | `RTCPeerConnection` con `VITE_ICE_SERVERS`, transceivers `recvonly` de video y audio, y DataChannel `input` (`ordered: false`, `maxRetransmits: 0`). Pide un jitter buffer y un playout delay de 0 y `contentHint = "motion"`. |
| `stats.ts` | kbps de video y audio, RTT, FPS, paquetes perdidos, jitter buffer y codec negociado. |
| `upscale.ts` | Upscaling del stream con AMD FSR 1.0 (EASU + RCAS) en WebGL2. Ver [Upscaling](#upscaling). |

El input (`pump`, `snapshot`, `gamepad`, `keyboard`, `pointer`, `frame`, `input`) está
documentado en [Input remoto](../game-station/input.md#cliente).

## Interfaz del player

| Componente | Muestra |
| --- | --- |
| `GameView` | Stream (`UpscaledVideo`), pantalla completa (incluye API webkit) y el menú de configuración. |
| `UpscaledVideo` | `<video>` del stream con un `<canvas>` FSR encima (ver [Upscaling](#upscaling)). |
| `StationOverlay` | Mensaje por estado (`Conectando…`, `Preparando…` con barra de progreso, `Iniciando…`, `Esperando video…`) o error con **Reintentar**. |
| `StatusBadge` | Estado actual de la estación. |
| `ControlsModal` | Se abre con **Configuración → Controles**: cada acción del juego con su tecla y su botón de mando (`GET /catalog/games/{id}/inputs`, el juego se busca por `path`). Los pide `useGameInputs` en `PlayerScreen` mientras se crea el contenedor, así el modal abre con los datos. |
| `RtcStatsOverlay` | Métricas de `stats.ts` y estado del upscaler (`FSR 854×480→1920×1080 0.8ms`, `nativo …`, `upscale: off`). |

Con video, si el cursor queda quieto 2,4 s se ocultan la barra del juego (volver, ícono y título)
y el botón de Configuración, con el mouse del juego encendido o apagado; vuelven al mover el
cursor. No se ocultan mientras el cursor está encima de ellos, con el menú de opciones abierto
(también si se abrió con el Home del mando) ni con el modal de controles abierto.

### Upscaling

Si la estación envía el video más chico que el juego (`STATION_STREAM_SIZE`), el cliente lo
agranda con FSR 1.0 antes de mostrarlo:

```
<video> ──requestVideoFrameCallback──► textura ─EASU─► FBO ─RCAS─► <canvas>
```

- El `<video>` sigue montado debajo: da el `srcObject`, el autoplay y la geometría del mouse.
  El canvas se alinea al área visible (`object-fit: contain`) y no recibe eventos, así que
  pointer lock, clicks y pantalla completa (sobre el documento, así la barra del player sigue visible) no cambian.
- **off** (default): `<video>` normal. El usuario lo activa desde el menú del player
  (**Rendimiento** → **Calidad**), que pasa a **auto**: se activa solo si el stream es más
  chico que el canvas en píxeles físicos (a 1:1 o al reducir, EASU empeora la imagen).
- Fallback al `<video>` normal sin WebGL2, si se pierde el contexto o si el render supera
  4 ms por frame (CPU de subida + GPU medida con `EXT_disjoint_timer_query_webgl2`).
- En iOS, el fullscreen nativo del `<video>` (`webkitEnterFullscreen`) muestra el video sin FSR.
- Escape no sale de pantalla completa: `GameView` lo bloquea con la Keyboard Lock API
  (`navigator.keyboard.lock(['Escape'])`) y la tecla llega al juego. Solo en Chromium y con
  HTTPS/localhost; mantener Escape ~2 s sigue saliendo (lo impone el navegador). En Safari y
  Firefox Escape sale igual (no hay forma de bloquearlo), así que el primer click o tecla
  siguiente vuelve a pantalla completa. Salir con el doble click del video no dispara ese regreso, y
  el botón de volver del player (`data-keep-windowed`) sale sin volver a pantalla completa.
- Si el juego usa gamepad (`needs.gamepad > 0`), el mouse arranca apagado en el menú de opciones;
  el usuario lo prende a mano.

El mouse absoluto no depende de la resolución del stream: `pointer.ts` lo escala a
`gameWidth`/`gameHeight` de la estación (ver [Input remoto](../game-station/input.md)).

### Navegación con mando

`gamepad/` mueve el foco de la interfaz con el mando y activa lo que esté enfocado. Es un camino
distinto del input del juego (ver [Input remoto](../game-station/input.md#cliente)): ese pump manda
el pad a la estación a 250 Hz, este maneja el DOM.

| Módulo | Función |
| --- | --- |
| `GamepadNav.tsx` | Se monta una vez en `App.tsx`. Convierte los eventos del pad en `focus()`, scroll y click. |
| `geometry.ts` | `pickInDirection`: función pura (sin DOM) que elige el candidato en una dirección. Test en `game-client/test/focusGeometry.test.ts`. |
| `focusCandidates.ts` | Arma la lista de elementos enfocables visibles, saltando los que no aplican; detecta los recortados por un contenedor con overflow y elige qué scrollea el stick derecho. |
| `padMachine.ts` | Función pura (sin DOM): flancos de botón, repetición de dirección, histéresis del stick y scroll del stick derecho. Test en `game-client/test/padMachine.test.ts`. |
| `padReader.ts` | Loop de `requestAnimationFrame` que lee todos los mandos conectados y alimenta a `padMachine`. Reusa la máscara de `webrtc/gamepad.ts`. |
| `sessionGate.ts` | Store con `useSyncExternalStore`: avisa si hay partida con input en curso y si el menú de opciones del player está abierto; el mando es del juego solo con partida y el menú cerrado. |

**Botones.**

| Botón | Acción |
| --- | --- |
| D-pad / stick izquierdo | Mover el foco. Repetir a los 400 ms y luego cada 120 ms mientras se mantiene. |
| Stick derecho | Scroll vertical continuo, proporcional a la inclinación: de la página o, con un diálogo abierto, de su área con scroll (la tabla de controles, la descripción del juego). |
| A / Cross | Activar el elemento enfocado. |
| B / Circle | Atrás: cierra el diálogo abierto; si no hay, cierra el menú de opciones del player; si no, usa el botón de volver de la pantalla (loader, error de la estación). En el home no hace nada. |
| LB / RB | En un carrusel o en el filtro de géneros, el foco salta una página de elementos (en el borde no hace nada). Fuera, una altura de viewport (con un diálogo abierto, de su área con scroll; nunca el fondo tapado), y el foco pasa a lo visible más cercano a donde estaba. |
| Home / Guide / PS | En el player abre el menú de opciones (Configuración) con el foco en su primera opción, también en plena partida; otro Home lo cierra (con el diálogo de controles, si quedó abierto) y le devuelve el mando al juego. En el home no hace nada. |
| Start / Menu | Sin acción. |

Se leen hasta cuatro mandos a la vez (botones sumados, de cada stick el más inclinado): con un pad
virtual (Steam, DS4Windows) el que tiene el usuario no siempre es el primero. En los mandos sin
mapeo estándar el D-pad que llega como ejes 6/7 también cuenta, salvo que esos ejes no estén en
reposo al conectar (entonces son otra cosa, por ejemplo gatillos).

Un botón o una dirección que ya estaba apretado cuando la UI recupera el mando (al salir del juego,
al reconectar el pad) no dispara nada hasta soltarlo: un A mantenido no puede activar lo enfocado.

**Cómo elige el destino.** No hay que registrar ningún componente: entra todo lo que ya es
enfocable con el teclado (`Button`, `CardGame` con `role="button"`, los chips de `CategoryFilter`,
las flechas de `Carousel`, el logo, el buscador, `BackButton`, el menú de opciones). La búsqueda es
por anillos, como en las interfaces de TV:

1. Se descartan los que no avanzan en el eje de la dirección.
2. Los que quedan se ordenan por distancia al origen, manda la mayor de las dos ejes y la menor
   entra como penalización (así la diagonal cuesta más que ir en línea recta).
3. Gana el **más cercano** que además comparta banda con el origen en el eje perpendicular.
4. Si ninguno comparte banda, el más cercano dentro del **cono de 45°** de la dirección (un vecino
   apenas desalineado). Fuera del cono no hay destino.

Que sea por distancia y no por "mejor alineación global" es lo que evita que un contenedor grande
—el banner de pantalla completa— se trague a los vecinos chicos que tiene pegados. El cono evita lo
contrario: que "derecha" al final de una fila del grid salte a algo que está, sobre todo, arriba
(una sección entera más arriba). Desde el botón *Jugar* del banner se sale con "abajo". No hay
wrap: `down` desde la última fila se queda quieto, que es lo esperable en un grid.

Excepción para los **banners**: al moverse arriba/abajo, si entre el origen y el destino elegido (o
sin destino) hay un `Banner`, el foco se detiene en su acción (*Jugar*). En el banner *highlight* el
botón está a la derecha y, desde una tarjeta o un chip de la izquierda, quedaba fuera del cono: el
foco se saltaba el banner entero.

Los candidatos fuera del viewport también cuentan: eso permite bajar al grid sin scrollear antes.
Los que **recorta un contenedor con overflow** (las tarjetas ocultas del carrusel, los chips fuera
del filtro) solo se alcanzan desde dentro de ese mismo contenedor: pasar a la tarjeta siguiente sí,
llegar a ellas desde otra sección no.

**El scroll solo se mueve cuando hace falta.** `focusItem` pregunta si el elemento entra entero en
el viewport (con 12 px de holgura para el anillo) y sin recortar por su contenedor; si es así, no
toca el scroll. Llamar a `scrollIntoView` sobre algo que ya está a la vista igual mueve algo, y como
el carrusel tiene `scroll-snap-type: x mandatory` y `scroll-behavior: smooth`, el navegador
re-ancla después y la pantalla da un tirón. Cuando sí hay que moverse va con `behavior: 'instant'`:
`auto` respetaría el `scroll-behavior: smooth` de los contenedores y repartiría el ajuste en varios
cuadros.

Por lo mismo el foco tiene prioridad sobre el scroll del stick: con una dirección mantenida el stick
derecho no scrollea. Y si el foco quedó fuera de la vista (por el stick derecho, la rueda del mouse
o un carrusel corrido), el siguiente movimiento lo retoma en lo visible más cercano a donde estaba,
en vez de saltar hacia atrás.

El origen de la navegación sale de la lista de candidatos: si el foco estaba en algo que no se
navega (el `<video>` del player, o `body` al volver del juego), el primer movimiento lleva al último
elemento enfocado fuera de un diálogo (la tarjeta o el banner desde el que se lanzó el juego) o, si
ya no existe, al primero que se ve.

Quedan fuera los subárboles `inert`, los `aria-hidden` y lo que no tiene área. `Modal` marca
`inert` el fondo y `MenuOptionGame` su lista cerrada, así que en ambos casos el recorrido se acota
solo, sin código extra. Mientras está el splash de entrada el mando no navega el home de abajo: A o
B lo saltean, como un click (solo cuando la página ya cargó).

**Lo que no le compete al mando** sale de la lista de candidatos (`focusCandidates.ts`), pero solo
para el mando: con ratón y teclado físico sigue funcionando igual, porque no se toca `inert`,
`aria-hidden` ni `tabindex` de nada.

| Zona | Por qué |
| --- | --- |
| `footer` | Enlaces sociales, legales y de contacto: son del sitio, no del juego. |
| `<video>` y `.game-view` | Son `tabindex="0"` a pantalla completa para el foco del juego y el pointer lock: no se activan con A y pondrían un anillo enorme sobre nada. |

A tampoco los activa: si el foco quedó en el footer (por un clic con el ratón), A solo vuelve a
mostrar el foco en algo navegable. Tampoco activa nada con el anillo oculto: el primer A o D-pad
después de usar el mouse solo muestra dónde está el foco.

**El input del buscador sí se enfoca con el mando.** Se llega a él con el D-pad y se escribe con el
teclado físico: al primer `keydown` el anillo desaparece porque el teclado pasó a ser el dispositivo
activo. Los demás campos de texto (`textarea`, `select`, `contenteditable`) sí quedan fuera del
recorrido, porque a esos no hay forma de llegarles a escribir.

**Cuándo está activo.** `session.ts` publica en `sessionGate` si el mando ya lo tiene el juego.
La navegación arranca sola cuando no hay partida (todo el home, y también el loader, los errores y
el **Reintentar** del player) y se desactiva en cuanto se abre el DataChannel, aunque el juego no
use gamepad: si no, el primer D-pad enfocaría *Salir del juego* y A cerraría la partida.

En partida el lector de la UI sigue corriendo pero solo atiende **Home**: abre el menú de opciones
y, mientras está abierto (`MenuOptionGame` lo publica en `sessionGate`, también si se abrió con el
mouse), el mando navega el menú y `pump.ts` manda los pads en reposo, así el juego no recibe lo que
se aprieta en el menú. Al cerrarlo (Home, B, Escape o un click afuera) el mando vuelve al juego; los
botones que estaban apretados en ese momento (la B que lo cerró) no llegan al juego hasta soltarlos.
El bit Guide nunca viaja a la estación: Home es del cliente. Cuando la
sesión se cae (socket cerrado, ICE fallida o el watchdog de video mudo de `useStation`) el input se
suelta en el acto, así que el aviso de desconexión se cierra con A o B. Al volver del juego el
anillo reaparece con el primer toque.

**El anillo de foco** (`html[data-input="gamepad"] [data-gamepad-focus]`) se dibuja solo mientras
el mando es el dispositivo activo: el primer `pointermove`, `pointerdown` o `keydown` lo saca y con
ratón o teclado la interfaz queda igual que antes. Al pasarle el mando al juego también se borra, y
un `focus()` sobre algo que el mando no navega (el `<video>`) no lo vuelve a dibujar. No se usa
`:focus-visible` porque el foco es programático y sin teclado de por medio.

**Lo que no hace.** La Gamepad API no trae entrada de texto, así que **no se puede escribir con el
mando**: se llega al input del buscador pero hay que escribir con el teclado físico (y hoy su
`onSearch` hace `console.log`, no filtra). Como el resto de la Gamepad API, solo funciona en
contexto seguro (HTTPS o `localhost`), y el navegador no expone el mando hasta que el usuario
aprieta un botón.

Un botón del mando **no cuenta como gesto del usuario** para el navegador: con solo el mando no hay
pantalla completa (se reintenta al primer click o tecla) ni sonido. Para que no quede la pantalla
en negro, si el navegador bloquea la reproducción con sonido el video arranca mudo y el player
muestra "click en el video para activar el sonido".

### Mensajes de error

`services/api/station.ts` traduce los códigos de la estación:

| Código | Mensaje |
| --- | --- |
| `STATION_BUSY` | El Spark está en partida. Probá en un minuto. |
| `GAME_NOT_READY` | Hay que esperar a que termine de preparar. |
| `PREPARE_IN_PROGRESS` | La estación ya está ocupada. |
| `PEER_BUSY` | Ya hay un peer en esta estación. |
| `NOT_PLAYING` | La estación no está en partida. |
| `CHECKSUM_MISMATCH` | El juego en cache no coincide. Volvé a preparar. |
| `SOURCE_NOT_FOUND` | No está el artefacto en la library de la estación. |
| `UNSUPPORTED_SOURCE` | Ese tipo de source no está en este corte. |
| `UNKNOWN_GAME` | Esta estación no tiene ese juego. |
| `PREPARE_FAILED` | La estación no pudo preparar el juego. |
| `PREPARE_TIMEOUT` | La estación tardó demasiado en preparar el juego. |

## Configuración

Variables de build (Vite las incrusta en el bundle):

| Variable | Default | Uso |
| --- | --- | --- |
| `VITE_STATION_URL` | `http://localhost:8090` en código, `/station` en Docker | Base de REST y WS. Si empieza con `http`, el WS usa `ws(s)://`. Si no, se resuelve contra `location.host`. |
| `VITE_ICE_SERVERS` | `stun:stun.l.google.com:19302` (Docker) | Servidores ICE del navegador, separados por coma. |
| `VITE_STATIONS_URL` | vacío | Modo `deploy-all.sh`: cada juego usa `${VITE_STATIONS_URL}/<gameId>`. |
| `VITE_DISPATCHER_URL` | vacío (`/dispatcher` en `compose.loader.yaml`) | Activa el modo dispatcher: cada partida pide una estación al Game Dispatcher. En desarrollo el proxy de Vite la reenvía a `DISPATCHER_PROXY_TARGET` (default `http://127.0.0.1:8094`). |
| `VITE_GAME_ID` | `test-pattern` en código, `$GAME` en compose | `gameId` por defecto de `useStation`. |
| `VITE_CATALOG_URL` | `/catalog` | Base de game-crud para el catálogo. En desarrollo el proxy de Vite la reenvía a `CRUD_PROXY_TARGET` (default `http://127.0.0.1:8096`). Las imágenes (`/media`) van al mismo destino sin el `/catalog` final (contra el servidor: `https://<host>/media`); `MEDIA_PROXY_TARGET` lo fija a mano. |
| `VITE_SHOW_TEST_GAMES` | vacío | `true` muestra los juegos de prueba (`status` `TEST` en game-crud). Vacío: se ven en `npm run dev` y no en el build. Los `COMING_SOON` se muestran como «Próximamente» sin poder jugarlos y los `DISABLED` nunca llegan. |

Prioridad de la base de una estación (`urls.ts`): estación asignada por el dispatcher → `VITE_STATIONS_URL/<gameId>` → `VITE_STATION_URL`.

`STATION_PROXY_TARGET` (solo para `vite dev`/`preview`) define a dónde reenvía Vite el
prefijo `/station`. Por defecto es `http://127.0.0.1:8090`.

## Imagen Docker y nginx

[`game-client/Dockerfile`](../../game-client/Dockerfile) tiene dos etapas:

1. `node:22-bookworm-slim`: `npm ci` y `npm run build` con los `VITE_*` recibidos como build args.
2. `goacme/lego`: solo aporta el binario `lego` para `TLS_CERT=acme`.
3. `nginx:1.27-alpine`: sirve `dist/` con la plantilla [`nginx.conf`](../../game-client/nginx.conf).

| Puerto | Protocolo | Uso |
| --- | --- | --- |
| `443` | HTTPS (TLS 1.2/1.3, HTTP/2) | Acceso principal. Es un contexto seguro, necesario para WebRTC y la Gamepad API. |
| `5173` | HTTP | Acceso sin TLS (túnel SSH o LAN). Chrome bloquea WebRTC en `http://<IP>`. |
| `80` | HTTP | Solo el desafío ACME (`/.well-known/acme-challenge/`); el resto redirige a HTTPS. |

En ambos puertos:

- `/station/` → `proxy_pass ${STATION_UPSTREAM}/` (default `http://127.0.0.1:8090`), con upgrade de WebSocket y timeouts de 24 h.
- `/stations/<gameId>/` → puerto de ese juego según `conf.d/stations.conf` (lo genera `deploy-all.sh`).
- `/dispatcher/sessions` (solo `POST`) y `/dispatcher/sessions/<uuid>` (solo `GET`/`DELETE`) → `${DISPATCHER_UPSTREAM}` (default `http://127.0.0.1:8094`). La API admin del dispatcher y el loader no se exponen.
- `/ports/<8200-8299>/…` → `127.0.0.1:<puerto>`, estaciones asignadas por el dispatcher, con upgrade de WebSocket.
- `/catalog/games…`, `/catalog/categories…`, `/catalog/home` y `/catalog/avatars` (solo `GET`, sin login) → `${CRUD_UPSTREAM}` (default `http://127.0.0.1:8096`). Escribir en el catálogo sigue yendo por `/admin/api/` con el login del admin.
- `/` → SPA (`try_files … /index.html`).
- `index.html` va con `Cache-Control: no-cache` (un deploy se ve con una recarga normal) y `/assets/`
  (bundles con hash de Vite) con `max-age=31536000, immutable`.

Detalle completo de las rutas, el login y los nginx internos en [nginx](../nginx.md).

El certificado lo prepara [`docker/16-tls-certs.sh`](../../game-client/docker/16-tls-certs.sh) al arrancar:
autofirmado con `CN=${PUBLIC_HOST}` (`TLS_CERT=self`, default) o Let's Encrypt (`TLS_CERT=acme`). Ver
[Despliegue § HTTPS](../despliegue.md#https).

## Scripts

```bash
cd game-client
npm ci
npm run build     # tsc -b && vite build → dist/
npm run lint      # eslint .
npm test          # node --test: mapeo del mouse (test/)
npm run dev       # servidor Vite en :5173 (proxy /station, /loader y /catalog → game-crud)
npm run preview   # sirve dist/ en :5173
```

El workflow [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) ejecuta `npm ci` y
`npm run build` (Node 22) en cada push a `main` y en cada pull request.
