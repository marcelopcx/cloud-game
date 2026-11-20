# Juegos

Cada carpeta de [`games/`](../../games) es una imagen Docker que extiende la imagen base de la
estación (`cloudgame/game-station-runtime:latest`) con un solo juego y su `manifest.yaml`.
Agregar un juego no requiere cambios en `game-station/`. Qué tiene que cumplir un juego para
correr en la estación: [Requisitos de un juego](requisitos.md).

## Catálogo

El catálogo completo (22 juegos, ROMs requeridas y notas por título) está en
[`games/README.md`](../../games/README.md). Acá se detallan los dos juegos de referencia y el manifiesto.

| ID | Juego | Versión | Arquitecturas | Pads | Teclado / mouse | Resolución |
| --- | --- | --- | --- | --- | --- | --- |
| `supertuxkart` | SuperTuxKart (binario oficial) | 1.5 | `arm64`, `amd64` | 2 | Sí / Sí | 1280x720 |
| `battleship` | BattleShip: port nativo de Super Smash Bros. 64 | v1.6 | `arm64` | 4 | Sí / Sí | 960x720 (ver nota) |

### `supertuxkart`

- [`install.sh`](../../games/supertuxkart/install.sh) descarga el tarball oficial de GitHub Releases según `TARGETARCH`, y verifica el tamaño y el SHA-256 antes de extraerlo en `/opt/stk`.
- [`run-game.sh`](../../games/supertuxkart/run-game.sh) lanza `run_game.sh --screensize=<W>x<H> --fullscreen`. Con `STK_WINDOWED=1` omite `--fullscreen`.
- [`home/config-0.10/`](../../games/supertuxkart/home/config-0.10) se copia a `/root/.config/supertuxkart/` (`config.xml` y `players.xml` precargados).
- Manifiesto: `SDL_JOYSTICK_ALLOW_BACKGROUND_EVENTS=1` (Xvfb no tiene window manager y SDL descartaría eventos de joystick sin foco). `audio.fallbackArgs: ["--disable-sound"]`.

### `battleship`

- **Requiere una ROM propia** `baserom.us.z64` (NTSC-U v1.0) en `games/battleship/` antes del build. La ignora `games/battleship/.gitignore` y no se versiona.
- [`install.sh`](../../games/battleship/install.sh) clona `JRickey/BattleShip` (tag `v1.6`), instala CMake 3.31.6 (aarch64), aplica un parche de fuentes para Pillow ≥ 9 y compila con clang y Ninja. Después purga las herramientas de build.
- [`run-game.sh`](../../games/battleship/run-game.sh) genera `BattleShip.cfg.json` desde la plantilla `BattleShip.cfg.json.in` con la resolución de `STATION_SIZE`, en `SHIP_HOME` (`/cache/battleship-ship`).
- Está pensado para 4:3 (`ENV STATION_SIZE=960x720` en su Dockerfile). Pero `compose.yaml` define `STATION_SIZE: ${STATION_SIZE:-1280x720}` y el loader hace lo mismo (`application.yml`, `station-env`), así que eso **pisa** el valor de la imagen en ambos modos. Para desplegarlo en 960x720 hay que poner `STATION_SIZE=960x720` en `.env`. El campo `size` de su `manifest.yaml` **no lo lee la estación**.
- Render por software: `LIBGL_ALWAYS_SOFTWARE=1`, `MESA_GL_VERSION_OVERRIDE=4.5`.

## Construcción

```bash
./scripts/build-station.sh <id>     # construye runtime + cloudgame/game-station-<id>
GAME=<id> ./scripts/deploy.sh       # construye y despliega el stack con ese juego
```

`build-station.sh` acepta cualquier `<id>` que tenga `games/<id>/Dockerfile`.

## Manifiesto

La estación lee `/opt/game/manifest.yaml` (`GAME_MANIFEST`). Campos soportados por `GameManifest`:

```yaml
id: supertuxkart            # único gameId aceptado por /prepare y /launch
version: "1.5.0"            # informativo
kind: native                # native | test-pattern
command: ["/opt/stk/run-game.sh"]
args: []                    # se concatenan a command
workdir: /opt/stk
saves: []                   # rutas fuera de CACHE_ROOT y HOME que el juego escribe (p. ej. /opt/zelda3/saves); se borran tras cada partida
savesExclude: []            # rutas dentro de esas áreas que no se suben al guardar (caches grandes y regenerables)
env:                        # se agregan al entorno del juego (sobrescriben los defaults)
  SDL_VIDEODRIVER: x11
needs:
  display: true             # levantar Xvfb y capturar (default true)
  audio: true               # comprobar Pulse y capturar audio (default true)
  gamepad: 2                # 0..4, o true (=1) / false
  keyboard: true            # XTEST
  mouse: true               # XTEST
  relativeMouse: false      # mouse como deltas vía LookFeed
audio:
  fallbackArgs: ["--disable-sound"]   # se agregan si Pulse no responde
```

- `command`, `args`, `env` y `fallbackArgs` admiten placeholders (`${WIDTH}`, `${HEIGHT}`, `${SIZE}`, `${DISPLAY}`, `${FPS}`, `${CACHE}`, `${LIBRARY}`). Ver [Game Station](../game-station/README.md#placeholders-del-manifiesto).
- Progreso entre jugadores: tras cada partida la estación vacía `CACHE_ROOT`, restaura `HOME` y borra `saves` (`STATION_RESET_SAVES`). Si el juego guarda en otro lado (típico: `saves/` en el cwd), declararlo en `saves`.
- Jugadores con sesión: esas mismas áreas (sin lo que copia `prepare` en `<juego>/<versión>/`, los `*.log` ni `savesExclude`) se empaquetan en `stop` y se restauran en el próximo `launch`, en cualquier estación. Ver [Sesión y saves](../game-crud/README.md#sesión-y-saves).
- Los campos desconocidos se ignoran.
- `kind: test-pattern` o un `command` vacío no ejecutan binario: el video son barras SMPTE.
- `env.CLOUDGAME_INPUT_UINPUT: "false"` fuerza los pads vía `libgameinput.so` en lugar de `/dev/uinput`. Ver [Input](../game-station/input.md#gamepads-uinput-y-libgameinputso).

## Agregar un juego

1. Crear `games/<id>/` con:

   ```dockerfile
   ARG RUNTIME_IMAGE=cloudgame/game-station-runtime:latest
   FROM ${RUNTIME_IMAGE}

   # instalar el juego (script propio, sin commitear binarios)
   COPY install.sh /tmp/install.sh
   RUN chmod +x /tmp/install.sh && /tmp/install.sh && rm /tmp/install.sh

   COPY run-game.sh /opt/<id>/run-game.sh
   COPY manifest.yaml /opt/game/manifest.yaml

   # payload de la library que usa POST /prepare (el cliente pide /library/<id>/1.0.0)
   RUN station-seed-library <id> 1.0.0

   LABEL org.cloudgame.role=game-station
   LABEL org.cloudgame.game.id=<id>
   ```

2. Escribir `manifest.yaml` con `id: <id>` y los `needs` del juego. Si usa ROM o disco propio, agregar `games/<id>/.gitignore` con su patrón (p. ej. `*.z64`).
3. En `run-game.sh`, redirigir la salida a `${CACHE_ROOT:-/cache}/<id>.log` y abrir la ventana en `0,0` con el tamaño de `STATION_SIZE`. El foco lo gestiona `xfocus-game`.
4. Si el juego necesita otra resolución, definir `STATION_SIZE=<W>x<H>` en `.env` (compose sobrescribe el `ENV` de la imagen).
5. Darlo de alta en el catálogo con `path` = `/games/<id>` desde [game-admin](../game-admin/README.md) (`/admin/`).
6. Construir y desplegar con `./scripts/deploy-loader.sh <id>` (o `GAME=<id> ./scripts/deploy.sh`).

> En modo loader, el loader acepta cualquier `gameId` que tenga imagen `cloudgame/game-station-<id>`
> con `org.cloudgame.role=game-station`. Con `deploy.sh`, la estación solo sirve el `GAME` desplegado.
